// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.dualsense

import android.bluetooth.BluetoothDevice
import android.util.Log
import java.util.concurrent.locks.LockSupport

/**
 * Háptica HD por Bluetooth: convierte la háptica de la PS5 (PCM estéreo int16 little-endian a 3000 Hz, ~30 muestras
 * cada 10 ms) en reportes 0x39 de 2 bloques de 32 muestras int8 a 3000 Hz cada ~21,3 ms (ver
 * `OutputReportBuilder.hapticsBluetooth`). La PS5 ya entrega 3 kHz, así que no hay remuestreo: solo `>> 8`.
 *
 * Un hilo propio manda un reporte cada 64 muestras (21,33 ms). Hay un colchón corto para absorber el jitter de la red;
 * si se acumula demasiado se descartan las muestras más viejas (prefiere latencia baja a audio completo), y si se
 * vacía se calla hasta volver a juntar colchón, sin mandar bloques a medias.
 */
class HdHapticsStreamer(
	private val sender: DualSenseBluetoothSender,
	private val nextSequence: () -> Int
)
{
	private val lock = Any()
	// Muestras estéreo int8 intercaladas (L, R, L, R...) en un anillo.
	private val ring = ByteArray(RING_SAMPLES * 2)
	private var head = 0 // en muestras
	private var count = 0 // en muestras
	@Volatile private var running = true
	private var packetCounter = 0
	private val thread = Thread(::run, "DualSenseHaptics").also { it.isDaemon = true; it.start() }

	/** Hilo de red de la sesión: no bloquea. `pcm` = estéreo int16 LE. */
	fun onFrame(pcm: ByteArray)
	{
		if(pcm.size < 4)
			return
		synchronized(lock)
		{
			var i = 0
			while(i + 3 < pcm.size)
			{
				if(count == RING_SAMPLES) // lleno: se pierde la muestra más vieja
				{
					head = (head + 1) % RING_SAMPLES
					count--
				}
				val tail = (head + count) % RING_SAMPLES
				ring[tail * 2] = toInt8(pcm[i], pcm[i + 1])
				ring[tail * 2 + 1] = toInt8(pcm[i + 2], pcm[i + 3])
				count++
				i += 4
			}
		}
	}

	fun close()
	{
		running = false
		LockSupport.unpark(thread)
		try { thread.join(500) } catch(_: InterruptedException) {}
	}

	private fun toInt8(lo: Byte, hi: Byte): Byte = hi // int16 little-endian >> 8 = el byte alto

	private fun run()
	{
		var device: BluetoothDevice? = null
		var deviceCheckedAt = 0L
		var nextAt = 0L
		var primed = false
		val block = ByteArray(OutputReportBuilder.HAPTICS_BLOCK_BYTES)
		var sent = 0L
		while(running)
		{
			val now = System.nanoTime()
			val buffered = synchronized(lock) { count }
			if(!primed)
			{
				if(buffered < PRIME_SAMPLES)
				{
					LockSupport.parkNanos(1_000_000)
					continue
				}
				primed = true
				nextAt = now
			}
			if(now < nextAt)
			{
				LockSupport.parkNanos(minOf(nextAt - now, 2_000_000))
				continue
			}
			val enough = synchronized(lock)
			{
				if(count >= BLOCK_SAMPLES)
				{
					for(i in 0 until BLOCK_SAMPLES)
					{
						val pos = (head + i) % RING_SAMPLES
						block[i * 2] = ring[pos * 2]
						block[i * 2 + 1] = ring[pos * 2 + 1]
					}
					head = (head + BLOCK_SAMPLES) % RING_SAMPLES
					count -= BLOCK_SAMPLES
					true
				}
				else false
			}
			if(!enough)
			{
				primed = false // se vació: se vuelve a llenar el colchón
				continue
			}
			if(device == null || now - deviceCheckedAt > 1_000_000_000L)
			{
				device = sender.connectedController()
				deviceCheckedAt = now
			}
			val target = device
			if(target != null && sender.available)
			{
				val report = OutputReportBuilder.hapticsBluetooth(block, nextSequence(), packetCounter)
				packetCounter = (packetCounter + 2) and 0xff
				if(sender.send(target, report)) sent++
			}
			nextAt += PERIOD_NS
			if(nextAt < now - 4 * PERIOD_NS) nextAt = now // se atrasó demasiado: se resincroniza
		}
		Log.i(TAG, "Háptica HD: $sent reportes enviados")
	}

	private companion object
	{
		const val TAG = "DualPlay"
		const val BLOCK_SAMPLES = 64 // por reporte (2 x 32)
		const val PRIME_SAMPLES = 96 // colchón inicial: un bloque y medio
		const val RING_SAMPLES = 320 // ~107 ms como máximo
		const val PERIOD_NS = BLOCK_SAMPLES * 1_000_000_000L / OutputReportBuilder.HAPTICS_SAMPLE_RATE
	}
}

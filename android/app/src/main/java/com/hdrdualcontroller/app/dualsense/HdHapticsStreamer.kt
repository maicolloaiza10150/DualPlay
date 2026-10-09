// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.dualsense

import android.bluetooth.BluetoothDevice
import android.util.Log
import java.util.concurrent.locks.LockSupport

/**
 * Háptica HD por Bluetooth: convierte la háptica de la PS5 (PCM estéreo int16 little-endian a 3000 Hz, ~30 muestras
 * cada 10 ms, solo mientras hay vibración) en reportes 0x39 de 2 bloques de 32 muestras int8 a 3000 Hz cada ~21,3 ms
 * (ver `OutputReportBuilder.hapticsBluetooth`). La PS5 ya entrega 3 kHz, así que no hay remuestreo: solo `>> 8`.
 *
 * Prioriza la LATENCIA: el primer bloque de una vibración sale en cuanto llega la primera muestra (completado con
 * silencio hasta 64 muestras), sin colchón previo; la cola se recorta a `MAX_QUEUE_SAMPLES` para que el retraso no
 * crezca; y el hilo para en silencio (la PS5 deja de mandar frames) para no enviar bloques vacíos.
 */
class HdHapticsStreamer(
	private val sender: DualSenseBluetoothSender,
	private val nextSequence: () -> Int,
	/** Multiplicador de la señal (1.0 = tal cual llega de la PS5). Se satura en int8. */
	private val gain: Float = 1f,
	/** Búfer interno de háptica del control (16..127); menos = menos retraso pero más riesgo de cortes. */
	private val controllerBuffer: Int = OutputReportBuilder.HAPTICS_BUFFER_LENGTH
)
{
	private val lock = Any()
	// Muestras estéreo int8 intercaladas (L, R, L, R...) en un anillo.
	private val ring = ByteArray(RING_SAMPLES * 2)
	private var head = 0 // en muestras
	private var count = 0 // en muestras
	@Volatile private var running = true
	private var packetCounter = 0

	// Estadísticas para afinar (se vuelcan al log cada STATS_PERIOD_NS).
	private var statFrames = 0
	private var statTrimmed = 0
	private var statPartial = 0 // bloques enviados incompletos (rellenados con silencio)
	private var statMaxQueued = 0
	private var statPeak = 0 // |muestra int16| máxima
	private var statSumSquares = 0.0
	private var statSamples = 0L
	private val thread = Thread(::run, "DualSenseHaptics").also { it.isDaemon = true; it.start() }

	/** Hilo de red de la sesión: no bloquea. `pcm` = estéreo int16 LE. */
	fun onFrame(pcm: ByteArray)
	{
		if(pcm.size < 4)
			return
		synchronized(lock)
		{
			statFrames++
			var i = 0
			while(i + 3 < pcm.size)
			{
				if(count == RING_SAMPLES) // lleno (no debería pasar por el recorte del hilo): se pierde la más vieja
				{
					head = (head + 1) % RING_SAMPLES
					count--
					statTrimmed++
				}
				val tail = (head + count) % RING_SAMPLES
				ring[tail * 2] = toInt8(pcm[i], pcm[i + 1])
				ring[tail * 2 + 1] = toInt8(pcm[i + 2], pcm[i + 3])
				count++
				i += 4
			}
			if(count > statMaxQueued) statMaxQueued = count
		}
		LockSupport.unpark(thread) // despierta al hilo si estaba en silencio
	}

	fun close()
	{
		running = false
		LockSupport.unpark(thread)
		try { thread.join(500) } catch(_: InterruptedException) {}
	}

	/** int16 little-endian -> int8 (`>> 8`) con ganancia y saturación; actualiza las estadísticas de nivel. */
	private fun toInt8(lo: Byte, hi: Byte): Byte
	{
		val sample = ((hi.toInt() shl 8) or (lo.toInt() and 0xff)) // ya con signo
		val abs = if(sample < 0) -sample else sample
		if(abs > statPeak) statPeak = abs
		statSumSquares += sample.toDouble() * sample
		statSamples++
		return Math.round(sample * gain / 256f).coerceIn(-128, 127).toByte()
	}

	private fun run()
	{
		var device: BluetoothDevice? = null
		var deviceCheckedAt = 0L
		var nextAt = 0L
		var active = false
		var idleTicks = 0
		val block = ByteArray(OutputReportBuilder.HAPTICS_BLOCK_BYTES)
		var sent = 0L
		var statsAt = System.nanoTime()
		while(running)
		{
			val now = System.nanoTime()
			if(now - statsAt >= STATS_PERIOD_NS)
			{
				statsAt = now
				logStats(sent)
			}
			val queued = synchronized(lock) { count }
			if(!active)
			{
				if(queued == 0)
				{
					LockSupport.parkNanos(5_000_000) // en silencio; onFrame() lo despierta
					continue
				}
				active = true
				idleTicks = 0
				nextAt = now // el primer bloque sale ya, sin colchón
			}
			if(now < nextAt)
			{
				LockSupport.parkNanos(minOf(nextAt - now, 2_000_000))
				continue
			}

			val taken = synchronized(lock)
			{
				// Recorte de latencia: si hay más de MAX_QUEUE_SAMPLES se descarta lo más viejo.
				if(count > MAX_QUEUE_SAMPLES)
				{
					val drop = count - MAX_QUEUE_SAMPLES
					head = (head + drop) % RING_SAMPLES
					count -= drop
					statTrimmed += drop
				}
				val n = minOf(count, BLOCK_SAMPLES)
				block.fill(0)
				for(i in 0 until n)
				{
					val pos = (head + i) % RING_SAMPLES
					block[i * 2] = ring[pos * 2]
					block[i * 2 + 1] = ring[pos * 2 + 1]
				}
				head = (head + n) % RING_SAMPLES
				count -= n
				if(n in 1 until BLOCK_SAMPLES) statPartial++
				n
			}
			if(taken == 0)
			{
				// Sin datos en este turno: tras dos turnos seguidos se considera silencio y el hilo se detiene.
				if(++idleTicks >= 2) active = false
				nextAt += PERIOD_NS
				continue
			}
			idleTicks = 0

			if(device == null || now - deviceCheckedAt > 1_000_000_000L)
			{
				device = sender.connectedController()
				deviceCheckedAt = now
			}
			val target = device
			if(target != null && sender.available)
			{
				val report = OutputReportBuilder.hapticsBluetooth(block, nextSequence(), packetCounter, controllerBuffer)
				packetCounter = (packetCounter + 2) and 0xff
				if(sender.send(target, report)) sent++
			}
			nextAt += PERIOD_NS
			if(nextAt < now - 4 * PERIOD_NS) nextAt = now // se atrasó demasiado: se resincroniza
		}
		Log.i(TAG, "Háptica HD: $sent reportes enviados")
	}

	private fun logStats(sent: Long)
	{
		val frames: Int
		val trimmed: Int
		val partial: Int
		val maxQueued: Int
		val peak: Int
		val rms: Double
		synchronized(lock)
		{
			frames = statFrames; trimmed = statTrimmed; partial = statPartial; maxQueued = statMaxQueued; peak = statPeak
			rms = if(statSamples > 0) Math.sqrt(statSumSquares / statSamples) else 0.0
			statFrames = 0; statTrimmed = 0; statPartial = 0; statMaxQueued = 0; statPeak = 0; statSumSquares = 0.0; statSamples = 0
		}
		// Nivel en % de plena escala int16 (32768): pico y RMS de la háptica que llega de la PS5.
		Log.i(TAG, "Háptica HD [5 s]: frames=$frames reportes=$sent recortadas=$trimmed incompletos=$partial " +
			"colaMax=${maxQueued * 1000 / OutputReportBuilder.HAPTICS_SAMPLE_RATE}ms " +
			"pico=%.1f%% rms=%.1f%% ganancia=%.2f búfer=$controllerBuffer".format(peak * 100.0 / 32768, rms * 100.0 / 32768, gain))
	}

	private companion object
	{
		const val STATS_PERIOD_NS = 5_000_000_000L
		const val TAG = "DualPlay"
		const val BLOCK_SAMPLES = 64 // por reporte (2 x 32)
		const val MAX_QUEUE_SAMPLES = 192 // 64 ms: más cola = más retraso
		const val RING_SAMPLES = 512
		const val PERIOD_NS = BLOCK_SAMPLES * 1_000_000_000L / OutputReportBuilder.HAPTICS_SAMPLE_RATE
	}
}

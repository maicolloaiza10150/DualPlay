// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.dualsense

import android.content.Context
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Efectos pedidos por la PS5 para cada gatillo (`ChiakiTriggerEffectsEvent`: tipo + 10 bytes por lado). */
class TriggerEffects(val left: TriggerEffect, val right: TriggerEffect)

/**
 * Salida hacia el DualSense por Bluetooth (FeedbackRouter, docs/ARCHITECTURE.md 2.5): gatillos adaptativos y rumble
 * emulado a partir de la háptica de la PS5. Los dos viajan en el mismo output report 0x31 con banderas de validez
 * independientes, así que se fusionan: de cada uno gana el último valor y se envía como mucho un reporte cada
 * `MIN_INTERVAL_MS`. Al cerrar se apagan gatillos y motores, porque el control conserva el último estado.
 */
class TriggerFeedback(context: Context, hdHaptics: Boolean = false)
{
	/** Cambios pendientes de enviar; `null` = sin cambios en ese campo. */
	private class Pending(val triggers: TriggerEffects?, val rumble: Int?)

	private val sender = DualSenseBluetoothSender(context)
	private val executor = Executors.newSingleThreadExecutor { Thread(it, "DualSenseOutput").also { t -> t.isDaemon = true } }
	private val pending = AtomicReference<Pending?>(null)
	private var opened = false
	private var hdStreamer: HdHapticsStreamer? = null
	private val useHdHaptics = hdHaptics

	init
	{
		executor.execute {
			opened = sender.open()
			if(opened && useHdHaptics)
				hdStreamer = HdHapticsStreamer(sender, sender::nextSequence)
			if(!opened)
				Log.w(TAG, "Los efectos del DualSense se ignorarán en esta sesión")
		}
	}

	/** Llamar desde cualquier hilo; no bloquea. */
	fun onTriggerEffects(effects: TriggerEffects)
	{
		pending.updateAndGet { Pending(effects, it?.rumble) }
		executor.execute { drain() }
	}

	/** Frame crudo de háptica de la PS5 (estéreo int16 a 3 kHz). Solo se usa en modo háptica HD. */
	fun onHapticFrame(pcm: ByteArray)
	{
		hdStreamer?.onFrame(pcm)
	}

	/** `strength` 0..255 (la háptica llega con 16 bits: se pasa `>> 8`, como `SetHapticRumble` del escritorio). */
	fun onRumble(strength: Int)
	{
		pending.updateAndGet { Pending(it?.triggers, strength) }
		executor.execute { drain() }
	}

	private fun drain()
	{
		val change = pending.getAndSet(null) ?: return
		if(!opened || !sender.available)
			return
		val device = sender.connectedController() ?: return
		val report = OutputReportBuilder.outputBluetooth(change.triggers?.left, change.triggers?.right, change.rumble, sender.nextSequence())
		sender.send(device, report)
		// Mientras duerme, los eventos nuevos sobrescriben `pending`: solo se envía lo más reciente.
		try { Thread.sleep(MIN_INTERVAL_MS) } catch(_: InterruptedException) {}
	}

	/** Apaga gatillos y motores y libera el perfil Bluetooth. Bloquea un instante como máximo. */
	fun clearAndClose()
	{
		pending.set(null)
		executor.execute {
			hdStreamer?.close()
			hdStreamer = null
			if(opened && sender.available)
				sender.connectedController()?.let {
					sender.send(it, OutputReportBuilder.outputBluetooth(TriggerEffect.CLEAR, TriggerEffect.CLEAR, rumble = 0, sequence = sender.nextSequence()))
				}
			sender.close()
		}
		executor.shutdown()
		try { executor.awaitTermination(1, TimeUnit.SECONDS) } catch(_: InterruptedException) {}
	}

	private companion object
	{
		const val TAG = "DualPlay"
		const val MIN_INTERVAL_MS = 8L
	}
}

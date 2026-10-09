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
 * Parte "gatillos" del FeedbackRouter (docs/ARCHITECTURE.md 2.5): recibe los efectos de la sesión y los manda al
 * DualSense por Bluetooth. El último estado gana (fusión) y hay un intervalo mínimo entre envíos para no saturar el
 * enlace; al cerrar se apagan los gatillos (modo 0x05), porque el control conserva el último efecto.
 */
class TriggerFeedback(context: Context)
{
	private val sender = DualSenseBluetoothSender(context)
	private val executor = Executors.newSingleThreadExecutor { Thread(it, "DualSenseTriggers").also { t -> t.isDaemon = true } }
	private val pending = AtomicReference<TriggerEffects?>(null)
	private var sequence = 0
	private var opened = false

	init
	{
		executor.execute { opened = sender.open(); if(!opened) Log.w(TAG, "Los efectos de gatillo se ignorarán en esta sesión") }
	}

	/** Llamar desde cualquier hilo; no bloquea. */
	fun onTriggerEffects(effects: TriggerEffects)
	{
		pending.set(effects)
		executor.execute { drain() }
	}

	private fun drain()
	{
		val effects = pending.getAndSet(null) ?: return
		if(!opened || !sender.available)
			return
		val device = sender.connectedController() ?: return
		val report = OutputReportBuilder.triggerEffectsBluetooth(effects.left, effects.right, sequence)
		sequence = (sequence + 1) and 0x0f
		sender.send(device, report)
		// Mientras duerme, nuevos eventos sobrescriben `pending`: solo se envía el más reciente.
		try { Thread.sleep(MIN_INTERVAL_MS) } catch(_: InterruptedException) {}
	}

	/** Apaga los gatillos y libera el perfil Bluetooth. Bloquea unos instantes como máximo. */
	fun clearAndClose()
	{
		pending.set(null)
		executor.execute {
			if(opened && sender.available)
				sender.connectedController()?.let { sender.send(it, OutputReportBuilder.triggerEffectsBluetooth(TriggerEffect.CLEAR, TriggerEffect.CLEAR, sequence)) }
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

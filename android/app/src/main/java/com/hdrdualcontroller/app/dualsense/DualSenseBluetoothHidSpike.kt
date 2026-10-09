// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.dualsense

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * SPIKE D (solo depuración): enviar el output report 0x31 al DualSense por Bluetooth usando el perfil HID Host
 * del propio sistema, sin abrir sockets: `BluetoothHidHost.sendData(device, hex)` (API oculta, perfil HID_HOST = 4).
 *
 * Verificado en el código de AOSP (packages/modules/Bluetooth, HidHostService.java): `sendData` solo exige
 * BLUETOOTH_CONNECT y que el dispositivo esté `STATE_CONNECTED`; `btif_hh` convierte el hex a bytes y lo envía
 * por el canal interrupt (cabecera HIDP 0xA2 añadida por la pila). El control debe estar conectado como "Input device".
 *
 * Es el MISMO camino que ya usa el kernel/Android para el rumble, así que el input sigue llegando como InputDevice.
 */
class DualSenseBluetoothHidSpike(private val context: Context)
{
	companion object
	{
		private const val TAG = DualSenseUsbTransport.TAG
		private const val PROFILE_HID_HOST = 4 // BluetoothProfile.HID_HOST (@hide)
	}

	@SuppressLint("MissingPermission")
	fun bondedControllers(): List<BluetoothDevice> =
		context.getSystemService(BluetoothManager::class.java).adapter?.bondedDevices.orEmpty()
			.filter { it.name?.contains("Wireless Controller", true) == true || it.name?.contains("DualSense", true) == true }

	private fun say(log: (String) -> Unit, line: String) { Log.i(TAG, line); log(line) }

	/**
	 * SPIKE E: háptica HD por Bluetooth. Manda un tono senoidal de `frequencyHz` durante `seconds` s usando reportes 0x39
	 * (2 bloques de 32 muestras int8 estéreo a 3 kHz cada ~21,3 ms). Bloqueante (hilo de fondo).
	 */
	fun hdHapticsTone(frequencyHz: Int, seconds: Int, amplitude: Int, log: (String) -> Unit)
	{
		val sender = DualSenseBluetoothSender(context)
		if(!sender.open()) { say(log, "FIN: no se pudo abrir el perfil HID Host"); return }
		try
		{
			val device = sender.connectedController() ?: run { say(log, "FIN: el DualSense no está conectado como 'Input device'"); return }
			val blockSamples = 64 // por reporte: 2 bloques x 32 muestras estéreo
			val periodNs = blockSamples * 1_000_000_000L / OutputReportBuilder.HAPTICS_SAMPLE_RATE
			val reports = seconds * 1_000_000_000L / periodNs
			say(log, "== E1: tono de $frequencyHz Hz, amplitud $amplitude/127, $seconds s = $reports reportes cada ${periodNs / 1_000_000.0} ms")
			var phase = 0
			var counter = 0
			var sent = 0
			var failed = 0
			val start = System.nanoTime()
			for(n in 0 until reports)
			{
				val block = ByteArray(OutputReportBuilder.HAPTICS_BLOCK_BYTES)
				for(i in 0 until blockSamples)
				{
					val value = (Math.sin(2.0 * Math.PI * frequencyHz * phase / OutputReportBuilder.HAPTICS_SAMPLE_RATE) * amplitude).toInt().toByte()
					block[i * 2] = value
					block[i * 2 + 1] = value
					phase++
				}
				val report = OutputReportBuilder.hapticsBluetooth(block, sequence = (n and 0x0f).toInt(), packetCounter = counter and 0xff)
				counter += 2
				if(sender.send(device, report)) sent++ else failed++
				val wait = start + (n + 1) * periodNs - System.nanoTime()
				if(wait > 0) Thread.sleep(wait / 1_000_000, (wait % 1_000_000).toInt())
			}
			say(log, "E1 fin: $sent enviados, $failed fallidos en ${(System.nanoTime() - start) / 1_000_000} ms. ¿Notaste una vibración fina?")
		}
		finally { sender.close() }
	}

	/** Bloqueante (hilo de fondo). */
	@SuppressLint("MissingPermission")
	fun send(left: TriggerEffect?, right: TriggerEffect?, log: (String) -> Unit)
	{
		val exempted = try { HiddenApiBypass.addHiddenApiExemptions("") } catch(e: Throwable) { say(log, "bypass falló: ${e.message}"); false }
		say(log, "== D1: bypass de APIs ocultas: $exempted")

		val adapter: BluetoothAdapter = context.getSystemService(BluetoothManager::class.java).adapter ?: run { say(log, "sin adaptador Bluetooth"); return }
		val latch = CountDownLatch(1)
		var proxy: BluetoothProfile? = null
		val requested = adapter.getProfileProxy(context, object: BluetoothProfile.ServiceListener {
			override fun onServiceConnected(profile: Int, p: BluetoothProfile) { proxy = p; latch.countDown() }
			override fun onServiceDisconnected(profile: Int) {}
		}, PROFILE_HID_HOST)
		say(log, "== D2: getProfileProxy(HID_HOST=4) devolvió $requested")
		if(!requested || !latch.await(5, TimeUnit.SECONDS) || proxy == null) { say(log, "FIN: no se obtuvo el proxy del perfil HID Host"); return }
		val hid = proxy!!
		say(log, "proxy: ${hid.javaClass.name}")

		val methods = hid.javaClass.declaredMethods.map { it.name }.filter { it.contains("Data") || it.contains("Report") }.toSortedSet()
		say(log, "métodos relevantes: $methods")

		val connected = hid.connectedDevices
		say(log, "== D3: dispositivos HID conectados: ${connected.joinToString { "${it.name} ${it.address}" }.ifEmpty { "ninguno" }}")
		val device = connected.firstOrNull { it.name?.contains("Wireless Controller", true) == true || it.name?.contains("DualSense", true) == true }
			?: run { say(log, "FIN: el DualSense no está conectado como 'Input device'. Actívalo en Ajustes de Bluetooth y enciende el control."); return }

		say(log, "== D4: enviar reporte 0x31 con efecto de gatillo por sendData()")
		val report = OutputReportBuilder.triggerEffectsBluetooth(left, right, sequence = 1)
		val hex = report.joinToString("") { "%02x".format(it.toInt() and 0xff) }
		try
		{
			val method = hid.javaClass.getMethod("sendData", BluetoothDevice::class.java, String::class.java)
			val result = method.invoke(hid, device, hex)
			say(log, "sendData devolvió $result (reporte de ${report.size} bytes, ${hex.length} caracteres hex)")
			say(log, "Aprieta los gatillos AHORA: ¿notas resistencia?")
		}
		catch(e: NoSuchMethodException)
		{
			say(log, "sendData no existe con esa firma: ${hid.javaClass.declaredMethods.filter { it.name == "sendData" }}")
		}
		catch(e: Throwable)
		{
			say(log, "sendData lanzó ${(e.cause ?: e).javaClass.simpleName}: ${(e.cause ?: e).message}")
		}
		finally
		{
			adapter.closeProfileProxy(PROFILE_HID_HOST, hid)
		}
	}
}

// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.dualsense

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Envía output reports al DualSense conectado por Bluetooth con `BluetoothHidHost.sendData(device, hex)`
 * (API oculta; perfil HID_HOST = 4). Funciona sin root: solo exige BLUETOOTH_CONNECT y que el control esté conectado
 * como "Input device" (SPIKE D, docs/ARCHITECTURE.md 4). No toca la conexión, así que el input sigue llegando como InputDevice.
 *
 * Es frágil por naturaleza (API oculta): cualquier fallo se captura, se registra una vez y deja el sender
 * inutilizable (`available == false`) sin afectar al stream. Todos los métodos son bloqueantes: llamar desde un hilo de fondo.
 */
class DualSenseBluetoothSender(context: Context)
{
	private val appContext = context.applicationContext
	private var adapter: BluetoothAdapter? = null
	private var proxy: BluetoothProfile? = null
	private var sendMethod: java.lang.reflect.Method? = null

	private val sequenceCounter = java.util.concurrent.atomic.AtomicInteger()

	/** Contador de 4 bits compartido por todos los reportes (0x31 y 0x39), como `reportSeqCounter` de DS5Dongle. */
	fun nextSequence(): Int = sequenceCounter.getAndIncrement() and 0x0f

	/** false si no hay permiso, no hay API o falló un envío; ya no se reintenta en esta sesión. */
	@Volatile var available = false
		private set

	/** Prepara el proxy del perfil HID Host. Devuelve true si se puede enviar. */
	@SuppressLint("MissingPermission")
	fun open(): Boolean
	{
		if(appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
			return fail("falta el permiso BLUETOOTH_CONNECT")
		try
		{
			// Solo se levanta la restricción para las clases de Bluetooth, no para todo el framework.
			HiddenApiBypass.addHiddenApiExemptions("Landroid/bluetooth/")
			val bt = appContext.getSystemService(BluetoothManager::class.java).adapter ?: return fail("sin adaptador Bluetooth")
			val latch = CountDownLatch(1)
			var connected: BluetoothProfile? = null
			val requested = bt.getProfileProxy(appContext, object: BluetoothProfile.ServiceListener {
				override fun onServiceConnected(profile: Int, p: BluetoothProfile) { connected = p; latch.countDown() }
				override fun onServiceDisconnected(profile: Int) { available = false }
			}, PROFILE_HID_HOST)
			if(!requested || !latch.await(5, TimeUnit.SECONDS) || connected == null)
				return fail("no se obtuvo el proxy HID Host")
			adapter = bt
			proxy = connected
			sendMethod = connected!!.javaClass.getMethod("sendData", BluetoothDevice::class.java, String::class.java)
			available = true
			return true
		}
		catch(e: Throwable)
		{
			return fail("${e.javaClass.simpleName}: ${e.message}")
		}
	}

	/** El DualSense que el sistema tiene conectado como dispositivo HID, o null. */
	@SuppressLint("MissingPermission")
	fun connectedController(): BluetoothDevice? =
		try
		{
			proxy?.connectedDevices?.firstOrNull { it.name?.contains("Wireless Controller", true) == true || it.name?.contains("DualSense", true) == true }
		}
		catch(e: Throwable) { fail("connectedDevices: ${e.message}"); null }

	/** Envía el reporte (con report id incluido, p. ej. 0x31 + 77 bytes). */
	fun send(device: BluetoothDevice, report: ByteArray): Boolean
	{
		val method = sendMethod ?: return false
		if(!available)
			return false
		return try
		{
			val hex = report.joinToString("") { "%02x".format(it.toInt() and 0xff) }
			method.invoke(proxy, device, hex) as? Boolean ?: false
		}
		catch(e: Throwable)
		{
			fail("sendData: ${(e.cause ?: e).javaClass.simpleName}: ${(e.cause ?: e).message}")
			false
		}
	}

	fun close()
	{
		available = false
		try { proxy?.let { adapter?.closeProfileProxy(PROFILE_HID_HOST, it) } } catch(_: Throwable) {}
		proxy = null
		sendMethod = null
	}

	private fun fail(reason: String): Boolean
	{
		if(available || proxy == null) // evita repetir el mismo aviso en cada envío
			Log.w(TAG, "Gatillos por Bluetooth no disponibles: $reason")
		available = false
		return false
	}

	private companion object
	{
		const val TAG = "DualPlay"
		const val PROFILE_HID_HOST = 4 // BluetoothProfile.HID_HOST (@hide)
	}
}

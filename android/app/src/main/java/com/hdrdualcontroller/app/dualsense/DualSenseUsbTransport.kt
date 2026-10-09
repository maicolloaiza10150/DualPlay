// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.dualsense

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log

/**
 * ÚNICA clase que abre `UsbDeviceConnection` y escribe en el DualSense (regla de CLAUDE.md).
 *
 * Estado: SPIKE A (docs/ARCHITECTURE.md 2.6). Por ahora solo expone intentos de envío aislados para medir
 * qué funciona en el teléfono; la cola de envío con límite de tasa y fusión de estados llega tras el spike.
 */
class DualSenseUsbTransport(context: Context)
{
	/** Resultado de un intento de envío, para mostrarlo en la pantalla de pruebas y en logcat. */
	data class Attempt(val label: String, val ok: Boolean, val detail: String)

	companion object
	{
		const val TAG = "DualPlay"
		const val VENDOR_SONY = 0x054C
		val PRODUCTS = setOf(0x0CE6 /* DualSense */, 0x0DF2 /* DualSense Edge */)

		private const val ACTION_PERMISSION = "com.hdrdualcontroller.app.USB_PERMISSION"
		private const val TIMEOUT_MS = 1000

		// HID class request SET_REPORT (USB HID 1.11, 7.2.2): bmRequestType host->device | class | interface.
		private const val REQUEST_TYPE_SET_REPORT = 0x21
		private const val REQUEST_SET_REPORT = 0x09
		private const val REPORT_TYPE_OUTPUT = 0x02
	}

	private val appContext = context.applicationContext
	private val usb = appContext.getSystemService(UsbManager::class.java)

	fun findDevice(): UsbDevice? =
		usb.deviceList.values.firstOrNull { it.vendorId == VENDOR_SONY && it.productId in PRODUCTS }

	fun hasPermission(device: UsbDevice) = usb.hasPermission(device)

	fun requestPermission(device: UsbDevice, onResult: (Boolean) -> Unit)
	{
		val receiver = object: BroadcastReceiver()
		{
			override fun onReceive(context: Context, intent: Intent)
			{
				appContext.unregisterReceiver(this)
				onResult(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
			}
		}
		appContext.registerReceiver(receiver, IntentFilter(ACTION_PERMISSION), Context.RECEIVER_NOT_EXPORTED)
		// FLAG_MUTABLE es obligatorio: el sistema rellena EXTRA_PERMISSION_GRANTED en este intent.
		val pending = PendingIntent.getBroadcast(
			appContext, 0, Intent(ACTION_PERMISSION).setPackage(appContext.packageName), PendingIntent.FLAG_MUTABLE)
		usb.requestPermission(device, pending)
	}

	/** Descripción de interfaces y endpoints para el informe del spike. */
	fun describe(device: UsbDevice): String = buildString {
		append("${device.productName ?: "DualSense"} vid=%04x pid=%04x".format(device.vendorId, device.productId))
		for(i in 0 until device.interfaceCount)
		{
			val intf = device.getInterface(i)
			append("\n  if#${intf.id} clase=${intf.interfaceClass} endpoints=${intf.endpointCount}")
			for(e in 0 until intf.endpointCount)
			{
				val ep = intf.getEndpoint(e)
				append("\n    ep 0x%02x %s tipo=%d".format(ep.address, if(ep.direction == UsbConstants.USB_DIR_IN) "IN" else "OUT", ep.type))
			}
		}
	}

	private fun hidInterface(device: UsbDevice): UsbInterface? =
		(0 until device.interfaceCount).map { device.getInterface(it) }.firstOrNull { it.interfaceClass == UsbConstants.USB_CLASS_HID }

	/** A1: SET_REPORT por el endpoint 0 SIN reclamar la interfaz (Android sigue entregando botones). */
	fun trySetReportWithoutClaim(device: UsbDevice, report: ByteArray): Attempt =
		attempt("A1 SET_REPORT sin reclamar", device) { connection, intf ->
			val r = connection.controlTransfer(
				REQUEST_TYPE_SET_REPORT, REQUEST_SET_REPORT, (REPORT_TYPE_OUTPUT shl 8) or report[0].toInt(), intf.id,
				report, report.size, TIMEOUT_MS)
			r to "controlTransfer devolvió $r (esperado ${report.size})"
		}

	/** A2: reclama la interfaz (con `force` desconecta el driver del kernel) y hace SET_REPORT. */
	fun trySetReportClaimed(device: UsbDevice, report: ByteArray, force: Boolean): Attempt =
		attempt("A2 SET_REPORT reclamando (force=$force)", device) { connection, intf ->
			if(!connection.claimInterface(intf, force))
				return@attempt -1 to "claimInterface(force=$force) falló"
			try
			{
				val r = connection.controlTransfer(
					REQUEST_TYPE_SET_REPORT, REQUEST_SET_REPORT, (REPORT_TYPE_OUTPUT shl 8) or report[0].toInt(), intf.id,
					report, report.size, TIMEOUT_MS)
				r to "controlTransfer devolvió $r (esperado ${report.size})"
			}
			finally { connection.releaseInterface(intf) }
		}

	/** A3: reclama la interfaz y escribe en el endpoint interrupt OUT (lo que hace hid-playstation). */
	fun tryInterruptOutClaimed(device: UsbDevice, report: ByteArray, force: Boolean): Attempt =
		attempt("A3 interrupt OUT reclamando (force=$force)", device) { connection, intf ->
			val ep = (0 until intf.endpointCount).map { intf.getEndpoint(it) }
				.firstOrNull { it.direction == UsbConstants.USB_DIR_OUT && it.type == UsbConstants.USB_ENDPOINT_XFER_INT }
				?: return@attempt -1 to "la interfaz HID no tiene endpoint interrupt OUT"
			if(!connection.claimInterface(intf, force))
				return@attempt -1 to "claimInterface(force=$force) falló"
			try
			{
				val r = connection.bulkTransfer(ep, report, report.size, TIMEOUT_MS)
				r to "bulkTransfer devolvió $r (esperado ${report.size})"
			}
			finally { connection.releaseInterface(intf) }
		}

	/**
	 * A5: reclama la interfaz (forzando) y lee reportes de entrada por el endpoint interrupt IN durante `durationMs`.
	 * Mide cuántos llegan, su tamaño y su report id (hid-playstation: id 0x01, 64 bytes por USB).
	 */
	fun tryReadInput(device: UsbDevice, durationMs: Long): Attempt =
		attempt("A5 leer input por interrupt IN", device) { connection, intf ->
			val ep = (0 until intf.endpointCount).map { intf.getEndpoint(it) }
				.firstOrNull { it.direction == UsbConstants.USB_DIR_IN && it.type == UsbConstants.USB_ENDPOINT_XFER_INT }
				?: return@attempt -1 to "la interfaz HID no tiene endpoint interrupt IN"
			if(!connection.claimInterface(intf, true))
				return@attempt -1 to "claimInterface(force=true) falló"
			try
			{
				val buffer = ByteArray(ep.maxPacketSize.coerceAtLeast(64))
				var count = 0
				var firstSize = 0
				var firstId = -1
				val ids = mutableSetOf<Int>()
				val start = System.nanoTime()
				while(System.nanoTime() - start < durationMs * 1_000_000)
				{
					val n = connection.bulkTransfer(ep, buffer, buffer.size, 100)
					if(n > 0)
					{
						if(count == 0) { firstSize = n; firstId = buffer[0].toInt() and 0xff }
						ids.add(buffer[0].toInt() and 0xff)
						count++
					}
				}
				val seconds = durationMs / 1000.0
				count to "$count reportes en ${seconds}s (%.0f/s), primer reporte: %d bytes, id 0x%02x, ids vistos=%s, maxPacketSize=%d"
					.format(count / seconds, firstSize, firstId, ids.joinToString { "0x%02x".format(it) }, ep.maxPacketSize)
			}
			finally { connection.releaseInterface(intf) }
		}

	private inline fun attempt(
		label: String,
		device: UsbDevice,
		block: (connection: android.hardware.usb.UsbDeviceConnection, intf: UsbInterface) -> Pair<Int, String>
	): Attempt
	{
		val intf = hidInterface(device) ?: return Attempt(label, false, "no hay interfaz HID").also { Log.w(TAG, "$label: ${it.detail}") }
		val connection = usb.openDevice(device) ?: return Attempt(label, false, "openDevice devolvió null (¿sin permiso?)").also { Log.w(TAG, "$label: ${it.detail}") }
		try
		{
			val (code, detail) = block(connection, intf)
			return Attempt(label, code > 0, detail).also { Log.i(TAG, "$label: ${it.ok} - $detail") }
		}
		catch(e: Exception)
		{
			return Attempt(label, false, "excepción ${e.javaClass.simpleName}: ${e.message}").also { Log.e(TAG, "$label: ${it.detail}", e) }
		}
		finally
		{
			connection.close()
		}
	}
}

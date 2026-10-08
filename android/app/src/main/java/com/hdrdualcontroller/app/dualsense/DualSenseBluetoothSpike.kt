// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.dualsense

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * SPIKE C (solo depuración): ¿puede la app abrir los canales HID L2CAP clásicos del DualSense por Bluetooth
 * (control PSM 0x11, interrupt PSM 0x13) y mandarle el reporte 0x31 con efectos de gatillo?
 *
 * Android no expone L2CAP clásico en la API pública, y su pila HID ya ocupa esos canales cuando el control está
 * conectado como "Input device". Este código solo mide qué pasa; no se integra hasta ver resultados.
 * Si funciona habrá que sacar `hiddenapibypass` de las dependencias de producción o aislarlo en un módulo opt-in.
 */
class DualSenseBluetoothSpike(private val context: Context)
{
	companion object
	{
		private const val PSM_HID_CONTROL = 0x11
		private const val PSM_HID_INTERRUPT = 0x13
		private const val CONNECT_TIMEOUT_S = 8L

		// HIDP (Bluetooth HID profile 1.1.1, 7.4/7.5): cabecera = tipo de transacción << 4 | parámetro.
		private const val HIDP_GET_REPORT_FEATURE = 0x43 // GET_REPORT, tipo Feature
		private const val HIDP_DATA_OUTPUT = 0xA2 // DATA, tipo Output
		private const val FEATURE_REPORT_CALIBRATION = 0x05 // SDL k_EPS5FeatureReportIdCalibration: activa el modo mejorado
	}

	private val executor = Executors.newCachedThreadPool()

	@SuppressLint("MissingPermission")
	fun bondedControllers(): List<BluetoothDevice> =
		context.getSystemService(BluetoothManager::class.java).adapter?.bondedDevices.orEmpty()
			.filter { it.name?.contains("Wireless Controller", true) == true || it.name?.contains("DualSense", true) == true }

	/** Qué parte de la API oculta existe en esta versión de Android (paso C1). */
	fun reflectionReport(log: (String) -> Unit)
	{
		fun methods(cls: Class<*>) = try { cls.declaredMethods.filter { it.name.contains("l2cap", true) }.map { it.toString() } }
			catch(e: Throwable) { listOf("error: ${e.message}") }
		log("Sin bypass, BluetoothDevice: ${methods(BluetoothDevice::class.java).ifEmpty { listOf("(ningún método l2cap visible)") }}")
		val exempted = try { HiddenApiBypass.addHiddenApiExemptions("") } catch(e: Throwable) { log("HiddenApiBypass falló: ${e.javaClass.simpleName}: ${e.message}"); false }
		log("addHiddenApiExemptions: $exempted")
		log("Con bypass, BluetoothDevice: ${methods(BluetoothDevice::class.java).ifEmpty { listOf("(ningún método l2cap)") }}")
		val ctors = try { BluetoothSocket::class.java.declaredConstructors.map { it.toString() } } catch(e: Throwable) { listOf("error: ${e.message}") }
		log("BluetoothSocket constructores: $ctors")
	}

	/** Intenta crear y conectar un socket L2CAP clásico al PSM dado. Devuelve el socket o null (con el motivo en el log). */
	private fun connectL2cap(device: BluetoothDevice, psm: Int, log: (String) -> Unit): BluetoothSocket?
	{
		val socket = createSocket(device, psm, log) ?: return null
		val future = executor.submit(Callable { socket.connect() })
		try
		{
			future.get(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
			log("PSM 0x%x: conectado".format(psm))
			return socket
		}
		catch(e: TimeoutException)
		{
			log("PSM 0x%x: timeout de ${CONNECT_TIMEOUT_S}s".format(psm))
		}
		catch(e: Exception)
		{
			log("PSM 0x%x: connect falló: ${(e.cause ?: e).javaClass.simpleName}: ${(e.cause ?: e).message}".format(psm))
		}
		try { socket.close() } catch(_: Exception) {}
		return null
	}

	private fun createSocket(device: BluetoothDevice, psm: Int, log: (String) -> Unit): BluetoothSocket?
	{
		for(name in listOf("createInsecureL2capSocket", "createL2capSocket"))
		{
			try
			{
				val method = BluetoothDevice::class.java.getDeclaredMethod(name, Int::class.javaPrimitiveType)
				method.isAccessible = true
				return method.invoke(device, psm) as BluetoothSocket
			}
			catch(e: NoSuchMethodException) { log("$name: no existe") }
			catch(e: Throwable) { log("$name: ${(e.cause ?: e).javaClass.simpleName}: ${(e.cause ?: e).message}") }
		}
		return null
	}

	private fun readWithTimeout(socket: BluetoothSocket, timeoutMs: Long): ByteArray?
	{
		val buf = ByteArray(128)
		val future = executor.submit(Callable { socket.inputStream.read(buf) })
		return try { buf.copyOf(future.get(timeoutMs, TimeUnit.MILLISECONDS).coerceAtLeast(0)) } catch(_: Exception) { future.cancel(true); null }
	}

	/**
	 * Secuencia completa, bloqueante (llamar desde un hilo de fondo):
	 * C1 reflexión -> C2 canales 0x11 y 0x13 -> C3 GET_REPORT feature 0x05 -> C4 efecto de gatillo.
	 */
	fun runSequence(device: BluetoothDevice, left: TriggerEffect?, right: TriggerEffect?, log: (String) -> Unit)
	{
		log("== C1: API oculta")
		reflectionReport(log)

		log("== C2: canales HID clásicos")
		val control = connectL2cap(device, PSM_HID_CONTROL, log)
		if(control == null) { log("FIN: sin canal de control. El sistema probablemente ocupa el PSM o no hay acceso."); return }
		val interrupt = connectL2cap(device, PSM_HID_INTERRUPT, log)
		if(interrupt == null) { control.close(); log("FIN: control conectado pero no el canal interrupt."); return }

		try
		{
			log("== C3: feature report 0x05 (activa el modo mejorado)")
			control.outputStream.write(byteArrayOf(HIDP_GET_REPORT_FEATURE.toByte(), FEATURE_REPORT_CALIBRATION.toByte()))
			control.outputStream.flush()
			val response = readWithTimeout(control, 3000)
			// Solo se registra el tamaño: el contenido es la calibración de sensores, sin interés aquí.
			log(if(response == null) "sin respuesta del control en 3s" else "respuesta de ${response.size} bytes, cabecera 0x%02x".format(response.firstOrNull() ?: 0))

			log("== C4: efecto de gatillo (0x31 + CRC)")
			val report = OutputReportBuilder.triggerEffectsBluetooth(left, right, sequence = 1)
			interrupt.outputStream.write(byteArrayOf(HIDP_DATA_OUTPUT.toByte()) + report)
			interrupt.outputStream.flush()
			log("enviados ${report.size + 1} bytes por el canal interrupt. Aprieta los gatillos AHORA (canales abiertos 15 s)")
			Thread.sleep(15_000)
		}
		catch(e: Exception)
		{
			log("error en C3/C4: ${e.javaClass.simpleName}: ${e.message}")
		}
		finally
		{
			try { interrupt.close() } catch(_: Exception) {}
			try { control.close() } catch(_: Exception) {}
		}
	}
}

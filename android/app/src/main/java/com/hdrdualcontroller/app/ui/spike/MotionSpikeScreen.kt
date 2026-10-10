// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.ui.spike

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.InputDevice
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hdrdualcontroller.app.ui.AppScaffold
import kotlinx.coroutines.delay

/**
 * SPIKE E (gyro): lee el acelerómetro y el giroscopio DEL CONTROL con `InputDevice.getSensorManager()` (API 31) y muestra
 * valores en vivo y la tasa real. Sirve para fijar el mapeo de ejes (en reposo y plano, el eje con +9,8 m/s² es la
 * gravedad) y ver si Android entrega los sensores del DualSense por Bluetooth. Solo depuración.
 */
@Composable
fun MotionSpikeScreen(onBack: () -> Unit)
{
	var info by remember { mutableStateOf("Buscando control…") }
	var accel by remember { mutableStateOf(FloatArray(3)) }
	var gyro by remember { mutableStateOf(FloatArray(3)) }
	var accelHz by remember { mutableStateOf(0) }
	var gyroHz by remember { mutableStateOf(0) }
	val counters = remember { IntArray(2) }

	DisposableEffect(Unit)
	{
		val sensorManagers = InputDevice.getDeviceIds().toList().mapNotNull { id ->
			InputDevice.getDevice(id)?.takeIf { it.vendorId == 0x054c }?.let { it to it.sensorManager }
		}
		val lines = StringBuilder()
		var chosen: SensorManager? = null
		for((device, manager) in sensorManagers)
		{
			val sensors = manager.getSensorList(Sensor.TYPE_ALL)
			lines.append("Dispositivo ${device.id} \"${device.name}\" vid=%04x pid=%04x fuentes=0x%x: %d sensores\n".format(device.vendorId, device.productId, device.sources, sensors.size))
			sensors.forEach { lines.append("  - ${it.name} tipo=${it.type} dinámico=${it.isDynamicSensor} minDelay=${it.minDelay}µs resolución=${it.resolution} rango=${it.maximumRange}\n") }
			if(chosen == null && manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null)
				chosen = manager
		}
		if(sensorManagers.isEmpty()) lines.append("No hay ningún InputDevice de Sony conectado.\n")
		info = lines.toString()

		val listener = object: SensorEventListener {
			override fun onSensorChanged(event: SensorEvent)
			{
				when(event.sensor.type)
				{
					Sensor.TYPE_ACCELEROMETER -> { accel = event.values.copyOf(3); counters[0]++ }
					Sensor.TYPE_GYROSCOPE -> { gyro = event.values.copyOf(3); counters[1]++ }
				}
			}
			override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
		}
		chosen?.let { manager ->
			listOfNotNull(manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE))
				.forEach { manager.registerListener(listener, it, 4000) }
		}
		onDispose { chosen?.unregisterListener(listener) }
	}

	// Tasa efectiva: se cuentan los eventos de cada segundo.
	LaunchedEffect(Unit)
	{
		while(true)
		{
			counters[0] = 0; counters[1] = 0
			delay(1000)
			accelHz = counters[0]; gyroHz = counters[1]
		}
	}

	AppScaffold(title = "SPIKE E: giroscopio del control", onBack = onBack) { padding ->
		Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()))
		{
			Text(info, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
			Text("Acelerómetro (m/s²) %.2f  %.2f  %.2f   [%d Hz]".format(accel[0], accel[1], accel[2], accelHz), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
			Text("Giroscopio  (rad/s) %.3f  %.3f  %.3f   [%d Hz]".format(gyro[0], gyro[1], gyro[2], gyroHz), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
			Text("Control plano, boca arriba: ¿qué eje marca ≈ +9,8? Luego inclínalo y gíralo para ver los signos.", fontSize = 12.sp)
		}
	}
}

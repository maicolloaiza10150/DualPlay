// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.ui.spike

import android.util.Log
import android.view.InputDevice
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hdrdualcontroller.app.dualsense.DualSenseUsbTransport
import com.hdrdualcontroller.app.dualsense.OutputReportBuilder
import com.hdrdualcontroller.app.dualsense.TriggerEffect
import com.hdrdualcontroller.app.ui.AppScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SPIKE A (docs/ARCHITECTURE.md 2.6): ¿se pueden mandar efectos de gatillo al DualSense por USB sin perder
 * el input nativo de Android? Solo se muestra en builds de depuración. No es UI de producto.
 */
@Composable
fun DualSenseSpikeScreen(onBack: () -> Unit)
{
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val transport = remember { DualSenseUsbTransport(context) }
	val log = remember { mutableStateListOf<String>() }
	val scroll = rememberScrollState()
	var effectName by remember { mutableStateOf("Resistencia constante") }

	val effects = listOf(
		"Resistencia constante" to TriggerEffect.CONSTANT_RESISTANCE,
		"Pulso con vibración" to TriggerEffect.PULSE,
		"Apagar efecto" to TriggerEffect.CLEAR)

	fun inputDevices(): String = InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }
		.filter { it.vendorId == DualSenseUsbTransport.VENDOR_SONY }
		.joinToString { "${it.name} (id ${it.id}, fuentes 0x%x)".format(it.sources) }
		.ifEmpty { "ninguno" }

	fun add(line: String) { log.add(line); Log.i(DualSenseUsbTransport.TAG, line) }
	LaunchedEffect(log.size) { scroll.scrollTo(scroll.maxValue) }

	fun report(): ByteArray
	{
		val effect = effects.first { it.first == effectName }.second
		return OutputReportBuilder.triggerEffects(effect, effect)
	}

	fun withDevice(block: (android.hardware.usb.UsbDevice) -> Unit)
	{
		val device = transport.findDevice()
		if(device == null) { add("No hay DualSense por USB. Conéctalo por cable al puerto USB-C (OTG)."); return }
		if(!transport.hasPermission(device)) { add("Falta permiso USB: usa el paso 1."); return }
		block(device)
	}

	fun runTest(test: (android.hardware.usb.UsbDevice, ByteArray) -> DualSenseUsbTransport.Attempt)
	{
		withDevice { device ->
			val data = report()
			add("Input antes: ${inputDevices()}")
			scope.launch {
				val result = withContext(Dispatchers.IO) { test(device, data) }
				add("${if(result.ok) "OK " else "FALLO "}${result.label}: ${result.detail}")
				delay(800) // margen para que Android quite/ponga el InputDevice
				add("Input después: ${inputDevices()}")
			}
		}
	}

	AppScaffold(title = "SPIKE A: gatillos por USB", onBack = onBack) { padding ->
		Column(
			Modifier.padding(padding).padding(16.dp).fillMaxSize(),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			Button(onClick = {
				val device = transport.findDevice()
				if(device == null)
					add("No hay DualSense por USB. Input Android: ${inputDevices()}")
				else
				{
					add(transport.describe(device))
					if(transport.hasPermission(device))
						add("Permiso USB ya concedido. Input Android: ${inputDevices()}")
					else
						transport.requestPermission(device) { add("Permiso USB: ${if(it) "concedido" else "denegado"}") }
				}
			}, modifier = Modifier.fillMaxWidth()) { Text("1. Detectar y pedir permiso") }

			Text("Efecto a enviar (ambos gatillos):")
			effects.forEach { (name, _) ->
				Row(
					Modifier.fillMaxWidth().selectable(selected = effectName == name, onClick = { effectName = name }),
					verticalAlignment = Alignment.CenterVertically
				) {
					RadioButton(selected = effectName == name, onClick = null)
					Text(name, Modifier.padding(start = 8.dp))
				}
			}

			OutlinedButton(onClick = { runTest { d, r -> transport.trySetReportWithoutClaim(d, r) } }, modifier = Modifier.fillMaxWidth()) {
				Text("A1: SET_REPORT sin reclamar (no pierde input)")
			}
			OutlinedButton(onClick = { runTest { d, r -> transport.trySetReportClaimed(d, r, force = false) } }, modifier = Modifier.fillMaxWidth()) {
				Text("A2a: reclamar sin forzar + SET_REPORT")
			}
			OutlinedButton(onClick = { runTest { d, r -> transport.trySetReportClaimed(d, r, force = true) } }, modifier = Modifier.fillMaxWidth()) {
				Text("A2b: reclamar FORZANDO + SET_REPORT")
			}
			OutlinedButton(onClick = { runTest { d, r -> transport.tryInterruptOutClaimed(d, r, force = true) } }, modifier = Modifier.fillMaxWidth()) {
				Text("A3: reclamar FORZANDO + interrupt OUT")
			}
			OutlinedButton(onClick = {
				withDevice { device ->
					val data = report()
					add("A4 inicio. Input antes: ${inputDevices()}")
					scope.launch {
						val result = withContext(Dispatchers.IO) { transport.tryInterruptOutClaimed(device, data, force = true) }
						add("${if(result.ok) "OK " else "FALLO "}${result.label}: ${result.detail}")
						val start = System.currentTimeMillis()
						var last = ""
						while(System.currentTimeMillis() - start < 15_000)
						{
							val now = inputDevices()
							if(now != last) { add("A4 +${System.currentTimeMillis() - start}ms Input: $now"); last = now }
							delay(250)
						}
						add("A4 fin. Input final: ${inputDevices()}")
					}
				}
			}, modifier = Modifier.fillMaxWidth()) { Text("A4: A3 y vigilar el input 15 s") }
			OutlinedButton(onClick = {
				withDevice { device ->
					add("A5 inicio. Input antes: ${inputDevices()}. Mueve sticks y pulsa botones.")
					scope.launch {
						val result = withContext(Dispatchers.IO) { transport.tryReadInput(device, 4000) }
						add("${if(result.ok) "OK " else "FALLO "}${result.label}: ${result.detail}")
						delay(800)
						add("A5 fin. Input después: ${inputDevices()}")
					}
				}
			}, modifier = Modifier.fillMaxWidth()) { Text("A5: leer reportes de entrada (4 s)") }
			OutlinedButton(onClick = { add("Input Android ahora: ${inputDevices()}") }, modifier = Modifier.fillMaxWidth()) {
				Text("Ver InputDevices")
			}

			Text(
				log.joinToString("\n"),
				fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp,
				modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll))
		}
	}
}

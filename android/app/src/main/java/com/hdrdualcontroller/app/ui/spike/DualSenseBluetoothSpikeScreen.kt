// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.ui.spike

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import com.hdrdualcontroller.app.dualsense.DualSenseBluetoothSpike
import com.hdrdualcontroller.app.dualsense.TriggerEffect
import com.hdrdualcontroller.app.ui.AppScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** SPIKE C (docs/ARCHITECTURE.md 4): gatillos por Bluetooth con canales L2CAP clásicos. Solo depuración. */
@Composable
fun DualSenseBluetoothSpikeScreen(onBack: () -> Unit)
{
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val spike = remember { DualSenseBluetoothSpike(context) }
	val log = remember { mutableStateListOf<String>() }
	val scroll = rememberScrollState()
	var devices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
	var selectedAddress by remember { mutableStateOf<String?>(null) }
	var effectName by remember { mutableStateOf("Resistencia constante") }
	var running by remember { mutableStateOf(false) }

	val effects = listOf(
		"Resistencia constante" to TriggerEffect.CONSTANT_RESISTANCE,
		"Pulso con vibración" to TriggerEffect.PULSE,
		"Apagar efecto" to TriggerEffect.CLEAR)

	fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

	fun refresh()
	{
		devices = spike.bondedControllers()
		if(selectedAddress == null) selectedAddress = devices.firstOrNull()?.address
		log.add(if(devices.isEmpty()) "No hay ningún DualSense emparejado." else "Emparejados: ${devices.joinToString { "${it.name} ${it.address}" }}")
	}

	val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
		if(granted) refresh() else log.add("Permiso BLUETOOTH_CONNECT denegado.")
	}

	LaunchedEffect(log.size) { scroll.scrollTo(scroll.maxValue) }

	AppScaffold(title = "SPIKE C: gatillos por Bluetooth", onBack = onBack) { padding ->
		Column(
			Modifier.padding(padding).padding(16.dp).fillMaxSize(),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			Text(
				"Antes: en Ajustes de Bluetooth de Android abre el control emparejado y DESACTIVA el perfil " +
					"\"Dispositivo de entrada\" (si no, el sistema ocupa los canales). Luego enciende el control con el botón PS.",
				fontSize = 12.sp)

			Button(onClick = { if(hasPermission()) refresh() else permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) },
				modifier = Modifier.fillMaxWidth()) { Text("1. Buscar controles emparejados") }

			devices.forEach { device ->
				Row(
					Modifier.fillMaxWidth().selectable(selected = selectedAddress == device.address, onClick = { selectedAddress = device.address }),
					verticalAlignment = Alignment.CenterVertically
				) {
					RadioButton(selected = selectedAddress == device.address, onClick = null)
					@Suppress("MissingPermission")
					Text("${device.name} (${device.address})", Modifier.padding(start = 8.dp))
				}
			}

			effects.forEach { (name, _) ->
				Row(
					Modifier.fillMaxWidth().selectable(selected = effectName == name, onClick = { effectName = name }),
					verticalAlignment = Alignment.CenterVertically
				) {
					RadioButton(selected = effectName == name, onClick = null)
					Text(name, Modifier.padding(start = 8.dp))
				}
			}

			OutlinedButton(
				enabled = !running && selectedAddress != null,
				onClick = {
					val device = devices.firstOrNull { it.address == selectedAddress } ?: return@OutlinedButton
					val effect = effects.first { it.first == effectName }.second
					running = true
					scope.launch {
						withContext(Dispatchers.IO) {
							try { spike.runSequence(device, effect, effect) { line -> scope.launch(Dispatchers.Main) { log.add(line) } } }
							catch(e: Throwable) { scope.launch(Dispatchers.Main) { log.add("excepción: ${e.javaClass.simpleName}: ${e.message}") } }
						}
						running = false
					}
				},
				modifier = Modifier.fillMaxWidth()
			) { Text(if(running) "Ejecutando…" else "2. Ejecutar secuencia C1-C4") }

			Text(
				log.joinToString("\n"),
				fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp,
				modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll))
		}
	}
}

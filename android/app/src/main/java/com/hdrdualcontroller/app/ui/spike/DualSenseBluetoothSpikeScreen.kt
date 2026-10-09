// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.ui.spike

import android.Manifest
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
import com.hdrdualcontroller.app.dualsense.DualSenseBluetoothHidSpike
import com.hdrdualcontroller.app.dualsense.TriggerEffect
import com.hdrdualcontroller.app.ui.AppScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** SPIKE D (docs/ARCHITECTURE.md 4): gatillos por Bluetooth con BluetoothHidHost.sendData (API oculta). Solo depuración. */
@Composable
fun DualSenseBluetoothSpikeScreen(onBack: () -> Unit)
{
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val spike = remember { DualSenseBluetoothHidSpike(context) }
	val log = remember { mutableStateListOf<String>() }
	val scroll = rememberScrollState()
	var effectName by remember { mutableStateOf("Resistencia constante") }
	var running by remember { mutableStateOf(false) }

	val effects = listOf(
		"Resistencia constante" to TriggerEffect.CONSTANT_RESISTANCE,
		"Pulso con vibración" to TriggerEffect.PULSE,
		"Apagar efecto" to TriggerEffect.CLEAR)

	fun run()
	{
		val effect = effects.first { it.first == effectName }.second
		running = true
		scope.launch {
			withContext(Dispatchers.IO) {
				try { spike.send(effect, effect) { line -> scope.launch(Dispatchers.Main) { log.add(line) } } }
				catch(e: Throwable) { scope.launch(Dispatchers.Main) { log.add("excepción: ${e.javaClass.simpleName}: ${e.message}") } }
			}
			running = false
		}
	}

	val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
		if(granted) run() else log.add("Permiso BLUETOOTH_CONNECT denegado.")
	}

	LaunchedEffect(log.size) { scroll.scrollTo(scroll.maxValue) }

	AppScaffold(title = "SPIKE D: gatillos por Bluetooth", onBack = onBack) { padding ->
		Column(
			Modifier.padding(padding).padding(16.dp).fillMaxSize(),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			Text(
				"Antes: el DualSense debe estar emparejado, ENCENDIDO y conectado por Bluetooth como 'Dispositivo de entrada' " +
					"(perfil activado). Sin cable USB.",
				fontSize = 12.sp)

			effects.forEach { (name, _) ->
				Row(
					Modifier.fillMaxWidth().selectable(selected = effectName == name, onClick = { effectName = name }),
					verticalAlignment = Alignment.CenterVertically
				) {
					RadioButton(selected = effectName == name, onClick = null)
					Text(name, Modifier.padding(start = 8.dp))
				}
			}

			Button(
				enabled = !running,
				onClick = {
					if(ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) run()
					else permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
				},
				modifier = Modifier.fillMaxWidth()
			) { Text(if(running) "Ejecutando…" else "Enviar efecto por BluetoothHidHost.sendData") }

			Text(
				log.joinToString("\n"),
				fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp,
				modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll))
		}
	}
}

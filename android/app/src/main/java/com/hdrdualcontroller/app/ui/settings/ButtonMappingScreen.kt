// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.ui.settings

import android.view.KeyEvent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hdrdualcontroller.app.R
import com.hdrdualcontroller.app.common.Preferences
import com.hdrdualcontroller.app.input.ButtonMapping
import com.hdrdualcontroller.app.input.PsButton
import com.hdrdualcontroller.app.ui.AppScaffold

private fun PsButton.labelRes() = when(this)
{
	PsButton.CROSS -> R.string.ps_button_cross
	PsButton.MOON -> R.string.ps_button_moon
	PsButton.BOX -> R.string.ps_button_box
	PsButton.PYRAMID -> R.string.ps_button_pyramid
	PsButton.L1 -> R.string.ps_button_l1
	PsButton.R1 -> R.string.ps_button_r1
	PsButton.L2 -> R.string.ps_button_l2
	PsButton.R2 -> R.string.ps_button_r2
	PsButton.L3 -> R.string.ps_button_l3
	PsButton.R3 -> R.string.ps_button_r3
	PsButton.SHARE -> R.string.ps_button_share
	PsButton.OPTIONS -> R.string.ps_button_options
	PsButton.PS -> R.string.ps_button_ps
	PsButton.TOUCHPAD -> R.string.ps_button_touchpad
	PsButton.DPAD_UP -> R.string.ps_button_dpad_up
	PsButton.DPAD_DOWN -> R.string.ps_button_dpad_down
	PsButton.DPAD_LEFT -> R.string.ps_button_dpad_left
	PsButton.DPAD_RIGHT -> R.string.ps_button_dpad_right
}

/**
 * Remapeo de botones: se toca un botón de PlayStation y se pulsa el botón del control que debe activarlo. Cualquier
 * tecla que Android entregue sirve, también las de botones extra (paletas del Flydigi Vader 4 Pro, 8BitDo, etc.).
 */
@Composable
fun ButtonMappingScreen(onBack: () -> Unit)
{
	val context = LocalContext.current
	val prefs = remember { Preferences(context) }
	var mapping by remember { mutableStateOf(ButtonMapping.parse(prefs.buttonRemap, prefs.swapCrossMoon)) }
	var learning by remember { mutableStateOf<PsButton?>(null) }

	fun save(newMapping: ButtonMapping)
	{
		mapping = newMapping
		prefs.buttonRemap = newMapping.serialize()
	}

	AppScaffold(title = stringResource(R.string.preferences_button_remap_title), onBack = onBack) { padding ->
		Column(Modifier.padding(padding).verticalScroll(rememberScrollState()))
		{
			PsButton.entries.forEach { button ->
				val keys = mapping.keysFor(button)
				ListItem(
					headlineContent = { Text(stringResource(button.labelRes())) },
					supportingContent = {
						Text(if(keys.isEmpty()) stringResource(R.string.remap_unassigned)
							else keys.joinToString(", ") { KeyEvent.keyCodeToString(it).removePrefix("KEYCODE_") })
					},
					modifier = Modifier.clickable { learning = button })
				HorizontalDivider()
			}
			ListItem(
				headlineContent = { Text(stringResource(R.string.remap_reset_title)) },
				supportingContent = { Text(stringResource(R.string.remap_reset_summary)) },
				modifier = Modifier.clickable { save(ButtonMapping(emptyMap(), prefs.swapCrossMoon)) })
		}
	}

	learning?.let { target ->
		val focus = remember { FocusRequester() }
		LaunchedEffect(Unit) { focus.requestFocus() }
		AlertDialog(
			onDismissRequest = { learning = null },
			confirmButton = { TextButton(onClick = { learning = null }) { Text(stringResource(android.R.string.cancel)) } },
			title = { Text(stringResource(R.string.remap_press_prompt, stringResource(target.labelRes()))) },
			text = {
				Box(
					Modifier
						.fillMaxWidth()
						.focusRequester(focus)
						.onPreviewKeyEvent { event ->
							val code = event.key.nativeKeyCode
							// Atrás cancela (lo gestiona el diálogo); el resto se toma como la tecla a asignar.
							if(code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_UNKNOWN)
								false
							else
							{
								if(event.type == KeyEventType.KeyDown)
								{
									save(mapping.assign(code, target))
									learning = null
								}
								true
							}
						}
						.padding(vertical = 16.dp))
				{
					Text(stringResource(R.string.remap_press_hint))
				}
			})
	}
}

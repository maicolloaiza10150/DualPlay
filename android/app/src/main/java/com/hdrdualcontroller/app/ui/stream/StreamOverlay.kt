// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.ui.stream

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hdrdualcontroller.app.R
import com.hdrdualcontroller.app.lib.QuitReason
import com.hdrdualcontroller.app.session.StreamState
import com.hdrdualcontroller.app.session.StreamStateConnected
import com.hdrdualcontroller.app.session.StreamStateConnecting
import com.hdrdualcontroller.app.session.StreamStateCreateError
import com.hdrdualcontroller.app.session.StreamStateIdle
import com.hdrdualcontroller.app.session.StreamStateLoginPinRequest
import com.hdrdualcontroller.app.session.StreamStateQuit
import com.hdrdualcontroller.app.stream.StreamErrorKind
import com.hdrdualcontroller.app.stream.TransformMode

/** Estado y acciones del panel de opciones del stream; la actividad lo posee y lo conecta con la sesión. */
class StreamOptions(
	val onScreenControls: Boolean,
	val touchpadOnly: Boolean,
	val microphone: Boolean,
	val displayMode: TransformMode,
	val controlsOpacityPercent: Int,
	val onOnScreenControls: (Boolean) -> Unit,
	val onTouchpadOnly: (Boolean) -> Unit,
	val onMicrophone: (Boolean) -> Unit,
	val onDisplayMode: (TransformMode) -> Unit,
	val onControlsOpacity: (Int) -> Unit
)

@Composable
fun StreamOverlay(
	consoleName: String,
	state: StreamState,
	panelOpen: Boolean,
	onPanelOpenChange: (Boolean) -> Unit,
	options: StreamOptions,
	onReconnect: () -> Unit,
	onQuit: () -> Unit,
	onSubmitPin: (String) -> Unit
)
{
	Box(Modifier.fillMaxSize())
	{
		// Botón fijo de arriba: abre el panel sin depender del gesto de borde.
		if(state is StreamStateConnected && !panelOpen)
			IconButton(
				onClick = { onPanelOpenChange(true) },
				modifier = Modifier
					.align(Alignment.TopCenter)
					.windowInsetsPadding(WindowInsets.displayCutout)
					.padding(top = 2.dp)
					.size(48.dp)
					.alpha(0.4f)
			) {
				Icon(
					painterResource(R.drawable.ic_gamepad), stringResource(R.string.stream_overlay_open),
					tint = Color.White)
			}

		if(state is StreamStateConnecting || state is StreamStateIdle)
			ConnectingScreen(consoleName, onCancel = onQuit)

		if(panelOpen)
			OptionsPanel(options, onClose = { onPanelOpenChange(false) }, onQuit = onQuit)

		when(state)
		{
			is StreamStateQuit -> if(state.reason.isError)
				ErrorDialog(
					kind = StreamErrorKind.fromQuitReason(state.reason.value),
					detail = errorDetail(state.reason, state.reasonString),
					onReconnect = onReconnect, onQuit = onQuit)
			is StreamStateCreateError ->
				ErrorDialog(
					kind = StreamErrorKind.GENERIC,
					detail = state.error.errorCode.toString(),
					onReconnect = null, onQuit = onQuit)
			is StreamStateLoginPinRequest -> PinDialog(state.pinIncorrect, onSubmitPin, onQuit)
			else -> {}
		}
	}
}

private fun errorDetail(reason: QuitReason, reasonString: String?)
	= reason.toString() + (if(reasonString != null) " — $reasonString" else "")

@Composable
private fun ConnectingScreen(consoleName: String, onCancel: () -> Unit)
{
	Column(
		Modifier
			.fillMaxSize()
			.background(Color.Black)
			// Absorbe los toques para que no lleguen a los controles de pantalla mientras conecta.
			.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
			.windowInsetsPadding(WindowInsets.safeDrawing),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.Center
	) {
		CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
		Text(
			stringResource(R.string.stream_connecting_to, consoleName),
			style = MaterialTheme.typography.titleMedium, color = Color.White,
			textAlign = TextAlign.Center, modifier = Modifier.padding(top = 24.dp))
		Text(
			stringResource(R.string.stream_connecting_hint),
			style = MaterialTheme.typography.bodyMedium, color = Color(0xFFB4C2D6),
			textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, start = 32.dp, end = 32.dp))
		OutlinedButton(onClick = onCancel, modifier = Modifier.padding(top = 24.dp).heightIn(min = 48.dp)) {
			Text(stringResource(R.string.action_cancel))
		}
	}
}

@Composable
private fun OptionsPanel(options: StreamOptions, onClose: () -> Unit, onQuit: () -> Unit)
{
	// Toque fuera del panel = cerrar.
	Box(
		Modifier
			.fillMaxSize()
			.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose))
	Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.BottomCenter)
	{
		Surface(
			modifier = Modifier
				.widthIn(max = 640.dp)
				.fillMaxWidth()
				.padding(8.dp)
				// El panel consume sus propios toques (no cierran).
				.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
			shape = RoundedCornerShape(24.dp),
			color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f),
			tonalElevation = 6.dp
		) {
			Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
			{
				Row(verticalAlignment = Alignment.CenterVertically)
				{
					Text(
						stringResource(R.string.stream_options_title),
						style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
					TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_done)) }
				}

				SwitchRow(stringResource(R.string.stream_on_screen_controls), options.onScreenControls, options.onOnScreenControls)
				SwitchRow(stringResource(R.string.stream_touchpad_only), options.touchpadOnly, options.onTouchpadOnly)
				SwitchRow(stringResource(R.string.stream_microphone), options.microphone, options.onMicrophone)

				Text(
					stringResource(R.string.stream_display_mode),
					style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(top = 8.dp))
				Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp))
				{
					DisplayModeChip(TransformMode.FIT, R.string.stream_display_fit, options)
					DisplayModeChip(TransformMode.ZOOM, R.string.stream_display_zoom, options)
					DisplayModeChip(TransformMode.STRETCH, R.string.stream_display_stretch, options)
				}

				Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically)
				{
					Text(
						stringResource(R.string.stream_controls_opacity),
						style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
					Text(
						"${options.controlsOpacityPercent}%",
						style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
				}
				Slider(
					value = options.controlsOpacityPercent.toFloat(),
					onValueChange = { options.onControlsOpacity(it.toInt()) },
					valueRange = 20f..100f)

				OutlinedButton(onClick = onQuit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
					Text(stringResource(R.string.stream_end_session))
				}
			}
		}
	}
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit)
{
	Row(
		Modifier
			.fillMaxWidth()
			.heightIn(min = 48.dp)
			.clickable(role = Role.Switch) { onChange(!checked) },
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
		Switch(checked = checked, onCheckedChange = null)
	}
}

@Composable
private fun DisplayModeChip(mode: TransformMode, label: Int, options: StreamOptions)
{
	FilterChip(
		selected = options.displayMode == mode,
		onClick = { options.onDisplayMode(mode) },
		label = { Text(stringResource(label)) },
		modifier = Modifier.heightIn(min = 48.dp))
}

@Composable
private fun ErrorDialog(kind: StreamErrorKind, detail: String, onReconnect: (() -> Unit)?, onQuit: () -> Unit)
{
	AlertDialog(
		onDismissRequest = onQuit,
		title = { Text(stringResource(kind.title)) },
		text = {
			Column {
				Text(stringResource(kind.body))
				Text(
					detail,
					style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(top = 12.dp))
			}
		},
		confirmButton = {
			if(onReconnect != null)
				Button(onClick = onReconnect, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_reconnect)) }
			else
				Button(onClick = onQuit, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_quit_session)) }
		},
		dismissButton = {
			if(onReconnect != null)
				TextButton(onClick = onQuit, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_quit_session)) }
		})
}

@Composable
private fun PinDialog(incorrect: Boolean, onSubmit: (String) -> Unit, onQuit: () -> Unit)
{
	var pin by remember { mutableStateOf("") }
	AlertDialog(
		onDismissRequest = onQuit,
		title = { Text(stringResource(R.string.alert_message_login_pin_request)) },
		text = {
			OutlinedTextField(
				value = pin,
				onValueChange = { pin = it.filter(Char::isDigit).take(8) },
				singleLine = true,
				isError = incorrect,
				supportingText = if(incorrect) ({ Text(stringResource(R.string.stream_pin_incorrect)) }) else null,
				visualTransformation = PasswordVisualTransformation(),
				keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
		},
		confirmButton = {
			Button(onClick = { onSubmit(pin) }, enabled = pin.isNotEmpty(), modifier = Modifier.heightIn(min = 48.dp)) {
				Text(stringResource(R.string.action_login_pin_connect))
			}
		},
		dismissButton = {
			TextButton(onClick = onQuit, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_quit_session)) }
		})
}

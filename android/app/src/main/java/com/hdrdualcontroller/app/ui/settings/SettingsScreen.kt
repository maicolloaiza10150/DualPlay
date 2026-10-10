// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hdrdualcontroller.app.R
import com.hdrdualcontroller.app.common.Preferences
import com.hdrdualcontroller.app.common.exportAndShareAllSettings
import com.hdrdualcontroller.app.common.ext.viewModelFactory
import com.hdrdualcontroller.app.common.getDatabase
import com.hdrdualcontroller.app.common.importSettingsFromUri
import com.hdrdualcontroller.app.psn.PsnAccountStore
import com.hdrdualcontroller.app.settings.SettingsViewModel
import com.hdrdualcontroller.app.ui.AppScaffold
import com.hdrdualcontroller.app.ui.DrawableIcon

private tailrec fun Context.findActivity(): Activity? = when(this)
{
	is Activity -> this
	is ContextWrapper -> baseContext.findActivity()
	else -> null
}

@Composable
fun SettingsScreen(
	onBack: () -> Unit,
	onOpenRegisteredHosts: () -> Unit,
	onOpenLogs: () -> Unit,
	onOpenButtonMapping: () -> Unit,
	onPsnLogin: () -> Unit,
	onOpenDualSenseSpike: () -> Unit,
	onOpenBluetoothSpike: () -> Unit,
	onOpenMotionSpike: () -> Unit
)
{
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val viewModel: SettingsViewModel = viewModel(factory = viewModelFactory { SettingsViewModel(getDatabase(context), Preferences(context)) })
	val prefs = viewModel.preferences
	val psnStore = remember { PsnAccountStore(context) }

	var swapCrossMoon by remember { mutableStateOf(prefs.swapCrossMoon) }
	var rumble by remember { mutableStateOf(prefs.rumbleEnabled) }
	var motion by remember { mutableStateOf(prefs.motionEnabled) }
	var buttonHaptic by remember { mutableStateOf(prefs.buttonHapticEnabled) }
	var logVerbose by remember { mutableStateOf(prefs.logVerbose) }
	var dualsenseFeatures by remember { mutableStateOf(prefs.dualsenseFeatures) }
	var hdHaptics by remember { mutableStateOf(prefs.hdHaptics) }
	var hdSingle by remember { mutableStateOf(prefs.hdHapticsSingleBlock) }
	var hdGain by remember { mutableStateOf(prefs.hdHapticsGainPercent) }
	var showHdGain by remember { mutableStateOf(false) }
	var hdBuffer by remember { mutableStateOf(prefs.hdHapticsBuffer) }
	var showHdBuffer by remember { mutableStateOf(false) }
	var resolution by remember { mutableStateOf(prefs.resolution) }
	var fps by remember { mutableStateOf(prefs.fps) }
	var codec by remember { mutableStateOf(prefs.codec) }
	var bitrate by remember { mutableStateOf(prefs.bitrate) }
	val bitrateAuto by viewModel.bitrateAuto.observeAsState(prefs.bitrateAuto)
	val registeredCount by viewModel.registeredHostsCount.observeAsState(0)

	// El login de PSN vive en otra pantalla: se relee el Account ID al volver.
	var psnAccountId by remember { mutableStateOf<String?>(null) }
	var psnOnlineId by remember { mutableStateOf<String?>(null) }
	LifecycleResumeEffect(Unit) {
		psnAccountId = psnStore.accountId
		psnOnlineId = psnStore.onlineId
		onPauseOrDispose { }
	}

	var showResolution by remember { mutableStateOf(false) }
	var showFps by remember { mutableStateOf(false) }
	var showCodec by remember { mutableStateOf(false) }
	var showBitrate by remember { mutableStateOf(false) }

	// Los efectos de gatillo por Bluetooth necesitan BLUETOOTH_CONNECT: se pide al activar la opción.
	val bluetoothPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
		if(granted)
		{
			dualsenseFeatures = true
			prefs.dualsenseFeatures = true
		}
		else
			Toast.makeText(context, R.string.preferences_dualsense_features_denied, Toast.LENGTH_LONG).show()
	}

	val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
		val activity = context.findActivity()
		if(uri != null && activity != null)
			importSettingsFromUri(activity, uri, scope)
	}

	AppScaffold(title = stringResource(R.string.title_settings), onBack = onBack) { padding ->
		Column(Modifier.padding(padding).verticalScroll(rememberScrollState()))
		{
			Section(R.string.psn_account_title)
			if(psnAccountId == null)
				ClickItem(
					R.drawable.ic_psn_id, R.string.psn_sign_in,
					stringResource(R.string.psn_account_signed_out), onPsnLogin)
			else
			{
				ClickItem(
					R.drawable.ic_psn_id, R.string.psn_account_title,
					psnOnlineId?.let { stringResource(R.string.psn_account_signed_in, it) }
						?: stringResource(R.string.psn_account_signed_in_unknown_name),
					onPsnLogin)
				ClickItem(
					R.drawable.ic_close, R.string.psn_sign_out,
					stringResource(R.string.psn_sign_out_summary)
				) {
					psnStore.clear()
					psnAccountId = null
					psnOnlineId = null
				}
			}

			HorizontalDivider()
			Section(R.string.preferences_category_title_general)
			ClickItem(
				R.drawable.ic_console_simple, R.string.preferences_registered_hosts_title,
				stringResource(R.string.preferences_registered_hosts_summary, registeredCount), onOpenRegisteredHosts)
			SwitchItem(R.drawable.ic_gamepad, R.string.preferences_swap_cross_moon_title, R.string.preferences_swap_cross_moon_summary, swapCrossMoon) {
				swapCrossMoon = it; prefs.swapCrossMoon = it
			}
			ClickItem(R.drawable.ic_gamepad, R.string.preferences_button_remap_title, stringResource(R.string.preferences_button_remap_summary), onOpenButtonMapping)
			SwitchItem(R.drawable.ic_rumble, R.string.preferences_rumble_enabled_title, R.string.preferences_rumble_enabled_summary, rumble) {
				rumble = it; prefs.rumbleEnabled = it
			}
			SwitchItem(R.drawable.ic_motion, R.string.preferences_motion_enabled_title, R.string.preferences_motion_enabled_summary, motion) {
				motion = it; prefs.motionEnabled = it
			}
			SwitchItem(R.drawable.ic_button_haptic, R.string.preferences_button_haptic_enabled_title, R.string.preferences_button_haptic_enabled_summary, buttonHaptic) {
				buttonHaptic = it; prefs.buttonHapticEnabled = it
			}
			SwitchItem(R.drawable.ic_gamepad, R.string.preferences_dualsense_features_title, R.string.preferences_dualsense_features_summary, dualsenseFeatures) { enabled ->
				if(!enabled)
				{
					dualsenseFeatures = false
					prefs.dualsenseFeatures = false
				}
				else if(ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
				{
					dualsenseFeatures = true
					prefs.dualsenseFeatures = true
				}
				else
					bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
			}
			SwitchItem(R.drawable.ic_rumble, R.string.preferences_hd_haptics_title, R.string.preferences_hd_haptics_summary, hdHaptics) {
				hdHaptics = it; prefs.hdHaptics = it
			}
			ClickItem(R.drawable.ic_rumble, R.string.preferences_hd_haptics_gain_title, stringResource(R.string.preferences_hd_haptics_gain_value, hdGain)) { showHdGain = true }
			ClickItem(R.drawable.ic_rumble, R.string.preferences_hd_haptics_buffer_title, stringResource(R.string.preferences_hd_haptics_buffer_value, hdBuffer)) { showHdBuffer = true }
			SwitchItem(R.drawable.ic_rumble, R.string.preferences_hd_haptics_single_title, R.string.preferences_hd_haptics_single_summary, hdSingle) {
				hdSingle = it; prefs.hdHapticsSingleBlock = it
			}
			SwitchItem(R.drawable.ic_log, R.string.preferences_log_verbose_title, R.string.preferences_log_verbose_summary, logVerbose) {
				logVerbose = it; prefs.logVerbose = it
			}
			ClickItem(R.drawable.ic_log, R.string.preferences_logs_title, stringResource(R.string.preferences_logs_summary), onOpenLogs)

			HorizontalDivider()
			Section(R.string.preferences_category_title_stream)
			ClickItem(R.drawable.ic_resolution, R.string.preferences_resolution_title, stringResource(resolution.title)) { showResolution = true }
			ClickItem(R.drawable.ic_fps, R.string.preferences_fps_title, stringResource(fps.title)) { showFps = true }
			ClickItem(
				R.drawable.ic_bitrate, R.string.preferences_bitrate_title,
				bitrate?.toString() ?: stringResource(R.string.preferences_bitrate_auto, bitrateAuto)
			) { showBitrate = true }
			ClickItem(R.drawable.ic_codec, R.string.preferences_codec_title, stringResource(codec.title)) { showCodec = true }

			HorizontalDivider()
			Section(R.string.preferences_category_title_export)
			ClickItem(R.drawable.ic_export, R.string.preferences_export_settings_title, stringResource(R.string.preferences_export_settings_summary)) {
				context.findActivity()?.let { exportAndShareAllSettings(it, scope) }
			}
			ClickItem(R.drawable.ic_import, R.string.preferences_import_settings_title, stringResource(R.string.preferences_import_settings_summary)) {
				importLauncher.launch(arrayOf("application/json"))
			}

			// Solo en builds de depuración: herramientas de los SPIKE de docs/ARCHITECTURE.md.
			if(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
			{
				HorizontalDivider()
				Section(R.string.developer_category_title)
				ClickItem(R.drawable.ic_gamepad, R.string.developer_dualsense_spike_title, stringResource(R.string.developer_dualsense_spike_summary), onOpenDualSenseSpike)
				ClickItem(R.drawable.ic_gamepad, R.string.developer_bluetooth_spike_title, stringResource(R.string.developer_bluetooth_spike_summary), onOpenBluetoothSpike)
				ClickItem(R.drawable.ic_motion, R.string.developer_motion_spike_title, stringResource(R.string.developer_motion_spike_summary), onOpenMotionSpike)
			}
		}
	}

	if(showResolution)
		ChoiceDialog(
			R.string.preferences_resolution_title, Preferences.resolutionAll.toList(), resolution,
			{ stringResource(it.title) }, { showResolution = false }) { resolution = it; prefs.resolution = it }
	if(showFps)
		ChoiceDialog(
			R.string.preferences_fps_title, Preferences.fpsAll.toList(), fps,
			{ stringResource(it.title) }, { showFps = false }) { fps = it; prefs.fps = it }
	if(showCodec)
		ChoiceDialog(
			R.string.preferences_codec_title, Preferences.codecAll.toList(), codec,
			{ stringResource(it.title) }, { showCodec = false }) { codec = it; prefs.codec = it }
	if(showHdGain)
		ChoiceDialog(
			R.string.preferences_hd_haptics_gain_title, listOf(50, 100, 150, 200, 300, 400), hdGain,
			{ stringResource(R.string.preferences_hd_haptics_gain_value, it) }, { showHdGain = false }) { hdGain = it; prefs.hdHapticsGainPercent = it }
	if(showHdBuffer)
		ChoiceDialog(
			R.string.preferences_hd_haptics_buffer_title, listOf(16, 24, 32, 48, 64), hdBuffer,
			{ stringResource(R.string.preferences_hd_haptics_buffer_value, it) }, { showHdBuffer = false }) { hdBuffer = it; prefs.hdHapticsBuffer = it }
	if(showBitrate)
		BitrateDialog(bitrate, bitrateAuto, { showBitrate = false }) {
			prefs.bitrate = it
			bitrate = prefs.bitrate
		}
}

@Composable
private fun Section(@StringRes title: Int)
{
	Text(
		stringResource(title),
		style = MaterialTheme.typography.titleSmall,
		color = MaterialTheme.colorScheme.primary,
		modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp))
}

@Composable
private fun ClickItem(@DrawableRes icon: Int, @StringRes title: Int, summary: String, onClick: () -> Unit)
{
	ListItem(
		headlineContent = { Text(stringResource(title)) },
		supportingContent = { Text(summary) },
		leadingContent = { DrawableIcon(icon, null) },
		modifier = Modifier.clickable(onClick = onClick))
}

@Composable
private fun SwitchItem(@DrawableRes icon: Int, @StringRes title: Int, @StringRes summary: Int, checked: Boolean, onChange: (Boolean) -> Unit)
{
	ListItem(
		headlineContent = { Text(stringResource(title)) },
		supportingContent = { Text(stringResource(summary)) },
		leadingContent = { DrawableIcon(icon, null) },
		trailingContent = { Switch(checked = checked, onCheckedChange = null) },
		modifier = Modifier.clickable { onChange(!checked) })
}

@Composable
private fun <T> ChoiceDialog(
	@StringRes title: Int,
	options: List<T>,
	selected: T,
	label: @Composable (T) -> String,
	onDismiss: () -> Unit,
	onSelect: (T) -> Unit
)
{
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(stringResource(title)) },
		text = {
			Column {
				options.forEach { option ->
					Row(
						Modifier.fillMaxWidth()
							.selectable(selected = option == selected, onClick = { onSelect(option); onDismiss() })
							.padding(vertical = 8.dp),
						verticalAlignment = Alignment.CenterVertically
					) {
						RadioButton(selected = option == selected, onClick = null)
						Text(label(option), Modifier.padding(start = 16.dp))
					}
				}
			}
		},
		confirmButton = {},
		dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } })
}

@Composable
private fun BitrateDialog(current: Int?, auto: Int, onDismiss: () -> Unit, onSave: (Int?) -> Unit)
{
	var text by remember { mutableStateOf(current?.toString() ?: "") }
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(stringResource(R.string.preferences_bitrate_title)) },
		text = {
			OutlinedTextField(
				value = text,
				onValueChange = { new -> text = new.filter { it.isDigit() }.take(6) },
				singleLine = true,
				placeholder = { Text(stringResource(R.string.preferences_bitrate_auto, auto)) },
				supportingText = { Text(stringResource(R.string.preferences_bitrate_dialog_hint)) },
				keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
		},
		confirmButton = {
			TextButton(onClick = { onSave(text.toIntOrNull()); onDismiss() }) { Text(stringResource(R.string.action_ok)) }
		},
		dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } })
}

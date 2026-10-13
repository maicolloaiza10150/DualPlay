// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.hdrdualcontroller.app.R
import com.hdrdualcontroller.app.common.Preferences
import com.hdrdualcontroller.app.psn.PsnAccountStore

private const val STEP_COUNT = 4

private fun Context.granted(permission: String)
	= ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/**
 * Primer arranque: explica cada paso antes de pedir nada. Todo es opcional salvo registrar la consola, y cada acción
 * se puede hacer más tarde desde Ajustes.
 */
@Composable
fun OnboardingScreen(onPsnLogin: () -> Unit, onFinish: (registerNow: Boolean) -> Unit)
{
	val context = LocalContext.current
	val prefs = remember { Preferences(context) }
	val psnStore = remember { PsnAccountStore(context) }

	var step by rememberSaveable { mutableStateOf(0) }
	var psnSignedIn by remember { mutableStateOf(false) }
	var bluetoothGranted by remember { mutableStateOf(context.granted(Manifest.permission.BLUETOOTH_CONNECT)) }
	var micGranted by remember { mutableStateOf(context.granted(Manifest.permission.RECORD_AUDIO)) }

	// El login de PSN vive en otra pantalla: se relee al volver.
	LifecycleResumeEffect(Unit) {
		psnSignedIn = psnStore.accountId != null
		onPauseOrDispose { }
	}

	val bluetoothLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
		bluetoothGranted = it
		// Con el permiso concedido se activan los efectos del DualSense (igual que el interruptor de Ajustes).
		if(it)
			prefs.dualsenseFeatures = true
	}
	val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { micGranted = it }

	BackHandler(enabled = step > 0) { step-- }

	Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp))
	{
		Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically)
		{
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically)
			{
				repeat(STEP_COUNT) { i ->
					Box(
						Modifier.size(if(i == step) 24.dp else 8.dp, 8.dp).background(
							if(i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
							CircleShape))
				}
			}
			TextButton(onClick = { onFinish(false) }, modifier = Modifier.heightIn(min = 48.dp)) {
				Text(stringResource(R.string.onboarding_skip))
			}
		}

		Column(
			Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.Center
		) {
			when(step)
			{
				0 -> StepContent(
					R.drawable.ic_gamepad, R.string.onboarding_welcome_title, R.string.onboarding_welcome_body)
				1 -> StepContent(
					R.drawable.ic_psn_id, R.string.onboarding_psn_title, R.string.onboarding_psn_body)
				2 -> StepContent(
					R.drawable.ic_rumble, R.string.onboarding_permissions_title, R.string.onboarding_permissions_body)
				else -> StepContent(
					R.drawable.ic_register, R.string.onboarding_register_title, R.string.onboarding_register_body)
			}

			when(step)
			{
				1 -> if(psnSignedIn)
					Text(
						stringResource(R.string.onboarding_psn_done),
						color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge,
						modifier = Modifier.padding(top = 16.dp))
				else
					OutlinedButton(onClick = onPsnLogin, modifier = Modifier.padding(top = 16.dp).heightIn(min = 48.dp)) {
						Text(stringResource(R.string.psn_sign_in))
					}
				2 -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp))
				{
					PermissionButton(
						R.string.onboarding_permission_bluetooth, bluetoothGranted,
						onRequest = { bluetoothLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) })
					PermissionButton(
						R.string.onboarding_permission_microphone, micGranted,
						onRequest = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) })
				}
			}
		}

		if(step < STEP_COUNT - 1)
			Button(
				onClick = { step++ },
				modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
			) { Text(stringResource(R.string.onboarding_next)) }
		else
		{
			Button(
				onClick = { onFinish(true) },
				modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
			) { Text(stringResource(R.string.action_register)) }
			TextButton(
				onClick = { onFinish(false) },
				modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
			) { Text(stringResource(R.string.onboarding_later)) }
		}
	}
}

@Composable
private fun StepContent(@DrawableRes icon: Int, @StringRes title: Int, @StringRes body: Int)
{
	Box(
		Modifier.size(96.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
		contentAlignment = Alignment.Center
	) {
		Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(48.dp))
	}
	Text(
		stringResource(title),
		style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
		modifier = Modifier.padding(top = 24.dp))
	Text(
		stringResource(body),
		style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
		textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun PermissionButton(@StringRes label: Int, granted: Boolean, onRequest: () -> Unit)
{
	if(granted)
		Text(
			stringResource(R.string.onboarding_permission_granted, stringResource(label)),
			color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge,
			modifier = Modifier.heightIn(min = 48.dp).padding(top = 12.dp))
	else
		OutlinedButton(onClick = onRequest, modifier = Modifier.heightIn(min = 48.dp)) {
			Text(stringResource(R.string.onboarding_permission_allow, stringResource(label)))
		}
}

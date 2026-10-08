// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.ui.regist

import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hdrdualcontroller.app.R
import com.hdrdualcontroller.app.lib.RegistInfo
import com.hdrdualcontroller.app.lib.Target
import com.hdrdualcontroller.app.psn.PsnAccountStore
import com.hdrdualcontroller.app.regist.RegistViewModel
import com.hdrdualcontroller.app.regist.RegistViewModel.ConsoleVersion
import com.hdrdualcontroller.app.ui.AppScaffold

private const val PIN_LENGTH = 8

@Composable
fun RegistScreen(
	initialHost: String?,
	initialBroadcast: Boolean,
	assignManualHostId: Long?,
	flow: RegistFlowViewModel,
	onBack: () -> Unit,
	onPsnLogin: () -> Unit,
	onStart: () -> Unit
)
{
	val context = LocalContext.current
	val viewModel: RegistViewModel = viewModel()
	val version by viewModel.ps4Version.observeAsState(ConsoleVersion.PS5)
	val psnStore = remember { PsnAccountStore(context) }

	var host by rememberSaveable { mutableStateOf(initialHost ?: "255.255.255.255") }
	var broadcast by rememberSaveable { mutableStateOf(initialBroadcast) }
	// El Account ID y el PIN no usan rememberSaveable: no deben terminar en el estado guardado de la actividad.
	var psnId by remember { mutableStateOf("") }
	var pin by remember { mutableStateOf("") }
	var showErrors by remember { mutableStateOf(false) }

	val usesOnlineId = version == ConsoleVersion.PS4_LT_7

	// Al volver del login de PSN se rellena el Account ID guardado.
	LifecycleResumeEffect(version) {
		if(psnId.isBlank() && !usesOnlineId)
			psnStore.accountId?.let { psnId = it }
		onPauseOrDispose { }
	}

	val hostValid = host.trim().isNotEmpty()
	val psnOnlineId: String? = if(usesOnlineId) psnId.trim() else null
	val psnAccountId: ByteArray? =
		if(!usesOnlineId) try { Base64.decode(psnId.trim(), Base64.DEFAULT) } catch(_: IllegalArgumentException) { null } else null
	val psnIdValid = if(usesOnlineId) !psnOnlineId.isNullOrEmpty() else psnAccountId != null && psnAccountId.size == RegistInfo.ACCOUNT_ID_SIZE
	val pinValid = pin.length == PIN_LENGTH

	fun register()
	{
		showErrors = true
		if(!hostValid || !psnIdValid || !pinValid)
			return
		val target = when(version)
		{
			ConsoleVersion.PS5 -> Target.PS5_1
			ConsoleVersion.PS4_GE_8 -> Target.PS4_10
			ConsoleVersion.PS4_GE_7 -> Target.PS4_9
			ConsoleVersion.PS4_LT_7 -> Target.PS4_8
		}
		flow.info = RegistInfo(target, host.trim(), broadcast, psnOnlineId, psnAccountId, pin.toInt())
		flow.assignManualHostId = assignManualHostId
		onStart()
	}

	AppScaffold(title = stringResource(R.string.title_regist), onBack = onBack) { padding ->
		Column(
			Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
			verticalArrangement = Arrangement.spacedBy(12.dp)
		) {
			OutlinedTextField(
				value = host,
				onValueChange = { host = it },
				label = { Text(stringResource(R.string.hint_regist_host)) },
				isError = showErrors && !hostValid,
				supportingText = { if(showErrors && !hostValid) Text(stringResource(R.string.entered_host_invalid)) },
				singleLine = true,
				modifier = Modifier.fillMaxWidth())

			Row(
				Modifier.fillMaxWidth().clickable { broadcast = !broadcast },
				verticalAlignment = Alignment.CenterVertically
			) {
				Checkbox(checked = broadcast, onCheckedChange = null)
				Text(stringResource(R.string.regist_broadcast), Modifier.padding(start = 12.dp))
			}

			listOf(
				ConsoleVersion.PS5 to R.string.regist_option_ps5,
				ConsoleVersion.PS4_GE_8 to R.string.regist_option_ps4_ge_8,
				ConsoleVersion.PS4_GE_7 to R.string.regist_option_ps4_ge_7,
				ConsoleVersion.PS4_LT_7 to R.string.regist_option_ps4_lt_7
			).forEach { (option, label) ->
				Row(
					Modifier.fillMaxWidth().selectable(selected = version == option, onClick = {
						// El Online ID (PS4 < 7) y el Account ID son datos distintos: solo se borra al cambiar de tipo.
						if((option == ConsoleVersion.PS4_LT_7) != usesOnlineId)
							psnId = ""
						viewModel.ps4Version.value = option
					}),
					verticalAlignment = Alignment.CenterVertically
				) {
					RadioButton(selected = version == option, onClick = null)
					Text(stringResource(label), Modifier.padding(start = 12.dp))
				}
			}

			if(!usesOnlineId)
			{
				OutlinedButton(
					onClick = {
						val saved = psnStore.accountId
						if(saved != null) psnId = saved else onPsnLogin()
					},
					modifier = Modifier.fillMaxWidth()
				) { Text(stringResource(R.string.regist_get_account_id_from_psn)) }

				Text(
					stringResource(R.string.regist_psn_account_id_help),
					style = MaterialTheme.typography.bodySmall,
					textAlign = TextAlign.Center,
					modifier = Modifier.fillMaxWidth())
				Text(
					stringResource(R.string.regist_psn_account_id_help_url),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.primary,
					textDecoration = TextDecoration.Underline,
					textAlign = TextAlign.Center,
					modifier = Modifier.fillMaxWidth().clickable {
						context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.regist_psn_account_id_help_url))))
					})
			}

			OutlinedTextField(
				value = psnId,
				onValueChange = { psnId = it },
				label = { Text(stringResource(if(usesOnlineId) R.string.hint_regist_psn_online_id else R.string.hint_regist_psn_account_id)) },
				isError = showErrors && !psnIdValid,
				supportingText = {
					if(showErrors && !psnIdValid)
						Text(stringResource(if(usesOnlineId) R.string.regist_psn_online_id_invalid else R.string.regist_psn_account_id_invalid))
				},
				singleLine = true,
				modifier = Modifier.fillMaxWidth())

			Text(
				stringResource(if(version.isPS5) R.string.regist_pin_instructions_ps5_before else R.string.regist_pin_instructions_ps4_before),
				textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
			Text(
				stringResource(if(version.isPS5) R.string.regist_pin_instructions_ps5_navigation else R.string.regist_pin_instructions_ps4_navigation),
				style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
			Text(
				stringResource(R.string.regist_pin_instructions_after),
				textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

			OutlinedTextField(
				value = pin,
				onValueChange = { new -> pin = new.filter { it.isDigit() }.take(PIN_LENGTH) },
				label = { Text(stringResource(R.string.hint_regist_pin)) },
				isError = showErrors && !pinValid,
				supportingText = { if(showErrors && !pinValid) Text(stringResource(R.string.regist_pin_invalid, PIN_LENGTH)) },
				singleLine = true,
				keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
				modifier = Modifier.fillMaxWidth())

			Button(onClick = ::register, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_regist)) }
		}
	}
}

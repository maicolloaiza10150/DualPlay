// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.ui.regist

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hdrdualcontroller.app.R
import com.hdrdualcontroller.app.common.MacAddress
import com.hdrdualcontroller.app.common.ext.viewModelFactory
import com.hdrdualcontroller.app.common.getDatabase
import com.hdrdualcontroller.app.regist.RegistExecuteViewModel
import com.hdrdualcontroller.app.regist.RegistExecuteViewModel.State
import com.hdrdualcontroller.app.ui.AppScaffold

@Composable
fun RegistExecuteScreen(flow: RegistFlowViewModel, onBack: () -> Unit, onFinished: () -> Unit)
{
	val context = LocalContext.current
	val viewModel: RegistExecuteViewModel = viewModel(factory = viewModelFactory { RegistExecuteViewModel(getDatabase(context)) })
	val state by viewModel.state.observeAsState(State.IDLE)
	val logText by viewModel.logText.observeAsState("")
	var duplicateDismissed by remember { mutableStateOf(false) }
	val scroll = rememberScrollState()

	LaunchedEffect(Unit) {
		val info = flow.info
		if(info != null)
		{
			viewModel.start(info, flow.assignManualHostId)
			// PIN y Account ID ya no hacen falta fuera del registro nativo.
			flow.info = null
			flow.assignManualHostId = null
		}
		else if(viewModel.state.value == State.IDLE)
			onBack()
	}

	// Igual que la versión anterior: salir de la pantalla (app en segundo plano) cancela el registro.
	LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.stop() }

	LaunchedEffect(logText) { scroll.scrollTo(scroll.maxValue) }

	AppScaffold(title = stringResource(R.string.title_regist), onBack = onBack) { padding ->
		Column(
			Modifier.padding(padding).padding(16.dp).fillMaxSize(),
			verticalArrangement = Arrangement.spacedBy(12.dp)
		) {
			if(state == State.RUNNING)
				LinearProgressIndicator(Modifier.fillMaxWidth())

			Text(
				logText,
				fontFamily = FontFamily.Monospace,
				fontSize = 11.sp,
				lineHeight = 14.sp,
				modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll))

			when(state)
			{
				State.FAILED -> Text(stringResource(R.string.regist_info_failed), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
				State.SUCCESSFUL, State.SUCCESSFUL_DUPLICATE -> Text(stringResource(R.string.regist_info_success), style = MaterialTheme.typography.titleMedium)
				else -> {}
			}

			OutlinedButton(
				onClick = {
					val send = Intent(Intent.ACTION_SEND).also {
						it.type = "text/plain"
						it.putExtra(Intent.EXTRA_TEXT, logText)
					}
					context.startActivity(Intent.createChooser(send, context.getString(R.string.action_share_log)))
				},
				modifier = Modifier.fillMaxWidth()
			) { Text(stringResource(R.string.action_share_log)) }

			if(state == State.SUCCESSFUL)
				Button(onClick = onFinished, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_done)) }
		}
	}

	if(state == State.SUCCESSFUL_DUPLICATE && !duplicateDismissed)
	{
		val mac = viewModel.host?.serverMac?.let { MacAddress(it).toString() } ?: ""
		AlertDialog(
			onDismissRequest = { duplicateDismissed = true },
			text = { Text(stringResource(R.string.alert_regist_duplicate, mac)) },
			confirmButton = {
				TextButton(onClick = { duplicateDismissed = true; viewModel.saveHost() }) { Text(stringResource(R.string.action_regist_overwrite)) }
			},
			dismissButton = { TextButton(onClick = { duplicateDismissed = true }) { Text(stringResource(R.string.action_regist_discard)) } })
	}
}

// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.ui.manual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hdrdualcontroller.app.R
import com.hdrdualcontroller.app.common.ManualHost
import com.hdrdualcontroller.app.common.RegisteredHost
import com.hdrdualcontroller.app.common.ext.viewModelFactory
import com.hdrdualcontroller.app.common.getDatabase
import com.hdrdualcontroller.app.manualconsole.EditManualConsoleViewModel
import com.hdrdualcontroller.app.ui.AppScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditManualScreen(manualHostId: Long?, onBack: () -> Unit)
{
	val context = LocalContext.current
	val viewModel: EditManualConsoleViewModel = viewModel(factory = viewModelFactory { EditManualConsoleViewModel(getDatabase(context), manualHostId) })

	val scope = rememberCoroutineScope()
	val existing by (viewModel.existingHost?.observeAsState() ?: remember { mutableStateOf<ManualHost?>(null) })
	val registeredHosts by viewModel.registeredHosts.observeAsState(listOf(null))
	val selected by viewModel.selectedRegisteredHost.observeAsState(null)

	var host by remember { mutableStateOf("") }
	var hostError by remember { mutableStateOf(false) }
	var expanded by remember { mutableStateOf(false) }

	LaunchedEffect(existing) { existing?.let { host = it.host } }

	fun title(registeredHost: RegisteredHost?) =
		if(registeredHost == null)
			context.getString(R.string.add_manual_regist_on_connect)
		else
			"${registeredHost.serverNickname ?: ""} (${registeredHost.serverMac})"

	AppScaffold(title = stringResource(R.string.title_edit_manual), onBack = onBack) { padding ->
		Column(
			Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
			verticalArrangement = Arrangement.spacedBy(16.dp)
		) {
			OutlinedTextField(
				value = host,
				onValueChange = { host = it; hostError = false },
				label = { Text(stringResource(R.string.hint_regist_host)) },
				isError = hostError,
				supportingText = { if(hostError) Text(stringResource(R.string.entered_host_invalid)) },
				singleLine = true,
				modifier = Modifier.fillMaxWidth())

			ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
				OutlinedTextField(
					value = title(selected),
					onValueChange = {},
					readOnly = true,
					label = { Text(stringResource(R.string.hint_add_manual_regist_host)) },
					trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
					modifier = Modifier.menuAnchor().fillMaxWidth())
				ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
					registeredHosts.forEach { registeredHost ->
						DropdownMenuItem(
							text = { Text(title(registeredHost)) },
							onClick = {
								viewModel.selectedRegisteredHost.value = registeredHost
								expanded = false
							})
					}
				}
			}

			Button(
				onClick = {
					val trimmed = host.trim()
					if(trimmed.isEmpty())
						hostError = true
					else
						scope.launch {
							viewModel.saveHost(trimmed).join()
							onBack()
						}
				},
				modifier = Modifier.fillMaxWidth()
			) { Text(stringResource(R.string.action_add_manual_save)) }
		}
	}
}

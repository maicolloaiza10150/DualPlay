// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hdrdualcontroller.app.R
import com.hdrdualcontroller.app.common.RegisteredHost
import com.hdrdualcontroller.app.common.ext.viewModelFactory
import com.hdrdualcontroller.app.common.getDatabase
import com.hdrdualcontroller.app.settings.SettingsRegisteredHostsViewModel
import com.hdrdualcontroller.app.ui.AppScaffold
import com.hdrdualcontroller.app.ui.DrawableIcon

@Composable
fun RegisteredHostsScreen(onBack: () -> Unit, onRegister: () -> Unit)
{
	val context = LocalContext.current
	val viewModel: SettingsRegisteredHostsViewModel = viewModel(factory = viewModelFactory { SettingsRegisteredHostsViewModel(getDatabase(context)) })
	val hosts by viewModel.registeredHosts.observeAsState(emptyList())
	var hostToDelete by remember { mutableStateOf<RegisteredHost?>(null) }

	AppScaffold(
		title = stringResource(R.string.preferences_registered_hosts_title),
		onBack = onBack,
		floatingActionButton = {
			FloatingActionButton(onClick = onRegister) { DrawableIcon(R.drawable.ic_add, stringResource(R.string.action_register)) }
		}
	) { padding ->
		Box(Modifier.fillMaxSize().padding(padding))
		{
			if(hosts.isEmpty())
				Text(
					stringResource(R.string.registered_hosts_empty_info),
					textAlign = TextAlign.Center,
					modifier = Modifier.align(Alignment.Center).padding(32.dp))
			else
				LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
					items(hosts, key = { it.id }) { host ->
						ListItem(
							headlineContent = { Text(host.serverNickname ?: "") },
							supportingContent = { Text(host.serverMac.toString()) },
							leadingContent = { DrawableIcon(R.drawable.ic_console_simple, null) },
							trailingContent = {
								IconButton(onClick = { hostToDelete = host }) {
									DrawableIcon(R.drawable.ic_delete_row, stringResource(R.string.action_delete))
								}
							})
					}
				}
		}
	}

	hostToDelete?.let { host ->
		AlertDialog(
			onDismissRequest = { hostToDelete = null },
			text = { Text(stringResource(R.string.alert_message_delete_registered_host, host.serverNickname ?: "", host.serverMac.toString())) },
			confirmButton = {
				TextButton(onClick = { hostToDelete = null; viewModel.deleteHost(host) }) { Text(stringResource(R.string.action_delete)) }
			},
			dismissButton = { TextButton(onClick = { hostToDelete = null }) { Text(stringResource(R.string.action_keep)) } })
	}
}

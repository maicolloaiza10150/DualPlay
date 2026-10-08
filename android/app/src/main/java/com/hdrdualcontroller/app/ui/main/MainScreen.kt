// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.ui.main

import android.content.Intent
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hdrdualcontroller.app.R
import com.hdrdualcontroller.app.common.DiscoveredDisplayHost
import com.hdrdualcontroller.app.common.DisplayHost
import com.hdrdualcontroller.app.common.ManualDisplayHost
import com.hdrdualcontroller.app.common.Preferences
import com.hdrdualcontroller.app.common.ext.viewModelFactory
import com.hdrdualcontroller.app.common.getDatabase
import com.hdrdualcontroller.app.lib.ConnectInfo
import com.hdrdualcontroller.app.lib.DiscoveryHost
import com.hdrdualcontroller.app.main.MainViewModel
import com.hdrdualcontroller.app.stream.StreamActivity
import com.hdrdualcontroller.app.ui.AppScaffold
import com.hdrdualcontroller.app.ui.DrawableIcon

private val condensedMedium = FontFamily(Typeface.create("sans-serif-condensed-medium", Typeface.NORMAL))
private val condensedLight = FontFamily(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL))

@Composable
fun MainScreen(
	onOpenSettings: () -> Unit,
	onAddManual: () -> Unit,
	onRegister: () -> Unit,
	onRegisterHost: (host: String, assignManualHostId: Long?) -> Unit,
	onEditManual: (manualHostId: Long) -> Unit
)
{
	val context = LocalContext.current
	val viewModel: MainViewModel = viewModel(factory = viewModelFactory { MainViewModel(getDatabase(context), Preferences(context)) })

	// Igual que antes: el descubrimiento solo corre mientras la pantalla está visible.
	LifecycleStartEffect(viewModel) {
		viewModel.discoveryManager.resume()
		onStopOrDispose { viewModel.discoveryManager.pause() }
	}

	val hosts by viewModel.displayHosts.observeAsState(emptyList())
	val discoveryActive by viewModel.discoveryActive.observeAsState(false)

	var fabExpanded by rememberSaveable { mutableStateOf(false) }
	var standbyHost by remember { mutableStateOf<DisplayHost?>(null) }
	var hostToDelete by remember { mutableStateOf<ManualDisplayHost?>(null) }

	BackHandler(enabled = fabExpanded) { fabExpanded = false }

	fun connect(host: DisplayHost)
	{
		val registeredHost = host.registeredHost ?: return
		val connectInfo = ConnectInfo(host.isPS5, host.host, registeredHost.rpRegistKey, registeredHost.rpKey, Preferences(context).videoProfile)
		context.startActivity(Intent(context, StreamActivity::class.java).putExtra(StreamActivity.EXTRA_CONNECT_INFO, connectInfo))
	}

	fun wakeup(host: DisplayHost)
	{
		val registeredHost = host.registeredHost ?: return
		viewModel.discoveryManager.sendWakeup(host.host, registeredHost.rpRegistKey, registeredHost.target.isPS5)
	}

	fun hostTriggered(host: DisplayHost)
	{
		if(host.registeredHost == null)
		{
			onRegisterHost(host.host, (host as? ManualDisplayHost)?.manualHost?.id)
			return
		}
		if(host is DiscoveredDisplayHost && host.discoveredHost.state == DiscoveryHost.State.STANDBY)
			standbyHost = host
		else
			connect(host)
	}

	AppScaffold(
		title = stringResource(R.string.app_name),
		actions = {
			IconButton(onClick = { viewModel.discoveryManager.active = !discoveryActive }) {
				DrawableIcon(
					if(discoveryActive) R.drawable.ic_discover_on else R.drawable.ic_discover_off,
					stringResource(R.string.action_discover))
			}
			IconButton(onClick = onOpenSettings) {
				DrawableIcon(R.drawable.ic_settings, stringResource(R.string.action_settings))
			}
		},
		floatingActionButton = {
			SpeedDial(
				expanded = fabExpanded,
				onToggle = { fabExpanded = !fabExpanded },
				onAddManual = { fabExpanded = false; onAddManual() },
				onRegister = { fabExpanded = false; onRegister() })
		}
	) { padding ->
		Box(Modifier.fillMaxSize().padding(padding))
		{
			if(hosts.isEmpty())
				EmptyInfo(discoveryActive)
			else
				LazyColumn(
					contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 120.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp)
				) {
					itemsIndexed(hosts) { _, host ->
						HostCard(
							host = host,
							onClick = { hostTriggered(host) },
							onWakeup = { wakeup(host) },
							onEdit = { (host as? ManualDisplayHost)?.let { onEditManual(it.manualHost.id) } },
							onDelete = { hostToDelete = host as? ManualDisplayHost })
					}
				}

			if(fabExpanded)
				Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { fabExpanded = false })
		}
	}

	standbyHost?.let { host ->
		AlertDialog(
			onDismissRequest = { standbyHost = null },
			text = { Text(stringResource(R.string.alert_message_standby_wakeup)) },
			confirmButton = {
				Row {
					TextButton(onClick = { standbyHost = null; wakeup(host) }) { Text(stringResource(R.string.action_wakeup)) }
					TextButton(onClick = { standbyHost = null; connect(host) }) { Text(stringResource(R.string.action_connect_immediately)) }
				}
			},
			dismissButton = {
				TextButton(onClick = { standbyHost = null }) { Text(stringResource(R.string.action_connect_cancel_connect)) }
			})
	}

	hostToDelete?.let { host ->
		AlertDialog(
			onDismissRequest = { hostToDelete = null },
			text = { Text(stringResource(R.string.alert_message_delete_manual_host, host.manualHost.host)) },
			confirmButton = {
				TextButton(onClick = { hostToDelete = null; viewModel.deleteManualHost(host.manualHost) }) {
					Text(stringResource(R.string.action_delete))
				}
			},
			dismissButton = {
				TextButton(onClick = { hostToDelete = null }) { Text(stringResource(R.string.action_keep)) }
			})
	}
}

@Composable
private fun SpeedDial(expanded: Boolean, onToggle: () -> Unit, onAddManual: () -> Unit, onRegister: () -> Unit)
{
	Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp))
	{
		if(expanded)
		{
			ExtendedFloatingActionButton(
				onClick = onAddManual,
				icon = { DrawableIcon(R.drawable.ic_add_manual, null) },
				text = { Text(stringResource(R.string.action_add_manual)) })
			ExtendedFloatingActionButton(
				onClick = onRegister,
				icon = { DrawableIcon(R.drawable.ic_register, null) },
				text = { Text(stringResource(R.string.action_register)) })
		}
		FloatingActionButton(onClick = onToggle) {
			DrawableIcon(if(expanded) R.drawable.ic_close else R.drawable.ic_add, null)
		}
	}
}

@Composable
private fun EmptyInfo(discoveryActive: Boolean)
{
	Column(
		Modifier.fillMaxSize().padding(32.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.Center
	) {
		Icon(
			painterResource(if(discoveryActive) R.drawable.ic_discover_on else R.drawable.ic_discover_off),
			contentDescription = null,
			modifier = Modifier.size(96.dp).alpha(0.6f))
		Text(
			stringResource(if(discoveryActive) R.string.display_hosts_empty_discovery_on_info else R.string.display_hosts_empty_discovery_off_info),
			textAlign = TextAlign.Center,
			modifier = Modifier.padding(top = 16.dp))
	}
}

@Composable
private fun HostCard(host: DisplayHost, onClick: () -> Unit, onWakeup: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit)
{
	val canWakeup = host.registeredHost != null
	val canEditDelete = host is ManualDisplayHost
	var menuOpen by remember { mutableStateOf(false) }

	val image = when
	{
		host is DiscoveredDisplayHost -> when(host.discoveredHost.state)
		{
			DiscoveryHost.State.STANDBY -> if(host.isPS5) R.drawable.ic_console_ps5_standby else R.drawable.ic_console_standby
			DiscoveryHost.State.READY -> if(host.isPS5) R.drawable.ic_console_ps5_ready else R.drawable.ic_console_ready
			else -> if(host.isPS5) R.drawable.ic_console_ps5 else R.drawable.ic_console
		}
		host.isPS5 -> R.drawable.ic_console_ps5
		else -> R.drawable.ic_console
	}

	val id = host.id
	val idText = if(id == null) "" else stringResource(
		if(host.isRegistered) R.string.display_host_id_registered else R.string.display_host_id_unregistered, id)
	val bottomText = (host as? DiscoveredDisplayHost)?.discoveredHost?.let { discovered ->
		if(discovered.runningAppName != null || discovered.runningAppTitleid != null)
			stringResource(R.string.display_host_app_title_id, discovered.runningAppName ?: "", discovered.runningAppTitleid ?: "")
		else
			""
	} ?: ""

	Card(Modifier.fillMaxWidth().height(250.dp).clickable(onClick = onClick))
	{
		Box(Modifier.fillMaxSize())
		{
			if(host is DiscoveredDisplayHost)
				Box(Modifier.align(Alignment.TopStart))
				{
					Image(painterResource(R.drawable.ic_triangle), null, Modifier.size(72.dp))
					Image(painterResource(R.drawable.ic_discovered_on_secondary), null, Modifier.padding(8.dp))
				}

			Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally)
			{
				Text(
					host.name ?: "",
					fontFamily = condensedMedium, fontSize = 32.sp, textAlign = TextAlign.Center,
					maxLines = 1, overflow = TextOverflow.Ellipsis)
				Text(
					stringResource(R.string.display_host_host, host.host),
					fontFamily = condensedMedium, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
				Text(idText, fontFamily = condensedMedium, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
				Image(
					painterResource(image), null,
					Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp, vertical = 4.dp),
					contentScale = ContentScale.Fit)
				Text(
					bottomText,
					fontFamily = condensedLight, fontSize = 16.sp, textAlign = TextAlign.Center,
					maxLines = 2, overflow = TextOverflow.Ellipsis,
					color = MaterialTheme.colorScheme.onSurface)
			}

			if(canWakeup || canEditDelete)
				Box(Modifier.align(Alignment.BottomEnd))
				{
					IconButton(onClick = { menuOpen = true }) {
						DrawableIcon(R.drawable.ic_overflow, stringResource(R.string.action_more))
					}
					DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
						if(canWakeup)
							DropdownMenuItem(
								text = { Text(stringResource(R.string.action_wakeup)) },
								onClick = { menuOpen = false; onWakeup() })
						if(canEditDelete)
						{
							DropdownMenuItem(
								text = { Text(stringResource(R.string.action_edit)) },
								onClick = { menuOpen = false; onEdit() })
							DropdownMenuItem(
								text = { Text(stringResource(R.string.action_delete)) },
								onClick = { menuOpen = false; onDelete() })
						}
					}
				}
		}
	}
}

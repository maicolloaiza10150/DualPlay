// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.ui.main

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.hdrdualcontroller.app.stream.HdrSupport
import com.hdrdualcontroller.app.stream.StreamActivity
import com.hdrdualcontroller.app.ui.AppScaffold
import com.hdrdualcontroller.app.ui.DrawableIcon
import com.hdrdualcontroller.app.ui.theme.DualPlayColors

/** Estado visible de una consola en la tarjeta. */
private enum class ConsoleStatus(val label: Int, val color: Color)
{
	READY(R.string.status_ready, DualPlayColors.Ready),
	STANDBY(R.string.status_standby, DualPlayColors.Standby),
	UNKNOWN(R.string.status_unreachable, DualPlayColors.Off),
	MANUAL(R.string.status_manual, DualPlayColors.Off)
}

private fun DisplayHost.status() = when
{
	this is DiscoveredDisplayHost -> when(discoveredHost.state)
	{
		DiscoveryHost.State.READY -> ConsoleStatus.READY
		DiscoveryHost.State.STANDBY -> ConsoleStatus.STANDBY
		else -> ConsoleStatus.UNKNOWN
	}
	else -> ConsoleStatus.MANUAL
}

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

	// El descubrimiento solo corre mientras la pantalla está visible.
	LifecycleStartEffect(viewModel) {
		viewModel.discoveryManager.resume()
		onStopOrDispose { viewModel.discoveryManager.pause() }
	}

	val hosts by viewModel.displayHosts.observeAsState(emptyList())
	val discoveryActive by viewModel.discoveryActive.observeAsState(false)

	var fabExpanded by rememberSaveable { mutableStateOf(false) }
	var hostToDelete by remember { mutableStateOf<ManualDisplayHost?>(null) }

	BackHandler(enabled = fabExpanded) { fabExpanded = false }

	fun connect(host: DisplayHost)
	{
		val registeredHost = host.registeredHost ?: return
		val requested = Preferences(context).videoProfile
		// DUALPLAY: si se pidió HDR y el teléfono no puede, se baja a HEVC SDR y se avisa.
		val profile = HdrSupport.adjust(context, requested)
		if(profile.codec != requested.codec)
			Toast.makeText(context, R.string.stream_hdr_unavailable, Toast.LENGTH_LONG).show()
		val connectInfo = ConnectInfo(host.isPS5, host.host, registeredHost.rpRegistKey, registeredHost.rpKey, profile, Preferences(context).dualsenseFeatures,
			Preferences(context).dualsenseFeatures && Preferences(context).hdHaptics)
		context.startActivity(Intent(context, StreamActivity::class.java)
			.putExtra(StreamActivity.EXTRA_CONNECT_INFO, connectInfo)
			// DUALPLAY: nombre visible en la pantalla de conexión.
			.putExtra(StreamActivity.EXTRA_CONSOLE_NAME, host.name))
	}

	fun wakeup(host: DisplayHost)
	{
		val registeredHost = host.registeredHost ?: return
		viewModel.discoveryManager.sendWakeup(host.host, registeredHost.rpRegistKey, registeredHost.target.isPS5)
		Toast.makeText(context, R.string.main_wake_sent, Toast.LENGTH_SHORT).show()
	}

	fun register(host: DisplayHost)
		= onRegisterHost(host.host, (host as? ManualDisplayHost)?.manualHost?.id)

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
			if(hosts.isNotEmpty())
				AddConsoleMenu(
					expanded = fabExpanded,
					onToggle = { fabExpanded = !fabExpanded },
					onAddManual = { fabExpanded = false; onAddManual() },
					onRegister = { fabExpanded = false; onRegister() })
		}
	) { padding ->
		Box(Modifier.fillMaxSize().padding(padding))
		{
			if(hosts.isEmpty())
				EmptyState(discoveryActive, onRegister = onRegister, onAddManual = onAddManual)
			else
				LazyColumn(
					contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
					verticalArrangement = Arrangement.spacedBy(12.dp)
				) {
					items(hosts) { host ->
						HostCard(
							host = host,
							onPlay = { connect(host) },
							onWakeup = { wakeup(host) },
							onRegister = { register(host) },
							onEdit = { (host as? ManualDisplayHost)?.let { onEditManual(it.manualHost.id) } },
							onDelete = { hostToDelete = host as? ManualDisplayHost })
					}
				}

			if(fabExpanded)
				Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable { fabExpanded = false })
		}
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
private fun AddConsoleMenu(expanded: Boolean, onToggle: () -> Unit, onAddManual: () -> Unit, onRegister: () -> Unit)
{
	Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp))
	{
		if(expanded)
		{
			ExtendedFloatingActionButton(
				onClick = onAddManual,
				containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
				contentColor = MaterialTheme.colorScheme.onSurface,
				icon = { DrawableIcon(R.drawable.ic_add_manual, null) },
				text = { Text(stringResource(R.string.action_add_manual)) })
			ExtendedFloatingActionButton(
				onClick = onRegister,
				containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
				contentColor = MaterialTheme.colorScheme.onSurface,
				icon = { DrawableIcon(R.drawable.ic_register, null) },
				text = { Text(stringResource(R.string.action_register)) })
		}
		ExtendedFloatingActionButton(
			onClick = onToggle,
			containerColor = MaterialTheme.colorScheme.primary,
			contentColor = MaterialTheme.colorScheme.onPrimary,
			icon = { DrawableIcon(if(expanded) R.drawable.ic_close else R.drawable.ic_add, null) },
			text = { Text(stringResource(R.string.main_add_console)) })
	}
}

@Composable
private fun EmptyState(discoveryActive: Boolean, onRegister: () -> Unit, onAddManual: () -> Unit)
{
	Column(
		Modifier.fillMaxSize().padding(horizontal = 32.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.Center
	) {
		Box(
			Modifier.size(96.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
			contentAlignment = Alignment.Center
		) {
			Icon(
				androidx.compose.ui.res.painterResource(R.drawable.ic_gamepad), contentDescription = null,
				tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(48.dp))
		}
		Text(
			stringResource(R.string.main_empty_title),
			style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
			modifier = Modifier.padding(top = 24.dp))
		Text(
			stringResource(R.string.main_empty_body),
			style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
			textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
		Text(
			stringResource(if(discoveryActive) R.string.main_empty_searching else R.string.main_empty_discovery_off),
			style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
			textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
		Button(onClick = onRegister, modifier = Modifier.padding(top = 24.dp).heightIn(min = 48.dp)) {
			Text(stringResource(R.string.action_register))
		}
		OutlinedButton(onClick = onAddManual, modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp)) {
			Text(stringResource(R.string.action_add_manual))
		}
	}
}

@Composable
private fun HostCard(
	host: DisplayHost,
	onPlay: () -> Unit,
	onWakeup: () -> Unit,
	onRegister: () -> Unit,
	onEdit: () -> Unit,
	onDelete: () -> Unit
)
{
	val registered = host.isRegistered
	val canEditDelete = host is ManualDisplayHost
	val status = host.status()
	var menuOpen by remember { mutableStateOf(false) }

	val title = host.name ?: host.host
	val appName = (host as? DiscoveredDisplayHost)?.discoveredHost?.runningAppName

	Card(
		Modifier.fillMaxWidth(),
		shape = RoundedCornerShape(24.dp),
		colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
	) {
		Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 16.dp))
		{
			Row(verticalAlignment = Alignment.CenterVertically)
			{
				Box(
					Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
					contentAlignment = Alignment.Center
				) {
					Icon(
						androidx.compose.ui.res.painterResource(R.drawable.ic_gamepad), contentDescription = null,
						tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
				}
				Column(Modifier.weight(1f).padding(start = 16.dp))
				{
					Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
					Text(
						stringResource(R.string.main_console_subtitle, stringResource(if(host.isPS5) R.string.console_ps5 else R.string.console_ps4), host.host),
						style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
						maxLines = 1, overflow = TextOverflow.Ellipsis)
				}
				Box {
					IconButton(onClick = { menuOpen = true }) {
						DrawableIcon(R.drawable.ic_overflow, stringResource(R.string.action_more))
					}
					DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
						if(registered)
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

			Row(
				Modifier.padding(top = 12.dp, end = 12.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(8.dp)
			) {
				StatusChip(status)
				if(!registered)
					Text(
						stringResource(R.string.status_unregistered),
						style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
			}

			if(appName != null)
				Text(
					stringResource(R.string.main_playing_now, appName),
					style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1, overflow = TextOverflow.Ellipsis,
					modifier = Modifier.padding(top = 8.dp, end = 12.dp))

			Row(
				Modifier.fillMaxWidth().padding(top = 16.dp, end = 12.dp),
				horizontalArrangement = Arrangement.spacedBy(8.dp)
			) {
				if(!registered)
					Button(onClick = onRegister, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
						Text(stringResource(R.string.action_register))
					}
				else
				{
					if(status == ConsoleStatus.STANDBY)
						FilledTonalButton(onClick = onWakeup, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
							Text(stringResource(R.string.action_wakeup))
						}
					Button(onClick = onPlay, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
						Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
						Text(stringResource(R.string.main_play), modifier = Modifier.padding(start = 4.dp))
					}
				}
			}
		}
	}
}

@Composable
private fun StatusChip(status: ConsoleStatus)
{
	Row(
		Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Box(Modifier.size(8.dp).background(status.color, CircleShape))
		Text(
			stringResource(status.label),
			style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface,
			modifier = Modifier.padding(start = 6.dp))
	}
}

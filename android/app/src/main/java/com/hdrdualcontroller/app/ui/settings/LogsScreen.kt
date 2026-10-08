// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.ui.settings

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hdrdualcontroller.app.R
import com.hdrdualcontroller.app.common.LogFile
import com.hdrdualcontroller.app.common.LogManager
import com.hdrdualcontroller.app.common.LogRedactor
import com.hdrdualcontroller.app.common.ext.viewModelFactory
import com.hdrdualcontroller.app.common.fileProviderAuthority
import com.hdrdualcontroller.app.settings.SettingsLogsViewModel
import com.hdrdualcontroller.app.ui.AppScaffold
import com.hdrdualcontroller.app.ui.DrawableIcon
import java.io.File
import java.io.IOException
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun LogsScreen(onBack: () -> Unit)
{
	val context = LocalContext.current
	val viewModel: SettingsLogsViewModel = viewModel(factory = viewModelFactory { SettingsLogsViewModel(LogManager(context)) })
	val logs by viewModel.sessionLogs.observeAsState(emptyList())
	val dateFormat = DateFormat.getDateInstance(DateFormat.SHORT)
	val timeFormat = SimpleDateFormat("HH:mm:ss:SSS", Locale.getDefault())

	AppScaffold(title = stringResource(R.string.preferences_logs_title), onBack = onBack) { padding ->
		Box(Modifier.fillMaxSize().padding(padding))
		{
			if(logs.isEmpty())
				Text(
					stringResource(R.string.session_logs_empty_info),
					textAlign = TextAlign.Center,
					modifier = Modifier.align(Alignment.Center).padding(32.dp))
			else
				LazyColumn {
					items(logs, key = { it.filename }) { logFile ->
						ListItem(
							headlineContent = { Text("${dateFormat.format(logFile.date)} ${timeFormat.format(logFile.date)}") },
							supportingContent = { Text(logFile.filename) },
							trailingContent = {
								Row {
									IconButton(onClick = { shareLogFile(context, logFile) }) {
										DrawableIcon(R.drawable.ic_share, stringResource(R.string.action_share_log))
									}
									IconButton(onClick = { viewModel.deleteLog(logFile) }) {
										DrawableIcon(R.drawable.ic_delete_row, stringResource(R.string.action_delete))
									}
								}
							})
					}
				}
		}
	}
}

private fun shareLogFile(context: Context, file: LogFile)
{
	// DUALPLAY: se comparte una copia redactada (sin Session ID, claves ni PIN), nunca el original.
	val redacted = try
	{
		LogRedactor.redactFile(file.file, File(File(context.cacheDir, "export_settings"), "redacted_${file.filename}"))
	}
	catch(e: IOException)
	{
		return
	}
	val uri = FileProvider.getUriForFile(context, fileProviderAuthority, redacted)
	val send = Intent(Intent.ACTION_SEND).also {
		it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		it.type = "text/plain"
		it.putExtra(Intent.EXTRA_STREAM, uri)
		it.clipData = ClipData.newRawUri("", uri)
	}
	context.startActivity(Intent.createChooser(send, context.getString(R.string.action_share_log)))
}

// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.settings

import android.content.ClipData
import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.content.FileProvider
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hdrdualcontroller.app.R
import com.hdrdualcontroller.app.common.LogFile
import com.hdrdualcontroller.app.common.LogManager
import com.hdrdualcontroller.app.common.LogRedactor
import java.io.File
import com.hdrdualcontroller.app.common.ext.viewModelFactory
import com.hdrdualcontroller.app.common.fileProviderAuthority
import com.hdrdualcontroller.app.databinding.FragmentSettingsLogsBinding

class SettingsLogsFragment: AppCompatDialogFragment(), TitleFragment
{
	private lateinit var viewModel: SettingsLogsViewModel

	private var _binding: FragmentSettingsLogsBinding? = null
	private val binding get() = _binding!!

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
		FragmentSettingsLogsBinding.inflate(inflater, container, false).let {
			_binding = it
			it.root
		}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?)
	{
		val context = requireContext()

		viewModel = ViewModelProvider(this, viewModelFactory { SettingsLogsViewModel(LogManager(context)) })
			.get(SettingsLogsViewModel::class.java)

		val adapter = SettingsLogsAdapter()
		binding.logsRecyclerView.layoutManager = LinearLayoutManager(context)
		binding.logsRecyclerView.adapter = adapter
		adapter.shareCallback = this::shareLogFile
		viewModel.sessionLogs.observe(viewLifecycleOwner, Observer {
			adapter.logFiles = it
			binding.emptyInfoGroup.visibility = if(it.isEmpty()) View.VISIBLE else View.GONE
		})

		val itemTouchSwipeCallback = object : ItemTouchSwipeCallback(context)
		{
			override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int)
			{
				val pos = viewHolder.adapterPosition
				val file = viewModel.sessionLogs.value?.getOrNull(pos) ?: return
				viewModel.deleteLog(file)
			}
		}
		ItemTouchHelper(itemTouchSwipeCallback).attachToRecyclerView(binding.logsRecyclerView)
	}

	override fun getTitle(resources: Resources): String = resources.getString(R.string.preferences_logs_title)

	private fun shareLogFile(file: LogFile)
	{
		val activity = activity ?: return
		// DUALPLAY: se comparte una copia redactada (sin Session ID, claves ni PIN), nunca el original.
		val redacted = try
		{
			LogRedactor.redactFile(file.file, File(File(activity.cacheDir, "export_settings"), "redacted_${file.filename}"))
		}
		catch(e: java.io.IOException)
		{
			return
		}
		val uri = FileProvider.getUriForFile(activity, fileProviderAuthority, redacted)
		Intent(Intent.ACTION_SEND).also {
			it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
			it.type = "text/plain"
			it.putExtra(Intent.EXTRA_STREAM, uri)
			it.clipData = ClipData.newRawUri("", uri)
			startActivity(Intent.createChooser(it, resources.getString(R.string.action_share_log)))
		}

	}
}
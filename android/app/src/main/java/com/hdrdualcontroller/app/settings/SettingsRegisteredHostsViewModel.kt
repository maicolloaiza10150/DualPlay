// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.hdrdualcontroller.app.common.AppDatabase
import com.hdrdualcontroller.app.common.RegisteredHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsRegisteredHostsViewModel(val database: AppDatabase): ViewModel()
{
	val registeredHosts by lazy {
		database.registeredHostDao().getAll().asLiveData()
	}

	fun deleteHost(host: RegisteredHost)
	{
		viewModelScope.launch(Dispatchers.IO) {
			try {
				database.registeredHostDao().delete(host)
			} catch(_: Exception) {}
		}
	}
}

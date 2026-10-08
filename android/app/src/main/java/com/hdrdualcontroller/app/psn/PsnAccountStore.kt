// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.psn

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Account ID de PSN cifrado con Android Keystore (regla de CLAUDE.md: nada de secretos en claro).
 * Si el archivo no se puede descifrar (p. ej. restaurado desde un backup en otro dispositivo) se descarta.
 */
class PsnAccountStore(context: Context)
{
	private val appContext = context.applicationContext
	private val prefs: SharedPreferences by lazy { open() }

	private fun open(): SharedPreferences
	{
		fun create() = EncryptedSharedPreferences.create(
			appContext,
			FILE_NAME,
			MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
			EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
			EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
		return try { create() }
		catch(_: Exception)
		{
			appContext.deleteSharedPreferences(FILE_NAME)
			create()
		}
	}

	val accountId: String? get() = prefs.getString(KEY_ACCOUNT_ID, null)?.takeIf { it.isNotEmpty() }
	val onlineId: String? get() = prefs.getString(KEY_ONLINE_ID, null)?.takeIf { it.isNotEmpty() }

	fun save(account: PsnAccount)
	{
		prefs.edit().putString(KEY_ACCOUNT_ID, account.accountId).putString(KEY_ONLINE_ID, account.onlineId).apply()
	}

	fun clear()
	{
		prefs.edit().clear().apply()
	}

	private companion object
	{
		const val FILE_NAME = "psn_account"
		const val KEY_ACCOUNT_ID = "account_id"
		const val KEY_ONLINE_ID = "online_id"
	}
}

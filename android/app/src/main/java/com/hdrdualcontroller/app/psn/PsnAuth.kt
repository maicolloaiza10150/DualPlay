// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.psn

import android.net.Uri
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

data class PsnAccount(val accountId: String, val onlineId: String?)

/**
 * Login de PSN para obtener el Account ID que pide el registro.
 *
 * Portado de gui/src/psnaccountid.cpp y gui/src/psnaccountid.h (chiaki-ng de escritorio): mismo client id,
 * redirect y endpoint de token. Es un endpoint no oficial de Sony y puede cambiar sin aviso.
 *
 * DUALPLAY: usa solo el scope `psn:clientapp` (suficiente para leer `user_id`; el escritorio pide más scopes
 * porque también conecta por Internet). No se guarda ningún token: solo el Account ID resultante.
 */
object PsnAuth
{
	// Credenciales públicas del cliente "Remote Play" de Windows, idénticas a las de chiaki-ng de escritorio.
	private const val CLIENT_ID = "ba495a24-818c-472b-b12d-ff231c1b5745"
	private const val CLIENT_SECRET = "mvaiZkRsAsI1IBkY"
	private const val TOKEN_URL = "https://auth.api.sonyentertainmentnetwork.com/2.0/oauth/token"
	const val REDIRECT_PAGE = "https://remoteplay.dl.playstation.net/remoteplay/redirect"
	private const val SCOPE = "psn:clientapp"

	val loginUrl: String get() =
		"https://auth.api.sonyentertainmentnetwork.com/2.0/oauth/authorize" +
			"?service_entity=urn:service-entity:psn&response_type=code&client_id=$CLIENT_ID" +
			"&redirect_uri=$REDIRECT_PAGE&scope=$SCOPE&request_locale=en_US&ui=pr&service_logo=ps" +
			"&layout_type=popup&smcid=remoteplay&prompt=always&PlatformPrivacyWs1=minimal&"

	fun isRedirect(url: String?) = url != null && url.startsWith(REDIRECT_PAGE)

	/** Extrae el parámetro `code` de la URL de redirect; null si no hay. */
	fun extractCode(url: String): String? =
		try { Uri.parse(url).getQueryParameter("code")?.takeIf { it.isNotEmpty() } }
		catch(_: Exception) { null }

	/** `user_id` (decimal) -> Account ID: 8 bytes little-endian en base64, como en psnaccountid.cpp. */
	fun accountIdFromUserId(userId: String): String
	{
		val bytes = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(userId.toLong()).array()
		return Base64.getEncoder().encodeToString(bytes)
	}

	/** Bloqueante: llamar desde Dispatchers.IO. */
	fun fetchAccount(code: String): Result<PsnAccount> = runCatching {
		val basic = "Basic " + Base64.getEncoder().encodeToString("$CLIENT_ID:$CLIENT_SECRET".toByteArray())

		val body = "grant_type=authorization_code&code=${URLEncoder.encode(code, "UTF-8")}" +
			"&redirect_uri=$REDIRECT_PAGE&"
		val token = JSONObject(request("POST", TOKEN_URL, basic, body)).getString("access_token")

		val info = JSONObject(request("GET", "$TOKEN_URL/${Uri.encode(token)}", basic, null))
		PsnAccount(accountIdFromUserId(info.getString("user_id")), info.optString("online_id").takeIf { it.isNotEmpty() })
	}

	private fun request(method: String, url: String, authorization: String, body: String?): String
	{
		val connection = URL(url).openConnection() as HttpURLConnection
		try
		{
			connection.requestMethod = method
			connection.connectTimeout = 15_000
			connection.readTimeout = 15_000
			connection.setRequestProperty("Authorization", authorization)
			if(body != null)
			{
				connection.doOutput = true
				connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
				connection.outputStream.use { it.write(body.toByteArray(Charsets.US_ASCII)) }
			}
			val code = connection.responseCode
			if(code != 200)
				// No se incluye el cuerpo de la respuesta: puede contener datos de la cuenta.
				throw IOException("PSN respondió HTTP $code")
			return connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
		}
		finally
		{
			connection.disconnect()
		}
	}
}

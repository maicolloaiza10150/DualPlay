// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.ui.psn

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.hdrdualcontroller.app.R
import com.hdrdualcontroller.app.psn.PsnAccountStore
import com.hdrdualcontroller.app.psn.PsnAuth
import com.hdrdualcontroller.app.ui.AppScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface LoginState
{
	data object WebLogin: LoginState
	data object GettingAccount: LoginState
	data class Failed(val message: String): LoginState
	/** Plan B: abrir el navegador y pegar la URL de redirect (si Sony rechaza el WebView). */
	data object PasteUrl: LoginState
}

@Composable
fun PsnLoginScreen(onBack: () -> Unit, onLoggedIn: () -> Unit)
{
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val store = remember { PsnAccountStore(context) }
	var state by remember { mutableStateOf<LoginState>(LoginState.WebLogin) }
	var attempt by remember { mutableStateOf(0) }
	var pasted by remember { mutableStateOf("") }
	var pasteError by remember { mutableStateOf(false) }

	fun exchange(code: String)
	{
		state = LoginState.GettingAccount
		scope.launch {
			val result = withContext(Dispatchers.IO) { PsnAuth.fetchAccount(code) }
			// La cookie de sesión de Sony no debe quedar en el WebView.
			CookieManager.getInstance().removeAllCookies(null)
			result.onSuccess {
				store.save(it)
				onLoggedIn()
			}.onFailure {
				state = LoginState.Failed(it.message ?: it.javaClass.simpleName)
			}
		}
	}

	AppScaffold(title = stringResource(R.string.psn_login_title), onBack = onBack) { padding ->
		Box(Modifier.padding(padding).fillMaxSize())
		{
			when(val current = state)
			{
				LoginState.WebLogin ->
				{
					Column(Modifier.fillMaxSize())
					{
						PsnWebView(
							key = attempt,
							onRedirect = { url ->
								val code = PsnAuth.extractCode(url)
								if(code != null) exchange(code)
								else state = LoginState.Failed(context.getString(R.string.psn_login_paste_invalid))
							},
							modifier = Modifier.weight(1f).fillMaxWidth())
						OutlinedButton(
							onClick = { state = LoginState.PasteUrl },
							modifier = Modifier.fillMaxWidth().padding(8.dp)
						) { Text(stringResource(R.string.psn_login_use_browser)) }
					}
				}

				LoginState.GettingAccount ->
					Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally)
					{
						CircularProgressIndicator()
						Text(stringResource(R.string.psn_login_getting_account), Modifier.padding(top = 16.dp))
					}

				is LoginState.Failed ->
					Column(
						Modifier.align(Alignment.Center).padding(24.dp),
						horizontalAlignment = Alignment.CenterHorizontally,
						verticalArrangement = Arrangement.spacedBy(16.dp)
					) {
						Text(
							stringResource(R.string.psn_login_error, current.message),
							color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
						Button(onClick = { attempt++; state = LoginState.WebLogin }) { Text(stringResource(R.string.psn_login_retry)) }
						OutlinedButton(onClick = { state = LoginState.PasteUrl }) { Text(stringResource(R.string.psn_login_use_browser)) }
					}

				LoginState.PasteUrl ->
					Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp))
					{
						Text(stringResource(R.string.psn_login_paste_info))
						OutlinedButton(
							onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PsnAuth.loginUrl))) },
							modifier = Modifier.fillMaxWidth()
						) { Text(stringResource(R.string.psn_login_open_browser)) }
						OutlinedTextField(
							value = pasted,
							onValueChange = { pasted = it; pasteError = false },
							label = { Text(stringResource(R.string.psn_login_paste_hint)) },
							isError = pasteError,
							supportingText = { if(pasteError) Text(stringResource(R.string.psn_login_paste_invalid)) },
							modifier = Modifier.fillMaxWidth())
						Button(
							onClick = {
								val code = if(PsnAuth.isRedirect(pasted.trim())) PsnAuth.extractCode(pasted.trim()) else null
								if(code == null) pasteError = true else exchange(code)
							},
							modifier = Modifier.fillMaxWidth()
						) { Text(stringResource(R.string.psn_login_paste_submit)) }
						OutlinedButton(
							onClick = { attempt++; state = LoginState.WebLogin },
							modifier = Modifier.fillMaxWidth()
						) { Text(stringResource(R.string.psn_login_retry)) }
					}
			}
		}
	}
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PsnWebView(key: Int, onRedirect: (String) -> Unit, modifier: Modifier)
{
	AndroidView(
		modifier = modifier,
		factory = { context ->
			WebView(context).apply {
				settings.javaScriptEnabled = true
				settings.domStorageEnabled = true
				// Sony rechaza algunos user agents de WebView ("; wv"): se presenta como Chrome móvil normal.
				settings.userAgentString = settings.userAgentString.replace("; wv", "")
				var handled = false
				webViewClient = object: WebViewClient()
				{
					override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean
					{
						val url = request.url.toString()
						if(!PsnAuth.isRedirect(url))
							return false
						if(!handled) { handled = true; onRedirect(url) }
						return true
					}

					// Algunas redirecciones llegan por JS/meta y no pasan por shouldOverrideUrlLoading.
					override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?)
					{
						if(url != null && PsnAuth.isRedirect(url) && !handled)
						{
							handled = true
							view.stopLoading()
							onRedirect(url)
						}
					}
				}
				loadUrl(PsnAuth.loginUrl)
			}
		},
		onRelease = { it.destroy() },
		// `key` fuerza una WebView nueva al reintentar.
		update = { if(key < 0) it.reload() })
}

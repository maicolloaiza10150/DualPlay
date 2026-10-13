// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.hdrdualcontroller.app.ui.AppNavHost
import com.hdrdualcontroller.app.ui.theme.DualPlayTheme

class MainActivity: ComponentActivity()
{
	override fun onCreate(savedInstanceState: Bundle?)
	{
		enableEdgeToEdge()
		super.onCreate(savedInstanceState)
		setContent {
			DualPlayTheme {
				// Surface fija el color de texto y el fondo para todas las pantallas (también la guía de primer arranque).
				Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
					AppNavHost()
				}
			}
		}
	}
}

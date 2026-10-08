// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hdrdualcontroller.app.ui.AppNavHost
import com.hdrdualcontroller.app.ui.theme.HdrDualControllerTheme

class MainActivity: ComponentActivity()
{
	override fun onCreate(savedInstanceState: Bundle?)
	{
		enableEdgeToEdge()
		super.onCreate(savedInstanceState)
		setContent {
			HdrDualControllerTheme {
				AppNavHost()
			}
		}
	}
}

// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Paleta heredada de la UI Views (res/values/colors.xml): azul marino + acento rosa.
private val Navy = Color(0xFF162D50)
private val NavyDark = Color(0xFF000128)
private val Accent = Color(0xFFFFAAEE)

private val DarkColors = darkColorScheme(
	primary = Accent,
	onPrimary = NavyDark,
	primaryContainer = Navy,
	onPrimaryContainer = Color.White,
	secondary = Accent,
	onSecondary = NavyDark,
	background = Color(0xFF111111),
	onBackground = Color(0xFFEDEDED),
	surface = Color(0xFF111111),
	onSurface = Color(0xFFEDEDED),
	surfaceVariant = Color(0xFF444444),
	onSurfaceVariant = Color(0xFFD0D0D0),
	surfaceContainer = Color(0xFF1B1B1B),
	surfaceContainerHigh = Color(0xFF242424),
	error = Color(0xFFFF6B6B),
)

private val LightColors = lightColorScheme(
	primary = Navy,
	onPrimary = Color.White,
	primaryContainer = Accent,
	onPrimaryContainer = NavyDark,
	secondary = Navy,
	onSecondary = Color.White,
)

@Composable
fun HdrDualControllerTheme(darkTheme: Boolean = true, content: @Composable () -> Unit)
{
	// Siempre oscuro: los drawables vectoriales heredados resuelven ?attr/colorOnSurface contra el tema XML
	// (Theme.MaterialComponents oscuro). Un tema claro de Compose dejaría iconos ilegibles.
	MaterialTheme(
		colorScheme = if(darkTheme) DarkColors else LightColors,
		content = content
	)
}

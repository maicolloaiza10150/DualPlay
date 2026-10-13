// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// DUALPLAY: paleta de DualPlay. Azul PlayStation sobre fondo oscuro azulado; todos los colores salen de aquí.
// Contraste mínimo 4,5:1 (WCAG AA) en texto: azul primario sobre fondo ≈ 8:1, onSurface sobre surface ≈ 15:1,
// onSurfaceVariant sobre surfaceContainer ≈ 9:1, onPrimary sobre primario ≈ 8:1.
object DualPlayColors
{
	val Blue = Color(0xFF5CAEFF)
	val BlueContainer = Color(0xFF0B4F94)
	val Background = Color(0xFF0A0E17)
	val SurfaceLow = Color(0xFF0F1521)
	val Surface = Color(0xFF131B2A)
	val SurfaceHigh = Color(0xFF1B2538)
	val SurfaceHighest = Color(0xFF243049)
	val Outline = Color(0xFF6B7C96)
	val OutlineVariant = Color(0xFF2B3850)
	val OnSurface = Color(0xFFE8EEF8)
	val OnSurfaceVariant = Color(0xFFB4C2D6)
	val Error = Color(0xFFFF8A8A)
	val Ready = Color(0xFF4ADE80)
	val Standby = Color(0xFFFFC857)
	val Off = Color(0xFF8A97AB)
}

private val DarkColors = darkColorScheme(
	primary = DualPlayColors.Blue,
	onPrimary = Color(0xFF00213F),
	primaryContainer = DualPlayColors.BlueContainer,
	onPrimaryContainer = Color(0xFFD6E8FF),
	secondary = Color(0xFF9DB7D6),
	onSecondary = Color(0xFF0A1B30),
	secondaryContainer = DualPlayColors.SurfaceHighest,
	onSecondaryContainer = DualPlayColors.OnSurface,
	tertiary = Color(0xFF7DD3FC),
	onTertiary = Color(0xFF00212E),
	background = DualPlayColors.Background,
	onBackground = DualPlayColors.OnSurface,
	surface = DualPlayColors.Background,
	onSurface = DualPlayColors.OnSurface,
	surfaceVariant = DualPlayColors.SurfaceHighest,
	onSurfaceVariant = DualPlayColors.OnSurfaceVariant,
	surfaceContainerLowest = Color(0xFF070A11),
	surfaceContainerLow = DualPlayColors.SurfaceLow,
	surfaceContainer = DualPlayColors.Surface,
	surfaceContainerHigh = DualPlayColors.SurfaceHigh,
	surfaceContainerHighest = DualPlayColors.SurfaceHighest,
	outline = DualPlayColors.Outline,
	outlineVariant = DualPlayColors.OutlineVariant,
	error = DualPlayColors.Error,
	onError = Color(0xFF3D0000),
)

/** Jerarquía tipográfica: fuente del sistema (sin descargas ni licencias extra), con pesos y tamaños ajustados. */
private val DualPlayTypography = Typography().let { base ->
	base.copy(
		headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
		headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
		titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
		titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
		titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
		labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
		bodyMedium = base.bodyMedium.copy(lineHeight = 21.sp),
	)
}

/** Colores de estado de la consola (lista / en reposo / apagada o sin respuesta). */
@Immutable
data class StatusColors(val ready: Color, val standby: Color, val off: Color)

val LocalStatusColors = staticCompositionLocalOf { StatusColors(DualPlayColors.Ready, DualPlayColors.Standby, DualPlayColors.Off) }

@Composable
fun DualPlayTheme(content: @Composable () -> Unit)
{
	// Siempre oscuro: los drawables vectoriales heredados resuelven ?attr/colorOnSurface contra el tema XML oscuro.
	MaterialTheme(
		colorScheme = DarkColors,
		typography = DualPlayTypography,
		content = content
	)
}

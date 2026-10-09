// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.input

import android.view.KeyEvent
import com.hdrdualcontroller.app.lib.ControllerState

/**
 * Botones del mando de PlayStation a los que se puede asignar una tecla del control físico.
 * `analogTrigger`: 1 = L2, 2 = R2 (se envían como gatillo analógico a fondo); en los demás vale 0 y se usa `mask`.
 */
enum class PsButton(val mask: UInt, val analogTrigger: Int = 0)
{
	CROSS(ControllerState.BUTTON_CROSS),
	MOON(ControllerState.BUTTON_MOON),
	BOX(ControllerState.BUTTON_BOX),
	PYRAMID(ControllerState.BUTTON_PYRAMID),
	L1(ControllerState.BUTTON_L1),
	R1(ControllerState.BUTTON_R1),
	L2(0U, 1),
	R2(0U, 2),
	L3(ControllerState.BUTTON_L3),
	R3(ControllerState.BUTTON_R3),
	SHARE(ControllerState.BUTTON_SHARE),
	OPTIONS(ControllerState.BUTTON_OPTIONS),
	PS(ControllerState.BUTTON_PS),
	TOUCHPAD(ControllerState.BUTTON_TOUCHPAD),
	DPAD_UP(ControllerState.BUTTON_DPAD_UP),
	DPAD_DOWN(ControllerState.BUTTON_DPAD_DOWN),
	DPAD_LEFT(ControllerState.BUTTON_DPAD_LEFT),
	DPAD_RIGHT(ControllerState.BUTTON_DPAD_RIGHT)
}

/**
 * Tabla tecla de Android -> botón de PlayStation: los valores de fábrica (los de siempre, con el intercambio de
 * cruz/círculo opcional) y las asignaciones del usuario, que mandan sobre los de fábrica. Las teclas sin asignar
 * (p. ej. las paletas traseras de un control Flydigi o los botones extra de un 8BitDo, que Android entrega como
 * KEYCODE_BUTTON_* sueltos) se pueden asignar desde la pantalla de remapeo.
 */
class ButtonMapping(private val overrides: Map<Int, PsButton> = emptyMap(), private val swapCrossMoon: Boolean = false)
{
	/** El botón de PS que activa esta tecla, o null si no hace nada. */
	fun resolve(keyCode: Int): PsButton? =
		overrides[keyCode] ?: DEFAULTS[keyCode]?.let { if(swapCrossMoon) swap(it) else it }

	/** Códigos de tecla que activan `button` ahora mismo (valores de fábrica no reasignados + asignaciones del usuario). */
	fun keysFor(button: PsButton): List<Int> =
		(DEFAULTS.keys + overrides.keys).distinct().filter { resolve(it) == button }.sorted()

	/** Copia con `keyCode` asignado a `button`. */
	fun assign(keyCode: Int, button: PsButton) = ButtonMapping(overrides + (keyCode to button), swapCrossMoon)

	fun serialize(): String = overrides.entries.sortedBy { it.key }.joinToString(";") { "${it.key}=${it.value.name}" }

	companion object
	{
		// Teclas de fábrica: las mismas que mapeaba StreamInput (upstream). El D-pad llega por MotionEvent (hat), no aquí.
		val DEFAULTS: Map<Int, PsButton> = mapOf(
			KeyEvent.KEYCODE_BUTTON_A to PsButton.CROSS,
			KeyEvent.KEYCODE_BUTTON_B to PsButton.MOON,
			KeyEvent.KEYCODE_BUTTON_X to PsButton.BOX,
			KeyEvent.KEYCODE_BUTTON_Y to PsButton.PYRAMID,
			KeyEvent.KEYCODE_BUTTON_L1 to PsButton.L1,
			KeyEvent.KEYCODE_BUTTON_R1 to PsButton.R1,
			KeyEvent.KEYCODE_BUTTON_L2 to PsButton.L2,
			KeyEvent.KEYCODE_BUTTON_R2 to PsButton.R2,
			KeyEvent.KEYCODE_BUTTON_THUMBL to PsButton.L3,
			KeyEvent.KEYCODE_BUTTON_THUMBR to PsButton.R3,
			KeyEvent.KEYCODE_BUTTON_SELECT to PsButton.SHARE,
			KeyEvent.KEYCODE_BUTTON_START to PsButton.OPTIONS,
			KeyEvent.KEYCODE_BUTTON_C to PsButton.PS,
			KeyEvent.KEYCODE_BUTTON_MODE to PsButton.PS)

		private fun swap(button: PsButton) = when(button)
		{
			PsButton.CROSS -> PsButton.MOON
			PsButton.MOON -> PsButton.CROSS
			PsButton.BOX -> PsButton.PYRAMID
			PsButton.PYRAMID -> PsButton.BOX
			else -> button
		}

		/** Formato guardado: `codigo=BOTON;codigo=BOTON`. Las entradas inválidas se ignoran. */
		fun parse(text: String?, swapCrossMoon: Boolean = false): ButtonMapping
		{
			val overrides = (text ?: "").split(';').mapNotNull {
				val parts = it.split('=')
				val key = parts.getOrNull(0)?.trim()?.toIntOrNull()
				val button = parts.getOrNull(1)?.trim()?.let { name -> PsButton.entries.firstOrNull { b -> b.name == name } }
				if(key != null && button != null) key to button else null
			}.toMap()
			return ButtonMapping(overrides, swapCrossMoon)
		}
	}
}

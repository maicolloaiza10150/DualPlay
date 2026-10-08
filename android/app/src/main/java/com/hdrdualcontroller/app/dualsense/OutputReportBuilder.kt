// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.dualsense

/**
 * Efecto de gatillo adaptativo: 1 byte de modo + 10 de parámetros, tal como llega de la PS5 en
 * `ChiakiTriggerEffectsEvent` (lib/include/chiaki/session.h: type_left/type_right + left[10]/right[10]).
 */
class TriggerEffect(val mode: Int, val params: ByteArray = ByteArray(PARAM_SIZE))
{
	init
	{
		require(params.size == PARAM_SIZE) { "Un efecto lleva $PARAM_SIZE parámetros, no ${params.size}" }
	}

	companion object
	{
		const val PARAM_SIZE = 10

		fun of(mode: Int, vararg params: Int) = TriggerEffect(mode, ByteArray(PARAM_SIZE).also {
			params.forEachIndexed { i, v -> it[i] = v.toByte() }
		})

		// Efectos de ejemplo de SDL, test/testcontroller.c (CyclePS5TriggerEffect).
		/** Modo 0x05: apaga el efecto. */
		val CLEAR = of(0x05)
		/** Modo 0x01: resistencia constante en todo el recorrido. */
		val CONSTANT_RESISTANCE = of(0x01, 0, 110)
		/** Modo 0x06: resistencia y vibración al pulsar. */
		val PULSE = of(0x06, 15, 63, 128)
	}
}

/**
 * Construye el output report USB del DualSense (report id 0x02, 63 bytes).
 *
 * Fuentes (verificadas el 2026-10-08):
 *  - Linux `drivers/hid/hid-playstation.c`: DS_OUTPUT_REPORT_USB = 0x02, DS_OUTPUT_REPORT_USB_SIZE = 63,
 *    `struct dualsense_output_report_usb` = report_id + `dualsense_output_report_common` (47 bytes) + 15 reservados.
 *  - SDL `src/joystick/hidapi/SDL_hidapi_ps5.c`, `DS5EffectsState_t`: mismo bloque común de 47 bytes, con el
 *    efecto del gatillo derecho en el offset 10 y el izquierdo en el 21 (11 bytes cada uno, relativos al bloque común),
 *    y `ucEnableBits1 |= 0x04` (derecho) / `0x08` (izquierdo) para aplicarlos. SDL manda el reporte USB como
 *    `data[0] = 0x02` seguido de la estructura (offset 1).
 *  - chiaki-ng escritorio `gui/src/controllermanager.cpp`, `Controller::SetTriggerEffects`: copia `type` en el
 *    primer byte y los 10 parámetros a continuación, igual que aquí. (Su comentario "0x04 left / 0x08 right" está
 *    invertido respecto a SDL; como siempre se activan ambos bits no afecta.)
 *
 * No se rellenan volumen de audio, LEDs ni el byte de intensidad: solo gatillos.
 */
object OutputReportBuilder
{
	const val REPORT_ID_USB = 0x02
	const val USB_REPORT_SIZE = 63
	const val COMMON_SIZE = 47

	// Offsets dentro del bloque común (SDL DS5EffectsState_t / hid-playstation dualsense_output_report_common).
	const val OFFSET_ENABLE_BITS_1 = 0
	const val OFFSET_RIGHT_TRIGGER = 10
	const val OFFSET_LEFT_TRIGGER = 21
	const val TRIGGER_EFFECT_SIZE = 11

	const val ENABLE_RIGHT_TRIGGER = 0x04
	const val ENABLE_LEFT_TRIGGER = 0x08

	/** Un `null` deja el gatillo como está (su bit de validez no se activa). */
	fun triggerEffects(left: TriggerEffect?, right: TriggerEffect?): ByteArray
	{
		val report = ByteArray(USB_REPORT_SIZE)
		report[0] = REPORT_ID_USB.toByte()
		var enable = 0
		if(right != null)
		{
			enable = enable or ENABLE_RIGHT_TRIGGER
			put(report, OFFSET_RIGHT_TRIGGER, right)
		}
		if(left != null)
		{
			enable = enable or ENABLE_LEFT_TRIGGER
			put(report, OFFSET_LEFT_TRIGGER, left)
		}
		report[1 + OFFSET_ENABLE_BITS_1] = enable.toByte()
		return report
	}

	private fun put(report: ByteArray, commonOffset: Int, effect: TriggerEffect)
	{
		val start = 1 + commonOffset // +1 por el report id
		report[start] = effect.mode.toByte()
		effect.params.copyInto(report, start + 1)
	}
}

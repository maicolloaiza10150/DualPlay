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
 * Bluetooth (report id 0x31, 78 bytes): `struct dualsense_output_report_bt` de hid-playstation = report_id, seq_tag
 * (secuencia en el nibble alto), tag 0x10, el MISMO bloque común de 47 bytes (desde el offset 3), 24 reservados y
 * CRC32 little-endian al final. El CRC es el CRC-32 estándar (zlib) calculado sobre el byte semilla 0xA2 (cabecera
 * HIDP DATA|OUTPUT, PS_OUTPUT_CRC32_SEED) seguido del reporte sin los 4 bytes del CRC (ver `ps_..._crc32` en
 * hid-playstation.c y SDL: `ubHdr = 0xA2`). Por el canal interrupt L2CAP se envía 0xA2 + reporte.
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
	const val OFFSET_ENABLE_BITS_2 = 1
	const val ENABLE_HAPTIC_LOW_PASS = 0x20
	const val ENABLE_MOTOR_POWER = 0x40
	const val OFFSET_MOTOR_RIGHT = 2
	const val OFFSET_MOTOR_LEFT = 3
	const val OFFSET_ENABLE_BITS_3 = 38 // hid-playstation: valid_flag2
	const val OFFSET_RIGHT_TRIGGER = 10
	const val OFFSET_LEFT_TRIGGER = 21
	const val TRIGGER_EFFECT_SIZE = 11

	const val ENABLE_RIGHT_TRIGGER = 0x04
	const val ENABLE_LEFT_TRIGGER = 0x08
	const val ENABLE_HAPTICS_SELECT = 0x02
	const val ENABLE_COMPATIBLE_VIBRATION_2 = 0x04

	const val REPORT_ID_BT = 0x31
	const val BT_REPORT_SIZE = 78
	const val BT_TAG = 0x10
	const val BT_CRC_SEED = 0xA2
	const val REPORT_ID_HAPTICS = 0x39
	const val HAPTICS_REPORT_SIZE = 547
	const val REPORT_ID_HAPTICS_SINGLE = 0x32
	const val HAPTICS_SINGLE_REPORT_SIZE = 142
	const val HAPTICS_SINGLE_BLOCK_BYTES = 64
	const val HAPTICS_BLOCK_BYTES = 128
	const val HAPTICS_BUFFER_LENGTH = 48 // valor por defecto de DS5Dongle (rango 16..127)
	const val HAPTICS_SAMPLE_RATE = 3000

	/** Un `null` deja el gatillo como está (su bit de validez no se activa). */
	fun triggerEffects(left: TriggerEffect?, right: TriggerEffect?): ByteArray
	{
		val report = ByteArray(USB_REPORT_SIZE)
		report[0] = REPORT_ID_USB.toByte()
		fillTriggers(report, 1, left, right)
		return report
	}

	/** Versión Bluetooth: 78 bytes con tag y CRC. `sequence` es el contador de 4 bits de hid-playstation. */
	fun triggerEffectsBluetooth(left: TriggerEffect?, right: TriggerEffect?, sequence: Int = 0): ByteArray =
		outputBluetooth(left, right, rumble = null, sequence = sequence)

	/**
	 * Reporte Bluetooth con gatillos y/o rumble. `rumble` (0..255) activa el rumble emulado "compatible" de los DualSense
	 * con firmware >= 0x0224 (todos los actuales): SDL/chiaki-ng `SetDualSenseRumble` pone `ucEnableBits1 |= 0x02`
	 * (HAPTICS_SELECT, rumble en vez de háptica de audio) y `ucEnableBits3 |= 0x04` (COMPATIBLE_VIBRATION2), con los
	 * motores en los offsets 2 (derecho) y 3 (izquierdo); hid-playstation hace lo mismo con `use_vibration_v2`.
	 * Los DualSense con firmware anterior usarían `ucEnableBits1 |= 0x01` y la mitad de fuerza; no se soportan.
	 */
	fun outputBluetooth(left: TriggerEffect?, right: TriggerEffect?, rumble: Int?, sequence: Int = 0): ByteArray
	{
		val report = ByteArray(BT_REPORT_SIZE)
		report[0] = REPORT_ID_BT.toByte()
		report[1] = ((sequence and 0x0f) shl 4).toByte()
		report[2] = BT_TAG.toByte()
		fillTriggers(report, 3, left, right)
		if(rumble != null)
		{
			val strength = rumble.coerceIn(0, 255).toByte()
			report[3 + OFFSET_ENABLE_BITS_1] = (report[3 + OFFSET_ENABLE_BITS_1].toInt() or ENABLE_HAPTICS_SELECT).toByte()
			report[3 + OFFSET_MOTOR_RIGHT] = strength
			report[3 + OFFSET_MOTOR_LEFT] = strength
			report[3 + OFFSET_ENABLE_BITS_3] = ENABLE_COMPATIBLE_VIBRATION_2.toByte()
		}
		val crc = java.util.zip.CRC32()
		crc.update(BT_CRC_SEED)
		crc.update(report, 0, BT_REPORT_SIZE - 4)
		val value = crc.value
		for(i in 0 until 4)
			report[BT_REPORT_SIZE - 4 + i] = ((value shr (8 * i)) and 0xff).toByte()
		return report
	}

	/**
	 * Reporte Bluetooth 0x39 (547 bytes) con 2 bloques de háptica HD: 2 x 64 bytes = 2 x 32 muestras estéreo int8 a 3000 Hz
	 * (~21,3 ms de audio por reporte, uno cada ~21,3 ms). Formato reverse-engineered, tomado del firmware DS5Dongle
	 * (github.com/awalol/DS5Dongle, MIT, src/audio.cpp `audio_bt_task`): [0]=0x39, [1]=seq<<4, paquete 0x11 (cabecera 0x91,
	 * longitud 6, flags 0x7E, 4 bytes de AudioBufferLength, contador de paquetes que sube de 2 en 2), paquete 0x12
	 * (cabecera 0xD2, longitud 64, 128 bytes de muestras), resto a cero (sin altavoz ni micrófono) y CRC32 con semilla 0xA2.
	 * Por 0x39 el control usa el audio como háptica en lugar del rumble: no mezclar con `outputBluetooth(rumble = ...)`.
	 */
	fun hapticsBluetooth(block: ByteArray, sequence: Int, packetCounter: Int, bufferLength: Int = HAPTICS_BUFFER_LENGTH): ByteArray
	{
		require(block.size == HAPTICS_BLOCK_BYTES) { "El bloque de háptica son $HAPTICS_BLOCK_BYTES bytes (2 x 64)" }
		val report = ByteArray(HAPTICS_REPORT_SIZE)
		report[0] = REPORT_ID_HAPTICS.toByte()
		report[1] = ((sequence and 0x0f) shl 4).toByte()
		report[2] = 0x91.toByte() // paquete 0x11 | bit 7
		report[3] = 6
		report[4] = 0x7E
		for(i in 5..8) report[i] = bufferLength.toByte()
		report[9] = packetCounter.toByte()
		report[10] = 0xD2.toByte() // paquete 0x12 | bits 6 y 7
		report[11] = 64
		block.copyInto(report, 12)
		val crc = java.util.zip.CRC32()
		crc.update(BT_CRC_SEED)
		crc.update(report, 0, HAPTICS_REPORT_SIZE - 4)
		val value = crc.value
		for(i in 0 until 4)
			report[HAPTICS_REPORT_SIZE - 4 + i] = ((value shr (8 * i)) and 0xff).toByte()
		return report
	}

	/**
	 * Reporte Bluetooth 0x32 (142 bytes) con UN bloque de háptica: 32 muestras estéreo int8 a 3000 Hz = 64 bytes (~10,7 ms),
	 * un reporte cada ~10,7 ms. Es el formato de SAxense (github.com/egormanga/SAxense, POC original) y de AnyPS5
	 * (github.com/boykopovar/AnyPS5, issue #2056): [0]=0x32, [1]=seq<<4, [2..4]=0x91 0x07 0xFE (paquete de control, 7 bytes),
	 * [5..9]=profundidad de cola del control, [10]=contador, [11..12]=0x92 0x40 (paquete de 64 bytes), [13..76]=muestras,
	 * resto a cero y CRC32 (semilla 0xA2) en los últimos 4 bytes. Reportes más pequeños y el doble de cadencia que 0x39.
	 */
	fun hapticsBluetoothSingle(block: ByteArray, sequence: Int, packetCounter: Int, bufferLength: Int = HAPTICS_BUFFER_LENGTH): ByteArray
	{
		require(block.size == HAPTICS_SINGLE_BLOCK_BYTES) { "El bloque de háptica son $HAPTICS_SINGLE_BLOCK_BYTES bytes (32 muestras estéreo)" }
		val report = ByteArray(HAPTICS_SINGLE_REPORT_SIZE)
		report[0] = REPORT_ID_HAPTICS_SINGLE.toByte()
		report[1] = ((sequence and 0x0f) shl 4).toByte()
		report[2] = 0x91.toByte()
		report[3] = 7
		report[4] = 0xFE.toByte()
		for(i in 5..9) report[i] = bufferLength.toByte()
		report[10] = packetCounter.toByte()
		report[11] = 0x92.toByte()
		report[12] = 64
		block.copyInto(report, 13)
		val crc = java.util.zip.CRC32()
		crc.update(BT_CRC_SEED)
		crc.update(report, 0, HAPTICS_SINGLE_REPORT_SIZE - 4)
		val value = crc.value
		for(i in 0 until 4)
			report[HAPTICS_SINGLE_REPORT_SIZE - 4 + i] = ((value shr (8 * i)) and 0xff).toByte()
		return report
	}

	/**
	 * Reporte de estado para el modo háptica HD (0x31): desactiva el filtro paso-bajo de la háptica y la reducción de
	 * potencia de motores, como el estado inicial de DS5Dongle (github.com/awalol/DS5Dongle, src/state_mgr.cpp: activa
	 * AllowHapticLowPassFilter y AllowMotorPowerLevel con HapticLowPassFilter = 0). Offsets del bloque común:
	 * enable bits 2 = 1 (SDL ucEnableBits2: 0x20 filtro háptico, 0x40 nivel de potencia), reduce_motor_power = 36
	 * (hid-playstation), filtro = bit 0 del byte 39 (DS5Dongle utils.h). Los bits de gatillo quedan a 0: no los toca.
	 */
	fun hapticsStateBluetooth(sequence: Int): ByteArray
	{
		val report = ByteArray(BT_REPORT_SIZE)
		report[0] = REPORT_ID_BT.toByte()
		report[1] = ((sequence and 0x0f) shl 4).toByte()
		report[2] = BT_TAG.toByte()
		report[3 + OFFSET_ENABLE_BITS_2] = (ENABLE_HAPTIC_LOW_PASS or ENABLE_MOTOR_POWER).toByte()
		return withCrc(report)
	}

	private fun withCrc(report: ByteArray): ByteArray
	{
		val crc = java.util.zip.CRC32()
		crc.update(BT_CRC_SEED)
		crc.update(report, 0, BT_REPORT_SIZE - 4)
		val value = crc.value
		for(i in 0 until 4)
			report[BT_REPORT_SIZE - 4 + i] = ((value shr (8 * i)) and 0xff).toByte()
		return report
	}

	/** `commonStart` = posición del primer byte del bloque común dentro del reporte (1 en USB, 3 en BT). */
	private fun fillTriggers(report: ByteArray, commonStart: Int, left: TriggerEffect?, right: TriggerEffect?)
	{
		var enable = 0
		if(right != null)
		{
			enable = enable or ENABLE_RIGHT_TRIGGER
			put(report, commonStart + OFFSET_RIGHT_TRIGGER, right)
		}
		if(left != null)
		{
			enable = enable or ENABLE_LEFT_TRIGGER
			put(report, commonStart + OFFSET_LEFT_TRIGGER, left)
		}
		report[commonStart + OFFSET_ENABLE_BITS_1] = enable.toByte()
	}

	private fun put(report: ByteArray, start: Int, effect: TriggerEffect)
	{
		report[start] = effect.mode.toByte()
		effect.params.copyInto(report, start + 1)
	}
}

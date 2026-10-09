// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.dualsense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputReportBuilderTest
{
	@Test
	fun reportHasUsbIdAndSize()
	{
		val report = OutputReportBuilder.triggerEffects(null, null)
		assertEquals(63, report.size)
		assertEquals(0x02, report[0].toInt())
		assertTrue(report.drop(1).all { it.toInt() == 0 })
	}

	@Test
	fun rightTriggerGoesAtCommonOffset10()
	{
		val report = OutputReportBuilder.triggerEffects(null, TriggerEffect.CONSTANT_RESISTANCE)
		assertEquals(0x04, report[1].toInt()) // solo el bit del derecho
		assertEquals(0x01, report[1 + 10].toInt()) // modo
		assertEquals(0, report[1 + 11].toInt())
		assertEquals(110, report[1 + 12].toInt())
		// el izquierdo (offset 21) queda intacto
		assertTrue((1 + 21..1 + 31).all { report[it].toInt() == 0 })
	}

	@Test
	fun leftTriggerGoesAtCommonOffset21()
	{
		val report = OutputReportBuilder.triggerEffects(TriggerEffect.PULSE, null)
		assertEquals(0x08, report[1].toInt())
		assertEquals(0x06, report[1 + 21].toInt())
		assertEquals(15, report[1 + 22].toInt())
		assertEquals(63, report[1 + 23].toInt())
		assertEquals(128, report[1 + 24].toInt() and 0xff)
		assertTrue((1 + 10..1 + 20).all { report[it].toInt() == 0 })
	}

	@Test
	fun bothTriggersSetBothBits()
	{
		val report = OutputReportBuilder.triggerEffects(TriggerEffect.CLEAR, TriggerEffect.CLEAR)
		assertEquals(0x0c, report[1].toInt())
		assertEquals(0x05, report[1 + 10].toInt())
		assertEquals(0x05, report[1 + 21].toInt())
	}

	@Test
	fun triggerEffectBlocksDoNotOverlapOtherFields()
	{
		// El último byte del efecto izquierdo es el offset 31; el siguiente campo definido empieza en 32.
		val report = OutputReportBuilder.triggerEffects(TriggerEffect.of(0x01, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10), null)
		assertEquals(10, report[1 + 31].toInt())
		assertEquals(0, report[1 + 32].toInt())
	}

	@Test
	fun bluetoothReportLayoutAndCrc()
	{
		val report = OutputReportBuilder.triggerEffectsBluetooth(TriggerEffect.PULSE, TriggerEffect.CONSTANT_RESISTANCE, sequence = 3)
		assertEquals(78, report.size)
		assertEquals(0x31, report[0].toInt())
		assertEquals(0x30, report[1].toInt() and 0xff) // secuencia 3 en el nibble alto
		assertEquals(0x10, report[2].toInt())
		assertEquals(0x0c, report[3].toInt()) // ambos gatillos
		assertEquals(0x01, report[3 + 10].toInt()) // derecho: resistencia constante
		assertEquals(110, report[3 + 12].toInt())
		assertEquals(0x06, report[3 + 21].toInt()) // izquierdo: pulso
		// CRC-32 sobre 0xA2 + los primeros 74 bytes, little-endian al final.
		val crc = java.util.zip.CRC32().also { it.update(0xA2); it.update(report, 0, 74) }.value
		val stored = (0 until 4).sumOf { (report[74 + it].toLong() and 0xff) shl (8 * it) }
		assertEquals(crc, stored)
	}

	@Test
	fun rumbleSetsMotorsAndFlags()
	{
		val report = OutputReportBuilder.outputBluetooth(null, null, rumble = 200, sequence = 0)
		assertEquals(0x02, report[3].toInt()) // HAPTICS_SELECT, sin bits de gatillo
		assertEquals(200, report[3 + 2].toInt() and 0xff) // motor derecho
		assertEquals(200, report[3 + 3].toInt() and 0xff) // motor izquierdo
		assertEquals(0x04, report[3 + 38].toInt()) // COMPATIBLE_VIBRATION2
		val crc = java.util.zip.CRC32().also { it.update(0xA2); it.update(report, 0, 74) }.value
		val stored = (0 until 4).sumOf { (report[74 + it].toLong() and 0xff) shl (8 * it) }
		assertEquals(crc, stored)
	}

	@Test
	fun rumbleAndTriggersShareOneReport()
	{
		val report = OutputReportBuilder.outputBluetooth(TriggerEffect.PULSE, TriggerEffect.CONSTANT_RESISTANCE, rumble = 255)
		assertEquals(0x0e, report[3].toInt()) // triggers 0x0c | haptics select 0x02
		assertEquals(0x01, report[3 + 10].toInt())
		assertEquals(0x06, report[3 + 21].toInt())
		assertEquals(255, report[3 + 2].toInt() and 0xff)
	}

	@Test
	fun noRumbleLeavesMotorsUntouched()
	{
		val report = OutputReportBuilder.outputBluetooth(TriggerEffect.CLEAR, TriggerEffect.CLEAR, rumble = null)
		assertEquals(0, report[3 + 2].toInt())
		assertEquals(0, report[3 + 38].toInt())
		assertEquals(0x0c, report[3].toInt())
	}

	@Test
	fun hapticsReportLayoutAndCrc()
	{
		val block = ByteArray(128) { (it - 64).toByte() }
		val report = OutputReportBuilder.hapticsBluetooth(block, sequence = 5, packetCounter = 0x22)
		assertEquals(547, report.size)
		assertEquals(0x39, report[0].toInt())
		assertEquals(0x50, report[1].toInt() and 0xff)
		assertEquals(0x91, report[2].toInt() and 0xff)
		assertEquals(6, report[3].toInt())
		assertEquals(0x7E, report[4].toInt())
		assertEquals(48, report[5].toInt())
		assertEquals(48, report[8].toInt())
		assertEquals(0x22, report[9].toInt())
		assertEquals(0xD2, report[10].toInt() and 0xff)
		assertEquals(64, report[11].toInt())
		assertEquals(block.toList(), report.copyOfRange(12, 140).toList())
		assertTrue(report.copyOfRange(140, 543).all { it.toInt() == 0 })
		val crc = java.util.zip.CRC32().also { it.update(0xA2); it.update(report, 0, 543) }.value
		val stored = (0 until 4).sumOf { (report[543 + it].toLong() and 0xff) shl (8 * it) }
		assertEquals(crc, stored)
	}
}

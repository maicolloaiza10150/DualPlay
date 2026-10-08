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
}

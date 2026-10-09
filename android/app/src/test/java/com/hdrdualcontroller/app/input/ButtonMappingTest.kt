package com.hdrdualcontroller.app.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ButtonMappingTest
{
	@Test
	fun defaultsMatchTheOldHardcodedMapping()
	{
		val m = ButtonMapping()
		assertEquals(PsButton.CROSS, m.resolve(KeyEvent.KEYCODE_BUTTON_A))
		assertEquals(PsButton.MOON, m.resolve(KeyEvent.KEYCODE_BUTTON_B))
		assertEquals(PsButton.PS, m.resolve(KeyEvent.KEYCODE_BUTTON_MODE))
		assertEquals(PsButton.L2, m.resolve(KeyEvent.KEYCODE_BUTTON_L2))
		assertNull(m.resolve(KeyEvent.KEYCODE_BUTTON_1))
	}

	@Test
	fun swapCrossMoonOnlyAffectsDefaults()
	{
		val m = ButtonMapping(mapOf(KeyEvent.KEYCODE_BUTTON_1 to PsButton.CROSS), swapCrossMoon = true)
		assertEquals(PsButton.MOON, m.resolve(KeyEvent.KEYCODE_BUTTON_A))
		assertEquals(PsButton.PYRAMID, m.resolve(KeyEvent.KEYCODE_BUTTON_X))
		assertEquals(PsButton.CROSS, m.resolve(KeyEvent.KEYCODE_BUTTON_1)) // la asignación del usuario no se intercambia
	}

	@Test
	fun assignedKeyWinsOverDefault()
	{
		val m = ButtonMapping().assign(KeyEvent.KEYCODE_BUTTON_A, PsButton.BOX)
		assertEquals(PsButton.BOX, m.resolve(KeyEvent.KEYCODE_BUTTON_A))
		assertEquals(emptyList<Int>(), m.keysFor(PsButton.CROSS))
		assertEquals(listOf(KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_A).sorted(), m.keysFor(PsButton.BOX))
	}

	@Test
	fun serializeRoundTripIgnoresGarbage()
	{
		val m = ButtonMapping().assign(100, PsButton.R3).assign(101, PsButton.TOUCHPAD)
		val parsed = ButtonMapping.parse(m.serialize() + ";x=y;5=NOPE;;")
		assertEquals(PsButton.R3, parsed.resolve(100))
		assertEquals(PsButton.TOUCHPAD, parsed.resolve(101))
		assertEquals(m.serialize(), parsed.serialize())
	}
}

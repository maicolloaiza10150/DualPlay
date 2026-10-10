package com.hdrdualcontroller.app.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class OrientationTrackerTest
{
	private fun TrackerNorm(t: OrientationTracker) =
		sqrt(t.orientX * t.orientX + t.orientY * t.orientY + t.orientZ * t.orientZ + t.orientW * t.orientW)

	@Test
	fun restingControllerKeepsAStableUnitOrientation()
	{
		val t = OrientationTracker()
		// Reposo plano: gravedad en +Y (convención de la PS5), giroscopio a 0, 250 Hz durante 4 s.
		var us = 1_000_000L
		repeat(1000) { t.update(0f, 0f, 0f, 0f, 1f, 0f, us); us += 4000 }
		assertEquals(1f, TrackerNorm(t), 1e-3f)
		val before = floatArrayOf(t.orientX, t.orientY, t.orientZ, t.orientW)
		repeat(250) { t.update(0f, 0f, 0f, 0f, 1f, 0f, us); us += 4000 }
		val after = floatArrayOf(t.orientX, t.orientY, t.orientZ, t.orientW)
		for(i in 0..3) assertEquals("componente $i", before[i], after[i], 2e-3f)
	}

	@Test
	fun gyroRotationIsIntegratedAndStaysNormalised()
	{
		val t = OrientationTracker()
		var us = 0L
		repeat(60) { t.update(0f, 0f, 0f, 0f, 1f, 0f, us); us += 4000 }
		val rest = t.orientW
		// Giro de 1 rad/s sobre el eje y durante 0,5 s sin corrección del acelerómetro (a = 0).
		repeat(125) { t.update(0f, 1f, 0f, 0f, 0f, 0f, us); us += 4000 }
		assertEquals(1f, TrackerNorm(t), 1e-3f)
		// El filtro "fuzz" de la lib atenúa los cambios lentos (igual que en el escritorio): se comprueba el sentido, no la magnitud.
		assertTrue("giro sobre y: q=(${t.orientX}, ${t.orientY}, ${t.orientZ}, ${t.orientW}), reposo w=$rest", t.orientY > 0.05f && abs(t.orientX) < 0.01f && abs(t.orientZ) < 0.01f)
		assertEquals(1f, t.gyroY, 0f)
	}

	@Test
	fun timestampWrapAround32BitsIsHandled()
	{
		val t = OrientationTracker()
		var us = 0xFFFFFFFFL - 8000
		repeat(10) { t.update(0f, 0f, 0f, 0f, 1f, 0f, us); us += 4000 }
		assertEquals(1f, TrackerNorm(t), 1e-3f)
	}
}

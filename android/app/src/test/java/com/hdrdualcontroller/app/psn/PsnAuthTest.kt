// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.psn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PsnAuthTest
{
	// Valores sintéticos, no pertenecen a ninguna cuenta real.
	@Test
	fun accountIdIsLittleEndianBase64()
	{
		assertEquals("AQAAAAAAAAA=", PsnAuth.accountIdFromUserId("1"))
		// 0x0102030405060708 -> bytes 08 07 06 05 04 03 02 01
		assertEquals("CAcGBQQDAgE=", PsnAuth.accountIdFromUserId("72623859790382856"))
	}

	@Test
	fun accountIdDecodesToEightBytes()
	{
		val decoded = java.util.Base64.getDecoder().decode(PsnAuth.accountIdFromUserId("1992796232824164116"))
		assertEquals(8, decoded.size)
	}

	@Test
	fun redirectDetection()
	{
		assertTrue(PsnAuth.isRedirect("${PsnAuth.REDIRECT_PAGE}?code=abc&cid=1"))
		assertFalse(PsnAuth.isRedirect("https://auth.api.sonyentertainmentnetwork.com/2.0/oauth/authorize"))
		assertFalse(PsnAuth.isRedirect(null))
	}
}

// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.common

import org.junit.Assert.*
import org.junit.Test

class LogRedactorTest
{
	@Test
	fun sessionId()
	{
		val r = LogRedactor.redact("[I] Ctrl received valid Session Id: FAKESESSIONID1234567890ABCDEFGH")
		assertEquals("[I] Ctrl received valid Session Id: [REDACTED]", r)
	}

	@Test
	fun keyValueForms()
	{
		val r = LogRedactor.redact(
			"regist_key: AAAA1111 x\nrp_key=BBBB2222\n{\"morning\":\"CCCC3333\",\"nonce\": \"DDDD4444\"}\n" +
			"PIN: 12345678\naccount_id=ZmFrZWFjY291bnQ=\nAccess_Token = tok_fake_999")
		for(secret in listOf("AAAA1111", "BBBB2222", "CCCC3333", "DDDD4444", "12345678", "ZmFrZWFjY291bnQ=", "tok_fake_999"))
			assertFalse(secret, r.contains(secret))
		assertTrue(r.contains("{\"morning\":\"[REDACTED]\""))
		assertTrue(r.contains("regist_key: [REDACTED] x"))
	}

	@Test
	fun hexdump()
	{
		val text = "start\n[V] offset 0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f  0123456789abcdef\n" +
			"[V]      0 de ad be ef 00 11 22 33 44 55 66 77 88 99 aa bb ........\n" +
			"[V]     10 de ad be ef 00 11 22 33 44 55 66 77 88 99 aa bb ........\nend"
		assertEquals("start\n[hexdump omitido]\nend", LogRedactor.redact(text))
	}

	@Test
	fun longHexString()
	{
		assertEquals("key [REDACTED]", LogRedactor.redact("key " + "ab12".repeat(8)))
	}

	@Test
	fun normalLinesUntouched()
	{
		val text = "[I] Takion connected\n[I] RTT = 2.830 ms\n[I] pinIncorrect handled\nSession started\nregist done"
		assertEquals(text, LogRedactor.redact(text))
	}

	@Test
	fun idempotent()
	{
		val text = "Session Id: FAKE123\nnonce=abc\noffset 0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f\n     0 de ad be ef 00 11 22 33 44 55 66 77 88 99 aa bb\nok"
		val once = LogRedactor.redact(text)
		assertEquals(once, LogRedactor.redact(once))
	}
}

// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.common

import java.io.File

/**
 * Enmascara secretos (Session ID, regist key, morning, nonce, PIN, account id, tokens y volcados hex)
 * en texto de log antes de mostrarlo o compartirlo. Idempotente.
 */
object LogRedactor
{
	const val MASK = "[REDACTED]"
	const val HEXDUMP_MARK = "[hexdump omitido]"

	// Fuente del formato: lib/src/log.c, chiaki_log_hexdump.
	private val hexdumpHeader = Regex("""offset\s+0\s+1\s+2\s+3\s+4\s+5\s+6\s+7\s+8\s+9\s+a\s+b\s+c\s+d\s+e\s+f(\s+0123456789abcdef)?\s*$""")
	private val hexdumpRow = Regex("""(^|\s)[0-9a-f]{1,6}\s+(?:[0-9a-fA-F]{2}\s+){8,}""")

	private val sessionId = Regex("""(Session Id\s*:?\s*)(\S+)""", RegexOption.IGNORE_CASE)

	private const val keys = """regist[_\s-]?key|rp[_-]?regist[_-]?key|rp[_-]?key(?:[_-]?type)?|morning|nonce|pin|login[_\s-]?pin|""" +
		"""(?:psn[_\s-]?)?account[_\s-]?id|(?:access|refresh|id|auth)?[_\s-]?token|authorization|password|secret"""
	private val keyValue = Regex(
		"""(?<![A-Za-z0-9_])((?:$keys)["']?\s*[:=]\s*["']?)(\[REDACTED\]|[^\s,;&"'}\]]+)""",
		RegexOption.IGNORE_CASE)

	private val longHex = Regex("""(?<![0-9A-Za-z])[0-9a-fA-F]{32,}(?![0-9A-Za-z])""")

	fun redact(text: String): String
	{
		val out = StringBuilder()
		var lastWasHexdump = false
		val lines = text.split("\n")
		for((i, line) in lines.withIndex())
		{
			val isHexdump = hexdumpHeader.containsMatchIn(line) || hexdumpRow.containsMatchIn(line)
			if(isHexdump)
			{
				if(!lastWasHexdump)
				{
					if(out.isNotEmpty() || i > 0)
						out.append('\n')
					out.append(HEXDUMP_MARK)
				}
				lastWasHexdump = true
				continue
			}
			if(line == HEXDUMP_MARK)
			{
				if(lastWasHexdump)
					continue
				lastWasHexdump = true
				if(i > 0) out.append('\n')
				out.append(line)
				continue
			}
			lastWasHexdump = false
			if(i > 0) out.append('\n')
			out.append(redactLine(line))
		}
		return out.toString()
	}

	private fun redactLine(line: String): String
	{
		var s = sessionId.replace(line) { it.groupValues[1] + MASK }
		s = keyValue.replace(s) { it.groupValues[1] + MASK }
		s = longHex.replace(s, MASK)
		return s
	}

	/** Escribe una copia redactada de [source] en [destination] y la devuelve. */
	fun redactFile(source: File, destination: File): File
	{
		destination.parentFile?.mkdirs()
		destination.writeText(redact(source.readText()))
		return destination
	}
}

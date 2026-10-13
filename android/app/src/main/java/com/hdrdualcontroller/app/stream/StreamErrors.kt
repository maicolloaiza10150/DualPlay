// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.stream

import androidx.annotation.StringRes
import com.hdrdualcontroller.app.R

/**
 * DUALPLAY: errores de sesión de lib/ traducidos a mensajes entendibles con una acción clara.
 * Los valores numéricos son los de ChiakiQuitReason (lib/include/chiaki/session.h); el orden del enum es parte de su ABI.
 */
enum class StreamErrorKind(@StringRes val title: Int, @StringRes val body: Int)
{
	CONSOLE_BUSY(R.string.stream_error_busy_title, R.string.stream_error_busy_body),
	UNREACHABLE(R.string.stream_error_unreachable_title, R.string.stream_error_unreachable_body),
	CONSOLE_CRASH(R.string.stream_error_crash_title, R.string.stream_error_crash_body),
	VERSION(R.string.stream_error_version_title, R.string.stream_error_version_body),
	CONNECTION_LOST(R.string.stream_error_lost_title, R.string.stream_error_lost_body),
	GENERIC(R.string.stream_error_generic_title, R.string.stream_error_generic_body);

	companion object
	{
		private const val SESSION_REQUEST_CONNECTION_REFUSED = 3
		private const val SESSION_REQUEST_RP_IN_USE = 4
		private const val SESSION_REQUEST_RP_CRASH = 5
		private const val SESSION_REQUEST_RP_VERSION_MISMATCH = 6
		private const val CTRL_CONNECT_FAILED = 8
		private const val CTRL_CONNECTION_REFUSED = 9
		private const val STREAM_CONNECTION_UNKNOWN = 10
		private const val STREAM_CONNECTION_REMOTE_DISCONNECTED = 11

		fun fromQuitReason(value: Int) = when(value)
		{
			SESSION_REQUEST_RP_IN_USE -> CONSOLE_BUSY
			SESSION_REQUEST_CONNECTION_REFUSED, CTRL_CONNECT_FAILED, CTRL_CONNECTION_REFUSED -> UNREACHABLE
			SESSION_REQUEST_RP_CRASH -> CONSOLE_CRASH
			SESSION_REQUEST_RP_VERSION_MISMATCH -> VERSION
			STREAM_CONNECTION_UNKNOWN, STREAM_CONNECTION_REMOTE_DISCONNECTED -> CONNECTION_LOST
			else -> GENERIC
		}
	}
}

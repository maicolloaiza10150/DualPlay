package com.hdrdualcontroller.app.stream

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamErrorKindTest
{
	@Test fun rpInUseIsBusy() = assertEquals(StreamErrorKind.CONSOLE_BUSY, StreamErrorKind.fromQuitReason(4))
	@Test fun refusedAndConnectFailedAreUnreachable()
	{
		for(v in listOf(3, 8, 9))
			assertEquals(StreamErrorKind.UNREACHABLE, StreamErrorKind.fromQuitReason(v))
	}
	@Test fun crashAndVersion()
	{
		assertEquals(StreamErrorKind.CONSOLE_CRASH, StreamErrorKind.fromQuitReason(5))
		assertEquals(StreamErrorKind.VERSION, StreamErrorKind.fromQuitReason(6))
	}
	@Test fun streamDropsAreConnectionLost()
	{
		assertEquals(StreamErrorKind.CONNECTION_LOST, StreamErrorKind.fromQuitReason(10))
		assertEquals(StreamErrorKind.CONNECTION_LOST, StreamErrorKind.fromQuitReason(11))
	}
	@Test fun unknownFallsBackToGeneric()
	{
		assertEquals(StreamErrorKind.GENERIC, StreamErrorKind.fromQuitReason(2))
		assertEquals(StreamErrorKind.GENERIC, StreamErrorKind.fromQuitReason(99))
	}
}

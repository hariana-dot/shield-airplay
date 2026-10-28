package io.github.jqssun.airplay.service

import io.github.jqssun.airplay.service.ReceiverStatusPolicy.CentreCopy
import org.junit.Assert.*
import org.junit.Test

class ReceiverStatusPolicyTest {
    @Test fun `receiver light follows server running only`() {
        val off = ReceiverStatusPolicy.deriveLights(
            serverRunning = false,
            mirroringActive = true, videoPlaybackActive = false, audioOnly = false,
            videoSessionPending = false, videoBuffering = false,
            handshakeUntilMs = null, nowMs = 0,
        )
        assertFalse(off.receiverOn)
        assertFalse(off.senderOn)
        assertFalse(off.mediaOn)

        val on = ReceiverStatusPolicy.deriveLights(
            serverRunning = true,
            mirroringActive = false, videoPlaybackActive = false, audioOnly = false,
            videoSessionPending = false, videoBuffering = false,
            handshakeUntilMs = null, nowMs = 0,
        )
        assertTrue(on.receiverOn)
        assertFalse(on.senderOn)
        assertFalse(on.mediaOn)
    }

    @Test fun `media light is green for mirror video or audio`() {
        for ((m, v, a) in listOf(
            Triple(true, false, false),
            Triple(false, true, false),
            Triple(false, false, true),
        )) {
            val lights = ReceiverStatusPolicy.deriveLights(
                serverRunning = true,
                mirroringActive = m, videoPlaybackActive = v, audioOnly = a,
                videoSessionPending = false, videoBuffering = false,
                handshakeUntilMs = null, nowMs = 0,
            )
            assertTrue("$m/$v/$a", lights.mediaOn)
            assertTrue("$m/$v/$a", lights.senderOn)
        }
    }

    @Test fun `pending video handshake lights sender and connecting copy`() {
        val lights = ReceiverStatusPolicy.deriveLights(
            serverRunning = true,
            mirroringActive = false, videoPlaybackActive = false, audioOnly = false,
            videoSessionPending = true, videoBuffering = false,
            handshakeUntilMs = null, nowMs = 0,
        )
        assertTrue(lights.senderOn)
        assertTrue(lights.mediaOn)
        assertEquals(
            CentreCopy.CONNECTING,
            ReceiverStatusPolicy.deriveCentreCopy(
                serverRunning = true,
                mirroringActive = false, videoPlaybackActive = false, audioOnly = false,
                videoSessionPending = true, videoBuffering = false,
                handshakeUntilMs = null, nowMs = 0,
            ),
        )
    }

    @Test fun `handshake window alone lights sender without forever TCP trust`() {
        val until = 10_000L
        val during = ReceiverStatusPolicy.deriveLights(
            serverRunning = true,
            mirroringActive = false, videoPlaybackActive = false, audioOnly = false,
            videoSessionPending = false, videoBuffering = false,
            handshakeUntilMs = until, nowMs = 9_999L,
        )
        assertTrue(during.senderOn)
        assertFalse(during.mediaOn)

        val after = ReceiverStatusPolicy.deriveLights(
            serverRunning = true,
            mirroringActive = false, videoPlaybackActive = false, audioOnly = false,
            videoSessionPending = false, videoBuffering = false,
            handshakeUntilMs = until, nowMs = 10_000L,
        )
        assertFalse(after.senderOn)
        assertEquals(
            CentreCopy.HERO,
            ReceiverStatusPolicy.deriveCentreCopy(
                serverRunning = true,
                mirroringActive = false, videoPlaybackActive = false, audioOnly = false,
                videoSessionPending = false, videoBuffering = false,
                handshakeUntilMs = until, nowMs = 10_000L,
            ),
        )
    }

    @Test fun `idle ready shows hero never press start on the sender`() {
        assertEquals(
            CentreCopy.HERO,
            ReceiverStatusPolicy.deriveCentreCopy(
                serverRunning = true,
                mirroringActive = false, videoPlaybackActive = false, audioOnly = false,
                videoSessionPending = false, videoBuffering = false,
                handshakeUntilMs = null, nowMs = 0,
            ),
        )
        assertEquals(
            CentreCopy.MEDIA,
            ReceiverStatusPolicy.deriveCentreCopy(
                serverRunning = true,
                mirroringActive = true, videoPlaybackActive = false, audioOnly = false,
                videoSessionPending = false, videoBuffering = false,
                handshakeUntilMs = null, nowMs = 0,
            ),
        )
    }

    @Test fun `stale tcp count resets without media or pending session`() {
        assertTrue(
            ReceiverStatusPolicy.shouldResetStaleConnectionCount(
                connectionCount = 1,
                mirroringActive = false, videoPlaybackActive = false, audioOnly = false,
                videoSessionPending = false,
                lastActivityAtMs = 1_000L, nowMs = 1_000L + ReceiverStatusPolicy.STALE_CONNECTION_MS,
            ),
        )
        assertFalse(
            ReceiverStatusPolicy.shouldResetStaleConnectionCount(
                connectionCount = 1,
                mirroringActive = false, videoPlaybackActive = false, audioOnly = false,
                videoSessionPending = false,
                lastActivityAtMs = 1_000L, nowMs = 1_000L + ReceiverStatusPolicy.STALE_CONNECTION_MS - 1,
            ),
        )
        assertFalse(
            ReceiverStatusPolicy.shouldResetStaleConnectionCount(
                connectionCount = 1,
                mirroringActive = true, videoPlaybackActive = false, audioOnly = false,
                videoSessionPending = false,
                lastActivityAtMs = 1_000L, nowMs = 60_000L,
            ),
        )
        assertFalse(
            ReceiverStatusPolicy.shouldResetStaleConnectionCount(
                connectionCount = 0,
                mirroringActive = false, videoPlaybackActive = false, audioOnly = false,
                videoSessionPending = false,
                lastActivityAtMs = 1_000L, nowMs = 60_000L,
            ),
        )
    }

    @Test fun `stale watchdog does not reset while handshake window is open`() {
        val activityAt = 1_000L
        val handshakeUntil = activityAt + ReceiverStatusPolicy.SENDER_HANDSHAKE_MS
        // Classic bug: 2s after TCP connect, still mid-handshake — must NOT wipe count.
        assertFalse(
            ReceiverStatusPolicy.shouldResetStaleConnectionCount(
                connectionCount = 1,
                mirroringActive = false, videoPlaybackActive = false, audioOnly = false,
                videoSessionPending = false,
                lastActivityAtMs = activityAt,
                nowMs = activityAt + ReceiverStatusPolicy.STALE_CONNECTION_MS + 3,
                handshakeUntilMs = handshakeUntil,
            ),
        )
        // After handshake expires with no media — idle leftover may reset.
        assertTrue(
            ReceiverStatusPolicy.shouldResetStaleConnectionCount(
                connectionCount = 1,
                mirroringActive = false, videoPlaybackActive = false, audioOnly = false,
                videoSessionPending = false,
                lastActivityAtMs = activityAt,
                nowMs = handshakeUntil,
                handshakeUntilMs = handshakeUntil,
            ),
        )
    }

    @Test fun `stale watchdog does not reset while buffering`() {
        assertFalse(
            ReceiverStatusPolicy.shouldResetStaleConnectionCount(
                connectionCount = 1,
                mirroringActive = false, videoPlaybackActive = false, audioOnly = false,
                videoSessionPending = false,
                lastActivityAtMs = 1_000L, nowMs = 60_000L,
                videoBuffering = true,
            ),
        )
    }

    @Test fun `stale check delay waits for handshake end`() {
        val now = 1_000L
        val until = now + ReceiverStatusPolicy.SENDER_HANDSHAKE_MS
        assertEquals(
            ReceiverStatusPolicy.SENDER_HANDSHAKE_MS,
            ReceiverStatusPolicy.staleCheckDelayMs(nowMs = now, handshakeUntilMs = until),
        )
        assertEquals(
            ReceiverStatusPolicy.STALE_CONNECTION_MS,
            ReceiverStatusPolicy.staleCheckDelayMs(nowMs = now, handshakeUntilMs = null),
        )
        assertEquals(
            ReceiverStatusPolicy.STALE_CONNECTION_MS,
            ReceiverStatusPolicy.staleCheckDelayMs(nowMs = until + 1, handshakeUntilMs = until),
        )
    }

    @Test fun `handshake helper advances window from now`() {
        assertEquals(8_000L, ReceiverStatusPolicy.handshakeUntilAfter(0L))
        assertEquals(5_000L + 8_000L, ReceiverStatusPolicy.handshakeUntilAfter(5_000L))
    }
}

package io.github.jqssun.airplay.service

/**
 * Derives the three receiver status lights and overview centre copy from
 * meaningful session activity — never from the raw TCP pre-auth connectionCount alone.
 */
internal object ReceiverStatusPolicy {
    /** How long Sender stays green after a real session signal without media yet. */
    const val SENDER_HANDSHAKE_MS = 8_000L

    /**
     * Idle TCP leftovers older than this are treated as stale for UI and watchdog.
     * Kept short so sticky "connected" clears within a few seconds after a real
     * destroy/idle — but [shouldResetStaleConnectionCount] must still refuse while
     * a handshake window is open (handshake is longer than this).
     */
    const val STALE_CONNECTION_MS = 2_000L

    data class Lights(
        val receiverOn: Boolean,
        val senderOn: Boolean,
        val mediaOn: Boolean,
    )

    enum class CentreCopy {
        /** Show the Ready hero / idle help. */
        HERO,
        /** Handshake or buffering before picture. */
        CONNECTING,
        /** Media is active — no centre placeholder. */
        MEDIA,
    }

    /** True when mirror, URL video, or audio-only is actually linked. */
    fun hasRealMedia(
        mirroringActive: Boolean,
        videoPlaybackActive: Boolean,
        audioOnly: Boolean,
    ): Boolean = mirroringActive || videoPlaybackActive || audioOnly

    /**
     * Media light: green while mirror/video/audio is active, or while URL video is
     * clearly pending/buffering with progress toward playback.
     */
    fun mediaActive(
        mirroringActive: Boolean,
        videoPlaybackActive: Boolean,
        audioOnly: Boolean,
        videoSessionPending: Boolean,
        videoBuffering: Boolean,
    ): Boolean {
        if (hasRealMedia(mirroringActive, videoPlaybackActive, audioOnly)) return true
        // Pending polls or in-player buffering count as media starting.
        return videoSessionPending || (videoPlaybackActive && videoBuffering)
    }

    /**
     * Sender light: green for real media, pending video handshake, or a short window
     * after the first meaningful client signal. Never forever from a leftover TCP count.
     */
    fun senderActive(
        mirroringActive: Boolean,
        videoPlaybackActive: Boolean,
        audioOnly: Boolean,
        videoSessionPending: Boolean,
        handshakeUntilMs: Long?,
        nowMs: Long,
    ): Boolean {
        if (hasRealMedia(mirroringActive, videoPlaybackActive, audioOnly)) return true
        if (videoSessionPending) return true
        val until = handshakeUntilMs ?: return false
        return nowMs < until
    }

    fun deriveLights(
        serverRunning: Boolean,
        mirroringActive: Boolean,
        videoPlaybackActive: Boolean,
        audioOnly: Boolean,
        videoSessionPending: Boolean,
        videoBuffering: Boolean,
        handshakeUntilMs: Long?,
        nowMs: Long,
    ): Lights {
        val media = mediaActive(
            mirroringActive, videoPlaybackActive, audioOnly,
            videoSessionPending, videoBuffering,
        )
        val sender = senderActive(
            mirroringActive, videoPlaybackActive, audioOnly,
            videoSessionPending, handshakeUntilMs, nowMs,
        )
        return Lights(
            receiverOn = serverRunning,
            senderOn = serverRunning && sender,
            mediaOn = serverRunning && media,
        )
    }

    fun deriveCentreCopy(
        serverRunning: Boolean,
        mirroringActive: Boolean,
        videoPlaybackActive: Boolean,
        audioOnly: Boolean,
        videoSessionPending: Boolean,
        videoBuffering: Boolean,
        handshakeUntilMs: Long?,
        nowMs: Long,
    ): CentreCopy {
        if (!serverRunning) return CentreCopy.HERO
        if (hasRealMedia(mirroringActive, videoPlaybackActive, audioOnly)) return CentreCopy.MEDIA
        val handshake =
            videoSessionPending ||
                videoBuffering ||
                (handshakeUntilMs != null && nowMs < handshakeUntilMs)
        return if (handshake) CentreCopy.CONNECTING else CentreCopy.HERO
    }

    /**
     * Whether a leftover TCP connectionCount should be forced back to zero.
     * Never reset while handshake is still open, media is linked, video is pending,
     * or buffering is in progress — only when truly idle sockets are past [staleAfterMs].
     */
    fun shouldResetStaleConnectionCount(
        connectionCount: Int,
        mirroringActive: Boolean,
        videoPlaybackActive: Boolean,
        audioOnly: Boolean,
        videoSessionPending: Boolean,
        lastActivityAtMs: Long,
        nowMs: Long,
        staleAfterMs: Long = STALE_CONNECTION_MS,
        handshakeUntilMs: Long? = null,
        videoBuffering: Boolean = false,
    ): Boolean {
        if (connectionCount <= 0) return false
        if (hasRealMedia(mirroringActive, videoPlaybackActive, audioOnly)) return false
        if (videoSessionPending) return false
        if (videoBuffering) return false
        // Mid-handshake: AirPlay auth/setup often takes longer than STALE_CONNECTION_MS.
        if (handshakeUntilMs != null && handshakeUntilMs > 0L && nowMs < handshakeUntilMs) return false
        if (lastActivityAtMs <= 0L) return nowMs > 0L // never saw activity → reset
        return nowMs - lastActivityAtMs >= staleAfterMs
    }

    /** Delay until the next stale check: past idle timeout, and not before handshake ends. */
    fun staleCheckDelayMs(
        nowMs: Long,
        handshakeUntilMs: Long?,
        staleAfterMs: Long = STALE_CONNECTION_MS,
    ): Long {
        val untilHandshake = handshakeUntilMs?.let { (it - nowMs).coerceAtLeast(0L) } ?: 0L
        return maxOf(staleAfterMs, untilHandshake)
    }

    /** Extend or start the sender handshake window from a meaningful signal. */
    fun handshakeUntilAfter(nowMs: Long, windowMs: Long = SENDER_HANDSHAKE_MS): Long =
        nowMs + windowMs
}

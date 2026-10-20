package com.shilapi.xcertplay.media

/** Tracks the unsigned AudioTrack head across wrap without flushing already queued sound. */
internal class AudioBufferProgress(private val frameBytes: Int) {
    private var writtenBytes = 0L
    private var playedFrames = 0L
    private var lastHead = 0L

    fun written(bytes: Int) { writtenBytes += bytes }

    fun queuedBytes(rawHead: Int): Long {
        val head = rawHead.toLong() and 0xffff_ffffL
        playedFrames += (head - lastHead) and 0xffff_ffffL
        lastHead = head
        return (writtenBytes - playedFrames * frameBytes).coerceAtLeast(0)
    }

    fun shouldRebuffer(isMedia: Boolean, playing: Boolean, underrunSinceStart: Boolean?,
        compressedQueueEmpty: Boolean, rawHead: Int, floorBytes: Long): Boolean {
        if (!isMedia || !playing || !compressedQueueEmpty) return false
        val remaining = queuedBytes(rawHead)
        // API 22 has no underrun counter. Actual exhaustion is the fallback signal;
        // a merely low buffer must not repeatedly pause otherwise healthy playback.
        return if (underrunSinceStart == null) writtenBytes > 0 && remaining == 0L
            else underrunSinceStart && remaining <= floorBytes
    }
}

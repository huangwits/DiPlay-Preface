package com.shilapi.xcertplay.transport

import java.nio.ByteBuffer

internal data class UsbReadQueueResult(val queued: Boolean, val firstBytes: Int, val fallbackBytes: Int? = null)

/**
 * Android 8/8.1 rejects requests above 16 KiB before native queueing. On API 28+,
 * a smaller request is a compatibility fallback for explicit vendor queue rejection.
 * Android 9 UsbRequest.queue(ByteBuffer) accepts any size and clears its queued state on false:
 * https://android.googlesource.com/platform/frameworks/base/+/android-9.0.0_r1/core/java/android/hardware/usb/UsbRequest.java
 * Call under the pipe's state lock, including publication, queueing and the open-state checks.
 */
internal class UsbReadQueuePolicy(sdkInt: Int) {
    private val platformLimit = if (sdkInt < 28) COMPATIBILITY_BYTES else Int.MAX_VALUE
    private var successfulLimit: Int? = null

    fun queue(buffer: ByteBuffer, checkOpen: () -> Unit, submit: (ByteBuffer) -> Boolean): UsbReadQueueResult {
        require(buffer.isDirect && !buffer.isReadOnly) { "USB read requires a writable direct buffer" }
        checkOpen()
        val position = buffer.position()
        val originalLimit = buffer.limit()
        val firstBytes = minOf(buffer.remaining(), platformLimit, successfulLimit ?: Int.MAX_VALUE)
        buffer.limit(position + firstBytes)
        if (submit(buffer)) return UsbReadQueueResult(true, firstBytes)
        // AOSP guarantees an unchanged buffer on explicit false. Do not retry an ambiguous
        // request that threw or unexpectedly changed its buffer state.
        check(buffer.position() == position && buffer.limit() == position + firstBytes) {
            "Rejected USB queue changed its buffer state"
        }
        if (firstBytes <= COMPATIBILITY_BYTES) {
            buffer.limit(originalLimit)
            return UsbReadQueueResult(false, firstBytes)
        }
        checkOpen()
        buffer.limit(position + COMPATIBILITY_BYTES)
        val queued = submit(buffer)
        if (queued) successfulLimit = COMPATIBILITY_BYTES else buffer.limit(originalLimit)
        return UsbReadQueueResult(queued, firstBytes, COMPATIBILITY_BYTES)
    }

    private companion object { const val COMPATIBILITY_BYTES = 16 * 1024 }
}

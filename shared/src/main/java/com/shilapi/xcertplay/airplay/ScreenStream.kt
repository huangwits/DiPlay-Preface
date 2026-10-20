package com.shilapi.xcertplay.airplay

import android.util.Log
import com.shilapi.xcertplay.media.MediaCodecSupport
import java.io.Closeable
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

enum class VideoCodec { H264, H265 }

/**
 * Receives one CarPlay screen stream on a TCP data port.
 *
 * Each message is a 128-byte AirPlayScreenHeader followed by a body: a clear VideoConfig
 * (avcC/hvcC) or a ChaCha20-Poly1305 sealed VideoFrame. The key is the DataStream output key
 * and the per-frame nonce is an 8-byte little-endian counter.
 */
class ScreenStream(private val key: ByteArray, private val onDiagnostic: (String) -> Unit = {}) : Closeable {
    interface Listener {
        fun onCodec(codec: VideoCodec) {}
        fun onConfig(codecData: ByteArray) {}
        fun onFrame(naluBytes: ByteArray) {}
        /**
         * A frame with the iPhone's frame time ([senderNanos], from the header) and when its body finished
         * arriving ([arrivalNanos], System.nanoTime). Listeners that do not pace frames ignore both.
         */
        fun onFrame(naluBytes: ByteArray, senderNanos: Long, arrivalNanos: Long) = onFrame(naluBytes)
        fun onClosed(cause: Throwable?) {}
    }

    private val closed = AtomicBoolean(false)
    private val frameCounter = AtomicLong(0)
    private val firstFrameLogged = AtomicBoolean(false)
    private val buffers = VideoBufferPool()
    private val decryptor = AirPlayCrypto.ChaChaOpener(key)
    private var nalLengthSize = 4
    private var server: ServerSocket? = null
    private var socket: Socket? = null
    private var thread: Thread? = null
    @Volatile private var listener: Listener = object : Listener {}

    fun listen(listener: Listener): Int {
        this.listener = listener
        val bound = ServerSocket()
        bound.reuseAddress = true
        bound.bind(InetSocketAddress(InetAddress.getByName("::"), 0))
        server = bound
        thread = Thread({ accept(bound) }, "airplay-screen").apply { isDaemon = true; start() }
        return bound.localPort
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        safeClose(socket)
        safeClose(server)
        thread?.interrupt()
    }

    private fun accept(bound: ServerSocket) {
        try {
            val accepted = bound.accept()
            socket = accepted
            run(accepted)
        } catch (error: Exception) {
            if (!closed.get()) listener.onClosed(error)
        }
    }

    private fun run(sock: Socket) {
        var failure: Throwable? = null
        val stats = StreamReceiveStats("video", onDiagnostic)
        try {
            val input = sock.getInputStream()
            val header = ByteArray(HEADER_LEN)
            while (!closed.get()) {
                stats.reading()
                if (!readFully(input, header, HEADER_LEN)) break
                val bodySize = readU32Le(header, 0)
                if (bodySize < 0 || bodySize > MAX_BODY) break
                val body = buffers.acquire(bodySize)
                if (!readFully(input, body.bytes, bodySize)) { body.close(); break }
                val arrivalNanos = System.nanoTime()
                stats.received(HEADER_LEN + bodySize)
                try { onMessage(header, body, stats, arrivalNanos) } finally { body.close() }
                stats.processed()
            }
        } catch (error: Exception) {
            failure = error
        } finally {
            stats.flush(ended = true)
            if (socket === sock) socket = null
            safeClose(sock)
            if (!closed.get()) listener.onClosed(failure)
        }
    }

    private fun onMessage(header: ByteArray, body: VideoBufferPool.Lease, stats: StreamReceiveStats, arrivalNanos: Long) {
        when (header[OPCODE_OFFSET].toInt() and 0xff) {
            OP_VIDEO_FRAME -> {
                val encrypted = body.length >= ScreenCodec.TAG_SIZE
                val payload = if (encrypted) buffers.acquire(body.length - ScreenCodec.TAG_SIZE) else null
                val payloadBytes = payload?.bytes ?: body.bytes
                val payloadLength = if (encrypted) {
                    val start = System.nanoTime()
                    try {
                        val length = decryptor.open(
                            frameCounter.get(), body.bytes, body.length, header, payloadBytes,
                        )
                        stats.decrypted(System.nanoTime() - start, body.length)
                        frameCounter.incrementAndGet()
                        length
                    } catch (error: Throwable) {
                        payload?.close()
                        throw error
                    }
                } else body.length
                try {
                    val annexB = ScreenCodec.lengthPrefixedToAnnexB(payloadBytes, payloadLength, nalLengthSize)
                    if (firstFrameLogged.compareAndSet(false, true)) {
                        Log.i(TAG,
                            "video first decrypted frame sealed=${body.length} plain=$payloadLength " +
                                "head=${payloadBytes.hexPrefix(16, payloadLength)} chacha=${AirPlayCrypto.chachaImplementation}")
                    }
                    listener.onFrame(annexB, ScreenCodec.senderNanos(header), arrivalNanos)
                } finally {
                    payload?.close()
                }
            }
            OP_VIDEO_CONFIG -> {
                // The phone can open an idle screen with a header-only config packet.
                // It carries no new codec record; preserve the stream until real data arrives.
                if (body.length == 0) {
                    onDiagnostic("Video: empty configuration ignored; retaining stream state")
                    return
                }
                val (codec, codecData) = ScreenCodec.detectConfig(body.bytes.copyOf(body.length))
                val lengthOffset = if (codec == VideoCodec.H265) 21 else 4
                require(codecData.size > lengthOffset) { "Truncated video configuration" }
                nalLengthSize = (codecData[lengthOffset].toInt() and 3) + 1
                require(nalLengthSize != 3) { "Reserved NAL length size" }
                Log.i(TAG, "video codec config codec=$codec body=${body.length} data=${codecData.size}")
                listener.onCodec(codec)
                listener.onConfig(codecData)
            }
        }
    }

    private fun readFully(input: InputStream, output: ByteArray, length: Int): Boolean {
        if (length < 0 || length > output.size) return false
        var offset = 0
        while (offset < length) {
            val read = input.read(output, offset, length - offset)
            if (read < 0) return false
            offset += read
        }
        return true
    }

    private companion object {
        const val TAG = "xcertplay-usb"
        const val HEADER_LEN = 128
        const val OPCODE_OFFSET = 4
        const val OP_VIDEO_FRAME = 0
        const val OP_VIDEO_CONFIG = 1
        const val MAX_BODY = 8 * 1024 * 1024
    }
}

private fun ByteArray.hexPrefix(length: Int, limit: Int = size): String =
    copyOf(minOf(size, limit, length)).joinToString("") { "%02x".format(it.toInt() and 0xff) }

/** Extracts the avcC/hvcC codec-data record from a VideoConfig payload. */
object ScreenCodec {
    /**
     * The frame time in a screen header: bytes 8..15, a little-endian NTP 32.32 value, in nanoseconds.
     * On a Tang with iOS 27 consecutive main-screen frames are exactly 1/60 s apart (1/30 s on the
     * cluster stream), and skipped frames leave whole multiples.
     */
    fun senderNanos(header: ByteArray): Long {
        if (header.size < SENDER_TIME_OFFSET + 8) return 0L
        var raw = 0L
        for (index in 7 downTo 0) raw = (raw shl 8) or (header[SENDER_TIME_OFFSET + index].toLong() and 0xff)
        return (raw ushr 32) * 1_000_000_000L + ((raw and 0xffff_ffffL) * 1_000_000_000L ushr 32)
    }

    private const val SENDER_TIME_OFFSET = 8

    internal fun lengthPrefixedToAnnexB(payload: ByteArray, length: Int, lengthSize: Int): ByteArray {
        if (length !in 0..payload.size || length == 0) return ByteArray(0)
        // Honor the negotiated NAL length before interpreting 00 00 01 as a start code:
        // a valid four-byte length of 256..511 has exactly that prefix.
        if (lengthSize == 4 && MediaCodecSupport.toAnnexBInPlace(payload, length)) return payload.copyOf(length)
        val frame = payload.copyOf(length)
        val converted = MediaCodecSupport.toAnnexB(frame, lengthSize, allowAnnexB = false)
        // One/two-byte lengths expand; the result need not fit the receive pool's bucket.
        // Keep Annex-B or malformed input unchanged, as the .25 decoder path did.
        return converted.takeIf { it.isNotEmpty() } ?: frame
    }

    fun decryptFrame(key: ByteArray, counter: Long, header: ByteArray, body: ByteArray): ByteArray =
        if (body.size < TAG_SIZE) body
        else AirPlayCrypto.chachaOpen(key, AirPlayCrypto.nonce64(counter), body, header)

    /**
     * Replaces each four-byte NAL length with an Annex B start code in place.
     *
     * The payload is left untouched unless every length-prefixed NAL is valid, so malformed
     * input keeps its original bytes for the normal decoder error path.
     */
    fun lengthPrefixedToAnnexB(payload: ByteArray): ByteArray {
        if (payload.size < 4 || payload.startsWithStartCode()) return payload

        var offset = 0
        while (offset + 4 <= payload.size) {
            val length = readU32Be(payload, offset)
            offset += 4
            if (length <= 0 || offset + length > payload.size) return payload
            offset += length
        }
        if (offset != payload.size) return payload

        offset = 0
        while (offset + 4 <= payload.size) {
            val length = readU32Be(payload, offset)
            payload[offset] = 0
            payload[offset + 1] = 0
            payload[offset + 2] = 0
            payload[offset + 3] = 1
            offset += 4 + length
        }
        return payload
    }

    fun detectConfig(payload: ByteArray): Pair<VideoCodec, ByteArray> {
        for (index in 4..payload.size - 4) {
            val fourcc = String(payload, index, 4, Charsets.US_ASCII)
            when (fourcc) {
                "hvcC" -> return VideoCodec.H265 to payload.copyOfRange(index + 4, payload.size)
                "avcC" -> return VideoCodec.H264 to payload.copyOfRange(index + 4, payload.size)
            }
        }
        return if (looksLikeAvcC(payload)) VideoCodec.H264 to payload else VideoCodec.H265 to payload
    }

    private fun looksLikeAvcC(payload: ByteArray): Boolean {
        if (payload.size < 9) return false
        if ((payload[5].toInt() and 0x1f) < 1) return false
        val spsLength = readU16Be(payload, 6)
        if (8 + spsLength > payload.size) return false
        return (payload[8].toInt() and 0x1f) == 7
    }

    private fun readU16Be(source: ByteArray, offset: Int): Int =
        ((source[offset].toInt() and 0xff) shl 8) or (source[offset + 1].toInt() and 0xff)

    const val TAG_SIZE = 16
}

private fun ByteArray.startsWithStartCode(): Boolean =
    size >= 4 &&
        this[0] == 0.toByte() &&
        this[1] == 0.toByte() &&
        this[2] == 0.toByte() &&
        this[3] == 1.toByte()

private fun readU32Be(source: ByteArray, offset: Int): Int =
    ((source[offset].toInt() and 0xff) shl 24) or
        ((source[offset + 1].toInt() and 0xff) shl 16) or
        ((source[offset + 2].toInt() and 0xff) shl 8) or
        (source[offset + 3].toInt() and 0xff)

private fun readU32Le(source: ByteArray, offset: Int): Int =
    (source[offset].toInt() and 0xff) or
        ((source[offset + 1].toInt() and 0xff) shl 8) or
        ((source[offset + 2].toInt() and 0xff) shl 16) or
        ((source[offset + 3].toInt() and 0xff) shl 24)

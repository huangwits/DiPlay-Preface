package com.shilapi.xcertplay.media

import com.shilapi.xcertplay.airplay.AudioCodecKind
import com.shilapi.xcertplay.airplay.AudioFormat
import com.shilapi.xcertplay.airplay.AudioStreamId
import org.junit.Assert.*
import org.junit.Test
import java.io.Closeable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class AndroidMediaSinkAudioCloseTest {
    private val id = AudioStreamId(100, "telephony")
    private val format = AudioFormat(AudioCodecKind.LPCM, 48_000, 1, 100, "telephony")

    @Test fun callbacksAfterCloseDoNotRecreatePlayersOrCallState() {
        val sink = AndroidMediaSink(callEchoCancellation = true)
        try {
            sink.close()
            sink.onAudioStarted(id, format, 0)
            sink.onAudioRtp(id, format, byteArrayOf(), 0)
            assertEmptyAudioState(sink)
        } finally { clean(sink) }
    }

    @Test fun startWaitingBehindCloseCannotReviveAudio() = raceClose { sink ->
        sink.onAudioStarted(id, format, 0)
    }

    @Test fun packetWaitingBehindCloseCannotReviveAudio() = raceClose { sink ->
        sink.onAudioRtp(id, format, byteArrayOf(), 0)
    }

    private fun raceClose(callback: (AndroidMediaSink) -> Unit) {
        val sink = AndroidMediaSink(callEchoCancellation = true)
        val errors = ConcurrentLinkedQueue<Throwable>()
        var worker: Thread? = null
        try {
            synchronized(sink) {
                worker = thread(name = "late-audio-test", isDaemon = true) {
                    try { callback(sink) } catch (error: Throwable) { errors.add(error) }
                }
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
                while (worker!!.state != Thread.State.BLOCKED && worker!!.isAlive && System.nanoTime() < deadline) {
                    Thread.yield()
                }
                assertEquals("Callback must contend with resource shutdown", Thread.State.BLOCKED, worker!!.state)
                sink.close()
            }
            worker!!.join(3_000)
            assertFalse("Callback did not finish", worker!!.isAlive)
            assertTrue(errors.toString(), errors.isEmpty())
            assertEmptyAudioState(sink)
        } finally {
            worker?.join(3_000)
            clean(sink)
        }
    }

    private fun field(sink: AndroidMediaSink, name: String): Any =
        AndroidMediaSink::class.java.getDeclaredField(name).apply { isAccessible = true }.get(sink)

    private fun assertEmptyAudioState(sink: AndroidMediaSink) {
        assertTrue("No player may survive closure", (field(sink, "audioRenderers") as Map<*, *>).isEmpty())
        assertTrue("No echo reference may survive closure", (field(sink, "callEchoReferences") as Map<*, *>).isEmpty())
        assertTrue("No call may restart after closure", (field(sink, "telephonyAudioTypes") as Set<*>).isEmpty())
    }

    private fun clean(sink: AndroidMediaSink) {
        sink.close()
        // Also release resources when this regression runs against the unfixed implementation.
        (field(sink, "audioRenderers") as MutableMap<*, *>).let { players ->
            players.values.toList().forEach { (it as Closeable).close() }
            players.clear()
        }
    }
}

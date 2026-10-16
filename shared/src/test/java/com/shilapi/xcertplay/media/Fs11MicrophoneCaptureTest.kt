package com.shilapi.xcertplay.media

import android.Manifest
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import com.shilapi.xcertplay.airplay.AirPlayCrypto
import com.shilapi.xcertplay.airplay.MicrophoneConfig
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAudioRecord
import org.robolectric.shadows.ShadowBuild
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], manifest = Config.NONE)
class Fs11MicrophoneCaptureTest {
    @Test fun legacyFs11UsesTheCabinMicAndCountsPcm16SamplesBeforeSealingOneFrame() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.RECORD_AUDIO)
        ShadowBuild.setDevice("FS11GQJ")
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 22)
        val reports = CopyOnWriteArrayList<String>()
        val reads = AtomicInteger()
        var source = -1
        ShadowAudioRecord.setSourceProvider { record ->
            source = record.audioSource
            object : ShadowAudioRecord.AudioRecordSource {
                override fun readInByteArray(buffer: ByteArray, offset: Int, size: Int, blocking: Boolean): Int {
                    throw AssertionError("FS11 speech must read PCM16 samples")
                }
                override fun readInShortArray(buffer: ShortArray, offset: Int, size: Int, blocking: Boolean): Int {
                    if (reads.incrementAndGet() > 2) { Thread.sleep(5); return 0 }
                    // Two half frames must produce one packet, preserving signed sample values.
                    repeat(480) { buffer[offset + it] = 0x9234.toShort() }
                    return 480
                }
            }
        }
        var uplink: MicrophoneUplink? = null
        try {
            DatagramSocket(0, InetAddress.getLoopbackAddress()).use { receiver ->
                receiver.soTimeout = 5000
                val key = ByteArray(32) { it.toByte() }
                val config = MicrophoneConfig("speechrecognition", 48_000, 1, 100, 20,
                    InetAddress.getLoopbackAddress(), receiver.localPort, key)
                uplink = MicrophoneUplink(config, onDiagnostic = reports::add)
                assertTrue(uplink!!.start())
                val packet = DatagramPacket(ByteArray(4096), 4096)
                receiver.receive(packet)
                val data = packet.data.copyOf(packet.length)
                val sealedEnd = data.size - 8
                val nonce = ByteArray(12).also { data.copyInto(it, 4, sealedEnd, data.size) }
                val body = AirPlayCrypto.chachaOpen(key, nonce, data.copyOfRange(12, sealedEnd), data.copyOfRange(4, 12))
                assertEquals(1920, body.size)
                repeat(960) {
                    assertEquals(0x92.toByte(), body[it * 2])
                    assertEquals(0x34.toByte(), body[it * 2 + 1])
                }
                assertEquals(MediaRecorder.AudioSource.MIC, source)
                assertTrue(reports.any { it.contains("readMode=pcm16") })
                uplink!!.close()
                assertTrue(reports.any { it.contains("source=MIC") && it.contains("captureBytes=1920") && it.contains("udpSent=1") })
            }
        } finally {
            uplink?.close()
            ShadowAudioRecord.clearSource()
            ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 23)
        }
    }
}

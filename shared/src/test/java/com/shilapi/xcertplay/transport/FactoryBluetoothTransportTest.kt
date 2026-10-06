package com.shilapi.xcertplay.transport

import android.os.Binder
import android.os.Parcel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], manifest = Config.NONE)
class FactoryBluetoothTransportTest {
    @Test fun mismatchedServiceNeverReceivesVendorTransactions() {
        val calls = AtomicInteger()
        val binder = object : Binder() {
            init { attachInterface(null, "some.other.bluetooth.Service") }
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                calls.incrementAndGet()
                return false
            }
        }
        try { AnwSppProtocol(binder).power(); fail("Expected rejection") } catch (_: IOException) { }
        assertEquals(0, calls.get())
    }

    @Test fun closingDuringConnectionReleasesLateResultAndNeverPublishesIt() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val closes = AtomicInteger()
        val failures = AtomicInteger()
        val stream = DeferredBluetoothStream { cancelled ->
            entered.countDown()
            check(release.await(2, TimeUnit.SECONDS))
            check(cancelled())
            object : BlockingDuplexByteStream {
                override fun send(data: ByteArray) { fail("Late result must not be used") }
                override fun recv(maxBytes: Int, timeoutMillis: Long): ByteArray? = null
                override fun close() { closes.incrementAndGet() }
            }
        }
        Thread {
            try { stream.connect() } catch (_: IOException) { failures.incrementAndGet() }
            finally { finished.countDown() }
        }.start()
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        stream.close()
        release.countDown()
        assertTrue(finished.await(2, TimeUnit.SECONDS))
        stream.close()
        assertEquals(1, closes.get())
        assertEquals(1, failures.get())
        assertArrayEquals(byteArrayOf(), stream.recv(10, 0))
    }

    @Test fun closingBeforeConnectionDoesNotTouchTheVendor() {
        val stream = DeferredBluetoothStream { error("Must not call vendor") }
        stream.close()
        try { stream.connect(); fail("Expected closed") } catch (_: IOException) { }
    }
}

package com.shilapi.xcertplay.transport

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.*
import org.junit.Test

class GocSppTransportTest {
    @Test fun deniedLocalSocketClosesWithoutRetry() {
        var closes = 0
        try {
            GocSppTransport.connectSocket("00:11:22:33:44:55", { false }, {}, {
                object : GocSocketAccess {
                    override fun connect() { throw SecurityException("denied") }
                    override fun inputStream(): InputStream = error("not connected")
                    override fun outputStream(): OutputStream = error("not connected")
                    override fun close() { closes++ }
                }
            }, { error("Permission errors must not retry") })
            fail()
        } catch (_: SecurityException) { }
        assertEquals(1, closes)
    }

    @Test fun sendsHeaderBeforeProtocolBytesAndRetriesOnlyConnectionSetup() {
        var attempts = 0
        var failedCloses = 0
        val output = ByteArrayOutputStream()
        val finish = java.util.concurrent.CountDownLatch(1)
        val input = object : InputStream() {
            override fun read(): Int { finish.await(); return -1 }
        }
        val stream = GocSppTransport.connectSocket("00:1a:2b:3c:4d:ff", { false }, {}, {
            val attempt = ++attempts
            object : GocSocketAccess {
                override fun connect() { if (attempt < 3) throw java.io.IOException("not listening") }
                override fun inputStream(): InputStream = input
                override fun outputStream(): OutputStream = output
                override fun close() { if (attempt < 3) failedCloses++ else finish.countDown() }
            }
        }, {})
        stream.send(byteArrayOf(0xff.toByte(), 0x5a))
        stream.close()
        assertEquals(3, attempts)
        assertEquals(2, failedCloses)
        assertArrayEquals("001A2B3C4DFF".toByteArray(Charsets.US_ASCII) + byteArrayOf(0xff.toByte(), 0x5a), output.toByteArray())
    }

    @Test fun partialHeaderFailureClosesAndDoesNotRetry() {
        var attempts = 0
        var closes = 0
        try {
            GocSppTransport.connectSocket("00:11:22:33:44:55", { false }, {}, {
                attempts++
                object : GocSocketAccess {
                    override fun connect() { }
                    override fun inputStream(): InputStream = error("Reader must not start")
                    override fun outputStream(): OutputStream = object : OutputStream() {
                        override fun write(value: Int) { throw java.io.IOException("write failed") }
                    }
                    override fun close() { closes++ }
                }
            }, {})
            fail()
        } catch (_: java.io.IOException) { }
        assertEquals(1, attempts); assertEquals(1, closes)
    }

    @Test fun headerIsExactlyTwelveUppercaseAsciiBytes() {
        assertArrayEquals("001A2B3C4DFF".toByteArray(Charsets.US_ASCII), GocSppTransport.addressHeader("00:1a:2b:3c:4d:ff"))
        for (bad in listOf("", "01:02:03:04:05", "01:02:03:04:05:zz", "01:02:03:04:05:06\n")) {
            try { GocSppTransport.addressHeader(bad); fail(bad) } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun gocBytesAreOrderedAndEofClosesOnlyOnce() {
        val bytes = ByteArray(5000) { (it % 251).toByte() }
        val socket = object : BluetoothRfcommSocketAccess {
            val input = ByteArrayInputStream(bytes)
            val output = ByteArrayOutputStream()
            var closes = 0
            override fun inputStream(): InputStream = input
            override fun outputStream(): OutputStream = output
            override fun close() { closes++ }
        }
        val logs = CopyOnWriteArrayList<String>()
        val stream = GocSppTransport.attach(socket, logs::add)
        val received = ByteArrayOutputStream()
        while (true) {
            val chunk = stream.recv(137, 1000) ?: error("Unexpected timeout")
            if (chunk.isEmpty()) break
            received.write(chunk)
        }
        stream.close(); stream.close()
        assertArrayEquals(bytes, received.toByteArray())
        assertEquals(1, socket.closes)
        assertTrue(logs.all { !it.contains("RFCOMM") })
        assertTrue(logs.any { it.contains("GOC SPP") })
    }

    @Test fun cancelledConnectDoesNotCreatePlatformSocket() {
        try { GocSppTransport.connect("00:11:22:33:44:55", { true }); fail() }
        catch (expected: java.io.IOException) { assertTrue(expected.message!!.contains("cancelled")) }
    }
}

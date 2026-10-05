package com.shilapi.xcertplay.transport

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class FactorySppDuplexStreamTest {
    private val address = "AA:BB:CC:DD:EE:01"
    private fun direct() = FactorySdkCalls { it.run(); true }
    private fun failure(code: String, action: () -> Unit) {
        try { action(); fail("Expected $code") }
        catch (error: FactoryBluetoothException) { assertEquals(code, error.code) }
    }

    @Test fun binaryWritesPreserveAllBytesAndOrderAcrossChunks() {
        val api = FakeApi()
        val stream = FactorySppDuplexStream(address, api, direct())
        stream.connect()
        val bytes = ByteArray(3_001) { it.toByte() }
        stream.send(bytes)
        assertArrayEquals(bytes, api.sent.fold(ByteArray(0)) { all, next -> all + next })
        assertEquals(listOf(1024, 1024, 953), api.sent.map { it.size })
        stream.close()
        assertEquals(1, api.disconnections)
        assertEquals(1, api.unregistrations)
    }

    @Test fun receivesOnlySelectedPeerCopiesCallbackBufferAndSupportsPartialReads() {
        val api = FakeApi()
        val stream = FactorySppDuplexStream(address, api, direct())
        stream.connect()
        api.event("onSppDataReceived", "AA:BB:CC:DD:EE:02", byteArrayOf(99))
        assertNull(stream.recv(100, 0))
        val payload = byteArrayOf(0, -1, 1, 127, -128)
        api.event("onSppDataReceived", address.lowercase(), payload)
        payload.fill(42)
        assertArrayEquals(byteArrayOf(0, -1), stream.recv(2, 0))
        assertArrayEquals(byteArrayOf(1, 127, -128), stream.recv(8, 0))
        assertNull(stream.recv(8, 0))
        stream.close()
    }

    @Test fun preexistingSppConnectionIsNeverTakenOverOrDisconnected() {
        val api = FakeApi().apply { isConnected = true }
        val stream = FactorySppDuplexStream(address, api, direct())
        failure("E01-F05") { stream.connect() }
        assertEquals(0, api.requests)
        assertEquals(0, api.disconnections)
        assertEquals(0, api.unregistrations)
    }

    @Test fun refusedRegistrationIsUnregisteredWithoutConnecting() {
        val api = FakeApi().apply { registerAccepted = false }
        val stream = FactorySppDuplexStream(address, api, direct())
        failure("E01-F05") { stream.connect() }
        assertEquals(0, api.requests)
        assertEquals(0, api.disconnections)
        assertEquals(1, api.unregistrations)
    }

    @Test fun refusedRequestReleasesOnlyAttemptedSppSession() {
        val api = FakeApi().apply { connectAccepted = false }
        failure("E01-F06") { FactorySppDuplexStream(address, api, direct()).connect() }
        assertEquals(1, api.disconnections)
        assertEquals(1, api.unregistrations)
    }

    @Test fun unknownStateNumbersDoNotReplaceConnectedQuery() {
        val api = FakeApi().apply {
            connectImmediately = false
            duringConnect = { event("onSppStateChanged", address, "iPhone", 0, 2) }
        }
        failure("E01-F06") { FactorySppDuplexStream(address, api, direct()).connect(10) }
        assertEquals(1, api.disconnections)
    }

    @Test fun disconnectedStateRechecksBooleanInsteadOfGuessingNumber() {
        val api = FakeApi()
        val stream = FactorySppDuplexStream(address, api, direct())
        stream.connect()
        api.event("onSppStateChanged", address, "iPhone", 3, 0)
        assertNull(stream.recv(1, 0)) // Boolean still connected despite state number zero.
        api.isConnected = false
        api.event("onSppStateChanged", address, "iPhone", 0, 99)
        failure("E01-F07") { stream.recv(1, 0) }
        stream.close()
    }

    @Test fun explicitErrorAndOverflowFailWithoutBlockingCallback() {
        val api = FakeApi()
        val stream = FactorySppDuplexStream(address, api, direct())
        stream.connect()
        api.event("onSppErrorResponse", address, 17)
        failure("E01-F07") { stream.send(byteArrayOf(1)) }
        stream.close()
        val second = FactorySppDuplexStream(address, api, direct())
        second.connect()
        api.event("onSppDataReceived", address, ByteArray(262_145))
        failure("E01-F07") { second.recv(1, 0) }
        second.close()
    }

    @Test fun repeatedCloseAndLateCallbacksCannotResurrectStream() {
        val api = FakeApi()
        val stream = FactorySppDuplexStream(address, api, direct())
        stream.connect()
        stream.close(); stream.close()
        api.event("onSppDataReceived", address, byteArrayOf(1))
        assertArrayEquals(ByteArray(0), stream.recv(1, 0))
        assertEquals(1, api.disconnections)
        assertEquals(1, api.unregistrations)
    }

    @Test fun cancellingWhileConnectIsInFlightCleansUpAfterLateReturn() {
        val executor = Executors.newSingleThreadExecutor()
        val calls = FactorySdkCalls { executor.execute(it); true }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val api = FakeApi().apply { duringConnect = { entered.countDown(); release.await(2, TimeUnit.SECONDS) } }
        val stream = FactorySppDuplexStream(address, api, calls)
        val finished = CountDownLatch(1)
        val connector = Thread { try { stream.connect() } catch (_: FactoryBluetoothException) { } finally { finished.countDown() } }
        try {
            connector.start()
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            stream.close()
            release.countDown()
            assertTrue(finished.await(2, TimeUnit.SECONDS))
            val drained = CountDownLatch(1)
            calls.cleanup { drained.countDown() }
            assertTrue(drained.await(2, TimeUnit.SECONDS))
            assertFalse(api.isConnected)
            assertEquals(1, api.disconnections)
            assertEquals(1, api.unregistrations)
        } finally { release.countDown(); executor.shutdownNow(); connector.join(2_000) }
    }

    @Test fun hungSdkRejectsFurtherCallsButRunsCleanupAfterItReturns() {
        val executor = Executors.newSingleThreadExecutor()
        val calls = FactorySdkCalls { executor.execute(it); true }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val clean = CountDownLatch(1)
        val extraCalls = AtomicInteger()
        try {
            failure("E01-F09") { calls.call("E01-F06", 30) { entered.countDown(); release.await() } }
            assertEquals(0L, entered.count)
            failure("E01-F09") { calls.call("E01-F06") { extraCalls.incrementAndGet() } }
            calls.cleanup { clean.countDown() }
            release.countDown()
            assertTrue(clean.await(2, TimeUnit.SECONDS))
            assertEquals(0, extraCalls.get())
        } finally { release.countDown(); executor.shutdownNow() }
    }

    @Test fun reflectionUsesSdkCallbacksAndActualBinarySendSignature() {
        val sdk = TestSdk()
        val api = ReflectiveSppApi(EcarxSppTransport.Endpoint(TestSdkApi::class.java, sdk, "E01-F02"))
        val stream = FactorySppDuplexStream(address, api, direct())
        stream.connect()
        assertTrue(sdk.listener!!.equals(sdk.listener))
        stream.send(byteArrayOf(0, -1, 3))
        assertArrayEquals(byteArrayOf(0, -1, 3), sdk.sent)
        sdk.listener!!.onSppDataReceived(address, byteArrayOf(-128, 0))
        assertArrayEquals(byteArrayOf(-128, 0), stream.recv(8, 0))
        stream.close()
        assertNull(sdk.listener)
        assertFalse(sdk.up)
    }

    private class FakeApi : FactorySppApi {
        var isConnected = false
        var registerAccepted = true
        var connectAccepted = true
        var connectImmediately = true
        var duringConnect: (() -> Unit)? = null
        var requests = 0
        var disconnections = 0
        var unregistrations = 0
        val sent = mutableListOf<ByteArray>()
        var callback: ((String, Array<out Any?>) -> Unit)? = null
        fun event(name: String, vararg args: Any?) { callback?.invoke(name, args) }
        override fun ready() = true
        override fun connected(address: String) = isConnected
        override fun register(callback: (String, Array<out Any?>) -> Unit): Boolean { this.callback = callback; return registerAccepted }
        override fun unregister() { unregistrations++ }
        override fun connect(address: String): Boolean {
            requests++; duringConnect?.invoke()
            if (connectAccepted && connectImmediately) isConnected = true
            return connectAccepted
        }
        override fun disconnect(address: String) { disconnections++; isConnected = false }
        override fun send(address: String, data: ByteArray) { sent.add(data.copyOf()) }
    }

    interface TestCallback {
        fun onSppDataReceived(address: String, data: ByteArray)
        fun onSppStateChanged(address: String, name: String, before: Int, after: Int)
    }
    interface TestSdkApi {
        fun isSppServiceReady(): Boolean
        fun isSppConnected(address: String): Boolean
        fun registerSppCallback(callback: TestCallback): Boolean
        fun unregisterSppCallback(callback: TestCallback): Boolean
        fun reqSppConnect(address: String): Boolean
        fun reqSppDisconnect(address: String): Boolean
        fun reqSppSendData(address: String, data: ByteArray)
    }
    class TestSdk : TestSdkApi {
        var up = false
        var listener: TestCallback? = null
        var sent: ByteArray? = null
        override fun isSppServiceReady() = true
        override fun isSppConnected(address: String) = up
        override fun registerSppCallback(callback: TestCallback): Boolean { listener = callback; return true }
        override fun unregisterSppCallback(callback: TestCallback): Boolean { assertSame(listener, callback); listener = null; return true }
        override fun reqSppConnect(address: String): Boolean { up = true; return true }
        override fun reqSppDisconnect(address: String): Boolean { up = false; return true }
        override fun reqSppSendData(address: String, data: ByteArray) { sent = data.copyOf() }
    }
}

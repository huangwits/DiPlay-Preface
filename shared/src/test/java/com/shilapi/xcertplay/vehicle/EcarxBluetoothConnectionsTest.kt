package com.shilapi.xcertplay.vehicle

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class EcarxBluetoothConnectionsTest {
    private val phone = "AA:BB:CC:DD:EE:01"

    @Test fun onlyBondedValidAddressesAreAccepted() {
        assertEquals(setOf(phone), vendorConnectedBondedAddresses(
            listOf(phone.lowercase(), "00:00:00:00:00:00"),
            setOf(phone, "AA:BB:CC:DD:EE:02", "00:00:00:00:00:00")))
        for (bad in listOf(null, "", "02:00:00:00:00:00", "FF:FF:FF:FF:FF:FF", "bad", "$phone ")) {
            assertNull(normalizeBluetoothAddress(bad))
        }
    }

    @Test fun multipleConnectedPhonesRemainAmbiguous() {
        val other = "AA:BB:CC:DD:EE:02"
        assertEquals(2, vendorConnectedBondedAddresses(listOf(phone, other), setOf(phone, other)).size)
    }

    @Test fun unavailableServiceFallsBack() {
        assertTrue(BoundedBluetoothQuery { throw SecurityException("denied") }.snapshot().isEmpty())
        assertTrue(BoundedBluetoothQuery { throw ClassNotFoundException("vendor API") }.snapshot().isEmpty())
    }

    @Test fun successfulQueriesRefresh() {
        var connected = setOf(phone.lowercase())
        val query = BoundedBluetoothQuery { connected }
        assertEquals(setOf(phone), query.snapshot())
        connected = emptySet()
        assertTrue(query.snapshot().isEmpty())
    }

    @Test fun stalledServiceDoesNotAccumulateThreads() {
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        val query = BoundedBluetoothQuery(waitMillis = 20) {
            calls.incrementAndGet()
            release.await()
            setOf(phone)
        }
        try {
            repeat(3) { assertTrue(query.snapshot().isEmpty()) }
            assertEquals(1, calls.get())
        } finally { release.countDown() }
    }

    @Test fun interruptionIsPreserved() {
        val release = CountDownLatch(1)
        val query = BoundedBluetoothQuery { release.await(); emptySet() }
        try {
            Thread.currentThread().interrupt()
            assertTrue(query.snapshot().isEmpty())
            assertTrue(Thread.currentThread().isInterrupted)
        } finally { Thread.interrupted(); release.countDown() }
    }
}

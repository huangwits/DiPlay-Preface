package com.shilapi.xcertplay.network

import org.junit.Assert.*
import org.junit.Test

class E01ConnectedPhonesTest {
    @Test fun requiresBothConnectedProfilesAndDeduplicatesAddresses() {
        val first = E01Phone("00:11:22:33:44:55", "")
        val other = E01Phone("10:11:22:33:44:55", "Other")
        assertEquals(listOf(first.copy(name = "iPhone")), E01ConnectedPhones.intersectProfiles(
            listOf(first, first, other), listOf(first.copy(name = "iPhone"))))
        assertTrue(E01ConnectedPhones.intersectProfiles(listOf(first), emptyList()).isEmpty())
    }
}

package com.shilapi.xcertplay.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformTetheringStartTest {
    @Test
    fun appOwnedTetheringIsLimitedToAndroid8Through10() {
        assertFalse(PlatformTetheringStart.supported(22))
        assertFalse(PlatformTetheringStart.supported(25))
        assertTrue(PlatformTetheringStart.supported(26))
        assertTrue(PlatformTetheringStart.supported(27))
        assertTrue(PlatformTetheringStart.supported(28))
        assertTrue(PlatformTetheringStart.supported(29))
        assertFalse(PlatformTetheringStart.supported(30))
        assertFalse(PlatformTetheringStart.supported(34))
    }

    @Test
    fun hiddenApiExemptionsStayScopedToTheTetheringPath() {
        assertEquals(
            listOf(
                "Landroid/net/IConnectivityManager",
                "Landroid/net/ConnectivityManager;->mService",
                "Landroid/os/ServiceManager;",
            ),
            PlatformTetheringStart.HIDDEN_API_EXEMPTIONS.toList(),
        )
    }
}

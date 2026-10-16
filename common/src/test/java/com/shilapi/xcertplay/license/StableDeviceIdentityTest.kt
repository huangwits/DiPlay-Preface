package com.shilapi.xcertplay.license

import com.geelyplay.preface.license.StableDeviceIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StableDeviceIdentityTest {
    private val packageName = "com.shihab.diplay.preface"

    @Test fun sameSystemDeviceIdProducesSameCodeAcrossInstallations() {
        val firstInstall = StableDeviceIdentity.id(null, "0123456789abcdef", packageName)
        val reinstall = StableDeviceIdentity.id(null, "0123456789ABCDEF", packageName)

        assertEquals(firstInstall, reinstall)
        assertEquals(64, firstInstall.length)
    }

    @Test fun hardwareSerialKeepsTheCodeAcrossFactoryReset() {
        val beforeReset = StableDeviceIdentity.id("fs11g-serial-123", "old-android-id", packageName)
        val afterReset = StableDeviceIdentity.id("FS11G-SERIAL-123", "new-android-id", packageName)

        assertEquals(beforeReset, afterReset)
    }

    @Test fun differentDevicesAndPackagesHaveDifferentCodes() {
        val id = StableDeviceIdentity.id(null, "0123456789abcdef", packageName)

        assertNotEquals(id, StableDeviceIdentity.id(null, "fedcba9876543210", packageName))
        assertNotEquals(id, StableDeviceIdentity.id(null, "0123456789abcdef", "com.example.other"))
    }

    @Test fun missingOrKnownBrokenAndroidIdIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { StableDeviceIdentity.id(null, null, packageName) }
        assertThrows(IllegalArgumentException::class.java) {
            StableDeviceIdentity.id(null, "9774d56d682e549c", packageName)
        }
    }
}

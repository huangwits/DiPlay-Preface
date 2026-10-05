package com.shilapi.xcertplay.network

import android.content.ContextWrapper
import android.os.IInterface
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 28, 32], manifest = Config.NONE)
class HotspotJoinLegacyTest {
    @Test fun unsupportedAndroidNeverAccessesContextAdbOrVendorCallbacks() {
        // A context without a base fails if the repair tries to reach files, keys or ADB.
        for (action in HotspotJoinRepair.Action.entries) {
            assertEquals(HotspotJoinRepair.Code.UNSUPPORTED,
                HotspotJoinRepair().run(ContextWrapper(null), action).code)
        }
        val registration = object : HotspotJoinCapability.Registration {
            override fun register(callback: IInterface) { fail("Unsupported vendor registration") }
            override fun unregister(callback: IInterface) { fail("No registration was owned") }
        }
        assertNull(HotspotJoinCapability.read(registration, 1))
    }
}

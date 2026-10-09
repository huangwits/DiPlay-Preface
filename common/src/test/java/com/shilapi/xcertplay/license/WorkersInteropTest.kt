package com.shilapi.xcertplay.license

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Base64
import org.json.JSONObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Optional live-workerd fixture is generated outside source using synthetic keys. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class WorkersInteropTest {
    @Test fun acceptsActualWorkerLeaseAndRejectsReplayAndOtherDevice() {
        val path = System.getenv("DIPLAY_WORKERS_VECTOR")
        assumeTrue("Run Workers tests with DIPLAY_WORKERS_VECTOR first", !path.isNullOrEmpty())
        val value = JSONObject(File(path!!).readText())
        fun bytes(name: String) = Base64.getDecoder().decode(value.getString(name))
        fun verify(device: String = value.getString("device"), challenge: String = value.getString("challenge")) =
            LicenseProtocol.verify(bytes("payload"), bytes("signature"), bytes("issuerPublic"),
                device, value.getString("package"), value.getString("signer"), challenge)
        assertEquals(300000, verify())
        assertThrows(Exception::class.java) { verify(device = "f".repeat(64)) }
        assertThrows(Exception::class.java) { verify(challenge = "f".repeat(48)) }
        val changed = bytes("payload").apply { this[0] = 'X'.code.toByte() }
        assertThrows(Exception::class.java) {
            LicenseProtocol.verify(changed, bytes("signature"), bytes("issuerPublic"), value.getString("device"),
                value.getString("package"), value.getString("signer"), value.getString("challenge"))
        }
    }
}

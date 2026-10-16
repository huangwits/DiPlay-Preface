package com.shilapi.xcertplay.e01goc

import org.junit.Assert.*
import org.junit.Test
import com.geelyplay.preface.e01goc.E01GocManager
import com.geelyplay.preface.e01goc.E01QuickSetup
import com.geelyplay.preface.e01goc.GocSnapshot

class E01QuickSetupTest {
    private val original = "a".repeat(64)
    private var installed = false
    private var cancelled = false
    private var busy = false
    private var listener = true
    private val events = mutableListOf<String>()
    private fun flow(test: () -> Unit = {}, admit: () -> Unit = {}) = E01QuickSetup(
        inspect = {
            events += "inspect"
            GocSnapshot(if (installed) E01GocManager.CANDIDATE_SHA else original,
                original, original, "running", listener, if (busy) "busy" else "idle")
        },
        test = { events += "test:$it"; test() },
        install = { events += "install"; installed = true },
        admission = { events += "admission"; admit() },
        cancelled = { cancelled },
    )

    @Test fun installedComponentSkipsMaintenanceEvenWithoutInstallationConsent() {
        installed = true
        flow().prepare("phone", false)
        assertEquals(listOf("inspect"), events)
    }

    @Test fun firstSetupTestsThenRechecksAdmissionAndInstallsOnce() {
        flow().prepare("phone", true)
        assertEquals(listOf("inspect", "admission", "test:phone", "admission", "install", "inspect"), events)
        assertTrue(installed)
    }

    @Test fun noConsentNeverStartsTheTestOrInstallation() {
        assertThrows(IllegalStateException::class.java) { flow().prepare("phone", false) }
        assertEquals(listOf("inspect"), events)
    }

    @Test fun failedTestOrUnconfirmedRestorationCannotInstall() {
        assertThrows(IllegalStateException::class.java) { flow(test = { error("restoration unconfirmed") }).prepare("phone", true) }
        assertFalse("install" in events)
    }

    @Test fun cancellingAfterRestorationStopsBeforeInstallation() {
        assertThrows(IllegalStateException::class.java) { flow(test = { cancelled = true }).prepare("phone", true) }
        assertFalse("install" in events)
        assertEquals(1, events.count { it == "admission" })
    }

    @Test fun lostAdmissionAfterTestStopsInstallation() {
        var checks = 0
        assertThrows(IllegalStateException::class.java) {
            flow(admit = { if (++checks == 2) error("revoked") }).prepare("phone", true)
        }
        assertFalse("install" in events)
    }

    @Test fun busyMaintenanceAndBrokenInstalledServiceNeverTriggerReplacement() {
        busy = true
        assertThrows(IllegalStateException::class.java) { flow().prepare("phone", true) }
        busy = false; installed = true; listener = false
        assertThrows(IllegalStateException::class.java) { flow().prepare("phone", true) }
        assertFalse("install" in events)
        assertFalse(events.any { it.startsWith("test:") })
    }
}

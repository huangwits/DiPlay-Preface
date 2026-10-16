package com.shilapi.xcertplay

import android.content.pm.PackageManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class VehicleScannerRemovalTest {
    @Test fun mergedApplicationHasNoScannerActivityOrReportUploadService() {
        val app = RuntimeEnvironment.getApplication()
        val info = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES)
        val components = info.activities.orEmpty().map { it.name } + info.services.orEmpty().map { it.name }
        for (name in listOf("VehicleProbeActivity", "ProbeService", "VehicleReportUploadService")) {
            assertFalse(name, components.any { it.endsWith(".$name") })
            assertThrows(ClassNotFoundException::class.java) { Class.forName("com.shilapi.xcertplay.vehicleprobe.$name") }
        }
        assertTrue(components.any { it.endsWith(".VehicleMapProjectionActivity") })
    }

    @Test fun settingsAndResourcesNoLongerExposeScanning() {
        assertFalse(SettingsSection.entries.any { it.name == "VEHICLE_PROBE" })
        val app = RuntimeEnvironment.getApplication()
        assertEquals(0, app.resources.getIdentifier("vehicle_probe", "string", app.packageName))
    }
}

package com.shilapi.xcertplay

import android.content.Context
import android.os.Build
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], manifest = Config.NONE)
class VehicleScanMetadataTest {
    private val raw = "schema=geely_property_probe_v2\nchain_id,kind,area_id,property_name,property_id,property_hex,\n\"raw\",\"row\"\n"

    @Test fun android51MetadataPreservesImportRowsAndDoesNotInventAModel() {
        val previous = Build.VERSION.SDK_INT
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 22)
        try {
            val text = format(raw, null)
            assertTrue(text.contains("API 22"))
            assertTrue(text.contains("Vehicle model: unidentified / 未识别"))
            assertTrue(text.contains("Vehicle model source: unavailable"))
            assertTrue(text.endsWith(raw))
            assertEquals(1, text.lineSequence().count { it == "schema=geely_property_probe_v2" })
        } finally {
            ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", previous)
        }
    }

    @Test fun formattingAnExistingReportKeepsItsOriginalScanMetadata() {
        val first = format(raw, "星瑞\nforged-field: value")
        assertTrue(first.contains("Vehicle model: 星瑞 forged-field: value"))
        assertEquals(first, format(first, "other car"))
    }

    // Exercise the vehicle-probe module without widening its internal production API.
    private fun format(report: String, model: String?): String {
        val type = Class.forName("com.shilapi.xcertplay.vehicleprobe.VehicleScanReport")
        return type.getDeclaredMethod("format", Context::class.java, String::class.java, String::class.java, String::class.java)
            .invoke(type.getField("INSTANCE").get(null), RuntimeEnvironment.getApplication(), report, model, "saved vehicle profile") as String
    }
}

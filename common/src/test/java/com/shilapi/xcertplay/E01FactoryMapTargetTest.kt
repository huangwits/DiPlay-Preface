package com.shilapi.xcertplay

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDisplayManager
import com.geelyplay.preface.E01FactoryMapTarget

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], manifest = Config.NONE)
class E01FactoryMapTargetTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private fun config(window: Int, renderer: Int = 1) = """
        {"Screens":{"InstrumentPannelLarge":{"Surface":"fixture"}},
         "SurfaceConfigs":{"fixture":{"WindowId":$window,"SurfaceRenderType":$renderer,
         "SurfaceWidth":1920,"SurfaceHeight":720}}}
    """.trimIndent()

    @Test fun usesTheFactoryWindowMappingWithoutTheUpstreamDisplaySelection() {
        val instrument = ShadowDisplayManager.addDisplay("w1920dp-h720dp")
        val unrelated = ShadowDisplayManager.addDisplay("w800dp-h600dp")
        VehicleMapSettings.select(app, GeelyHudProjection.availableDisplays(app).single { it.id == unrelated })
        VehicleMapSettings.setEnabled(app, false)
        VehicleMapSettings.setVehicleMode(app, false)
        val plan = E01FactoryMapTarget.select(app, E01FactoryMapTarget.parse(config(instrument + 1000)))!!
        assertEquals(instrument, plan.screen.id)
        assertTrue(plan.factoryNative)
        assertTrue(plan.vehicleMode)
        assertEquals(1920, plan.width)
        assertEquals(720, plan.height)
        assertFalse(VehicleMapSettings.enabled(app))
        assertEquals(unrelated, VehicleMapSettings.screen(app)!!.id)
        ShadowDisplayManager.removeDisplay(instrument)
        assertNull(E01FactoryMapTarget.select(app, E01FactoryMapTarget.parse(config(instrument + 1000))))
    }

    @Test fun presentationIndexIsIndependentFromSparseAndroidDisplayId() {
        val discarded = ShadowDisplayManager.addDisplay("w640dp-h480dp")
        val instrument = ShadowDisplayManager.addDisplay("w1920dp-h720dp")
        ShadowDisplayManager.removeDisplay(discarded)

        val plan = E01FactoryMapTarget.select(app, E01FactoryMapTarget.parse(config(1001)))!!
        assertEquals(instrument, plan.screen.id)
        assertEquals(1920, plan.width)
        assertEquals(720, plan.height)
        assertEquals(instrument, E01FactoryMapTarget.screenByDisplayId(app, instrument)!!.id)
    }

    @Test fun neverUsesTheMainScreenOrAnUnknownFactoryRenderer() {
        assertThrows(IllegalArgumentException::class.java) { E01FactoryMapTarget.parse(config(1000)) }
        assertThrows(IllegalArgumentException::class.java) { E01FactoryMapTarget.parse(config(1001, 5)) }
        assertNull(E01FactoryMapTarget.screen(app, 0))
        assertNull(E01FactoryMapTarget.select(app, E01FactoryMapTarget.parse(config(1100))))
    }

    @Test fun readsStandardFactoryNameOnlyWhenVariantFileIsMissing() {
        val reads = mutableListOf<String>()
        val spec = E01FactoryMapTarget.readSpec { path ->
            reads += path
            if (path.endsWith("C04010181159_MultipleScreen.json")) throw java.io.FileNotFoundException()
            config(1001)
        }
        assertEquals(1, spec.displayIndex)
        assertEquals(listOf("res/HmiRes/C04010181159_MultipleScreen.json", "res/HmiRes/MultipleScreen.json"), reads)
        assertTrue(E01FactoryMapTarget.diagnostics().contains("config=installed:res/HmiRes/MultipleScreen.json"))
    }

    @Test fun validVariantWinsAndInvalidVariantNeverFallsBackOrGuesses() {
        var calls = 0
        assertEquals(2, E01FactoryMapTarget.readSpec { calls++; config(1002) }.displayIndex)
        assertEquals(1, calls)
        calls = 0
        assertThrows(IllegalArgumentException::class.java) {
            E01FactoryMapTarget.readSpec { calls++; config(1001, 5) }
        }
        assertEquals(1, calls)
        assertThrows(java.io.FileNotFoundException::class.java) {
            E01FactoryMapTarget.readSpec { throw java.io.FileNotFoundException() }
        }
    }

    @Test fun missingInstalledConfigUsesTheBundledVehicleVariantBeforeStandardConfig() {
        val installedReads = mutableListOf<String>()
        val bundledReads = mutableListOf<String>()
        val spec = E01FactoryMapTarget.readSpec(
            readInstalled = { path -> installedReads += path; throw java.io.FileNotFoundException(path) },
            readBundled = { path -> bundledReads += path; config(1001) },
        )

        assertEquals(1, spec.displayIndex)
        assertEquals(listOf("res/HmiRes/C04010181159_MultipleScreen.json"), bundledReads)
        assertTrue(E01FactoryMapTarget.diagnostics().contains("config=firmware:res/HmiRes/C04010181159_MultipleScreen.json"))
    }

    @Test fun bundledStandardConfigIsUsedOnlyWhenBothVariantFilesAreAbsent() {
        val bundledReads = mutableListOf<String>()
        val spec = E01FactoryMapTarget.readSpec(
            readInstalled = { path -> throw java.io.FileNotFoundException(path) },
            readBundled = { path ->
                bundledReads += path
                if (path.endsWith("C04010181159_MultipleScreen.json")) throw java.io.FileNotFoundException(path)
                config(1001)
            },
        )

        assertEquals(1, spec.displayIndex)
        assertEquals(listOf("res/HmiRes/C04010181159_MultipleScreen.json", "res/HmiRes/MultipleScreen.json"), bundledReads)
        assertTrue(E01FactoryMapTarget.diagnostics().contains("config=firmware:res/HmiRes/MultipleScreen.json"))
    }
}

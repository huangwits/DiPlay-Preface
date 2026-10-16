package com.geelyplay.preface

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.view.Display
import com.geelyplay.preface.compat.systemService
import com.shilapi.xcertplay.host.R
import java.io.FileNotFoundException
import org.json.JSONObject
import com.shilapi.xcertplay.GeelyHudDisplay
import com.shilapi.xcertplay.GeelyHudProjection
import com.shilapi.xcertplay.VehicleMapPlan

/** Uses the installed or local FS11 navigation configuration, never the generic display picker. */
internal object E01FactoryMapTarget {
    private const val NAVI_PACKAGE = "com.autonavi.amapauto"
    private const val CONFIG = "res/HmiRes/C04010181159_MultipleScreen.json"
    private const val STANDARD_CONFIG = "res/HmiRes/MultipleScreen.json"
    @Volatile private var stage = "NOT_REQUESTED"
    private var configSource = "none"
    private var failure = "none"
    private var targetId: Int? = null

    /** The firmware WindowId is a 1-based Presentation-display index, not Android Display.getDisplayId(). */
    internal data class Spec(val displayIndex: Int, val width: Int, val height: Int)

    internal fun parse(json: String): Spec {
        val root = JSONObject(json)
        val surfaceName = root.getJSONObject("Screens").getJSONObject("InstrumentPannelLarge").getString("Surface")
        val surface = root.getJSONObject("SurfaceConfigs").getJSONObject(surfaceName)
        require(surface.getInt("SurfaceRenderType") == 1) { "Unsupported factory renderer" }
        // FS11's Navi uses WindowId 1001 for presentation display index 1. Its AndroidPlatform
        // implementation selects displays[index - 1]; do not treat 1001 as a display ID.
        val index = surface.getInt("WindowId") - 1000
        val width = surface.getInt("SurfaceWidth")
        val height = surface.getInt("SurfaceHeight")
        require(index > 0 && width in 64..4096 && height in 64..4096)
        return Spec(index, width, height)
    }

    fun plan(context: Context): VehicleMapPlan? {
        configSource = "none"; failure = "none"; targetId = null
        if (!E01NativeMapControl.supported(context)) { stage = "UNSUPPORTED_PLATFORM"; return null }
        if (!GeelyHudProjection.threeFingerEnabled(context)) { stage = "DISABLED"; return null }
        val installedAssets = runCatching {
            val info = context.packageManager.getApplicationInfo(NAVI_PACKAGE, 0)
            if (info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0) {
                null
            } else {
                context.createPackageContext(NAVI_PACKAGE, 0).assets
            }
        }.getOrNull()
        val spec = runCatching {
            readSpec(
                readInstalled = { path ->
                    installedAssets?.open(path)?.bufferedReader()?.use { it.readText() }
                        ?: throw FileNotFoundException(path)
                },
                readBundled = { path ->
                    E01MapConfigAssets.read(context, path.substringAfterLast('/'))
                },
            )
        }.getOrElse {
            stage = "FACTORY_CONFIG_UNAVAILABLE"; failure = it.javaClass.simpleName
            return null
        }
        return select(context, spec)
    }

    /** Both names occur in the inspected FS11 firmware. Never substitute a guessed display. */
    internal fun readSpec(read: (String) -> String): Spec = readSpec(
        readInstalled = read,
        readBundled = { path -> throw FileNotFoundException(path) },
    )

    internal fun readSpec(readInstalled: (String) -> String, readBundled: (String) -> String): Spec {
        for (path in listOf(CONFIG, STANDARD_CONFIG)) {
            var source = "installed"
            val json = try {
                readInstalled(path)
            } catch (missing: FileNotFoundException) {
                try {
                    source = "firmware"
                    readBundled(path)
                } catch (bundledMissing: FileNotFoundException) {
                    if (path == STANDARD_CONFIG) throw bundledMissing
                    continue
                }
            }
            val spec = parse(json) // A present but unsupported config must not silently fall back.
            configSource = "$source:$path"
            return spec
        }
        throw FileNotFoundException("Factory map configuration unavailable")
    }

    internal fun select(context: Context, spec: Spec): VehicleMapPlan? {
        val screen = screen(context, spec.displayIndex, spec.width, spec.height)
            ?: run { stage = "FACTORY_DISPLAY_UNAVAILABLE"; return null }
        targetId = screen.id
        stage = "FACTORY_TARGET_READY"
        // Match the live display dimensions, as the factory Presentation does.
        return VehicleMapPlan(screen, screen.width, screen.height, screen.height,
            vehicleMode = true, automaticCanvas = true, factoryNative = true)
    }

    /** Resolve the firmware's 1-based presentation index and require its configured dimensions. */
    fun screen(context: Context, index: Int, expectedWidth: Int? = null, expectedHeight: Int? = null): GeelyHudDisplay? {
        if (index <= 0) return null
        val displays = context.systemService(DisplayManager::class.java, "display")?.displays.orEmpty()
            .filter { it.displayId != Display.DEFAULT_DISPLAY }
        val display = displays.getOrNull(index - 1) ?: return null
        if (!display.isValid) return null
        val size = Point().also { display.getRealSize(it) }
        if (size.x !in 64..4096 || size.y !in 64..4096) return null
        if (expectedWidth != null && expectedHeight != null &&
            (size.x != expectedWidth || size.y != expectedHeight)) return null
        // The instrument can be asleep before the factory gesture switches it on.
        return GeelyHudDisplay(display.displayId, display.name, size.x, size.y)
    }

    /** Refresh an already selected Android display without reinterpreting its ID as a firmware index. */
    fun screenByDisplayId(context: Context, id: Int): GeelyHudDisplay? {
        if (id == Display.DEFAULT_DISPLAY) return null
        val display = context.systemService(DisplayManager::class.java, "display")?.getDisplay(id) ?: return null
        if (!display.isValid) return null
        val size = Point().also { display.getRealSize(it) }
        if (size.x !in 64..4096 || size.y !in 64..4096) return null
        return GeelyHudDisplay(id, display.name, size.x, size.y)
    }

    fun unavailableMessage(): Int = when (stage) {
        "FACTORY_CONFIG_UNAVAILABLE" -> R.string.e01_native_map_config_unavailable
        "FACTORY_DISPLAY_UNAVAILABLE" -> R.string.e01_native_map_display_unavailable
        else -> R.string.e01_native_map_unavailable
    }

    fun diagnostics() = "e01FactoryTarget stage=$stage config=$configSource target=$targetId failure=$failure"
}

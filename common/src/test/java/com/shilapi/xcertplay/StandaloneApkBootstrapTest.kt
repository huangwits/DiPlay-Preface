package com.shilapi.xcertplay

import android.content.ContextWrapper
import android.content.res.AssetManager
import android.os.Build
import com.shilapi.xcertplay.mfi.LocalMfiAuthenticationClient
import com.shilapi.xcertplay.orchestration.MfiTarget
import java.io.File
import java.io.FileNotFoundException
import java.util.zip.ZipFile
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/** Release-only check: consumes the APK selected by DIPLAY_VALIDATION_APK, never stored keys. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], manifest = Config.NONE)
class StandaloneApkBootstrapTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var apkFile: File

    @Before fun setUp() {
        val path = System.getProperty("diplay.validationApk").orEmpty()
        assumeTrue("Standalone APK validation is explicitly selected for release packaging", path.isNotBlank())
        apkFile = File(path)
        assertTrue("Selected APK must exist", apkFile.isFile)
        // Robolectric's oldest supported sandbox is 23; select the production API 22 branch.
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 22)
        DiPlayBootstrap::class.java.getDeclaredField("ready").apply { isAccessible = true }
            .setBoolean(null, false)
    }

    @Test fun packagedIdentityLoadsAndSignsOnFreshInstall() {
        ZipFile(apkFile).use { apk ->
            val context = contextFor(apk)
            DiPlayBootstrap.ensure(context, MfiTarget.LOCAL)
            val installed = File(context.noBackupFilesDir, "offline-mfi")
            val client = LocalMfiAuthenticationClient.load(installed)
            assertEquals(64, client.signChallenge(ByteArray(32) { it.toByte() }).size)
            for (name in listOf("identity.pk8", "certificate.p7b")) {
                assertTrue("Installed input must match packaged input", apk.getInputStream(
                    apk.getEntry("assets/offline-mfi/$name"),
                ).use { it.readBytes().contentEquals(File(installed, name).readBytes()) })
            }
            assertFalse(File(context.noBackupFilesDir, "offline-mfi-staging").exists())
        }
    }

    @Test fun fullApkRepairsMissingAssetsAfterOverlayWithoutClearingPhoneSettings() {
        ZipFile(apkFile).use { apk ->
            val context = contextFor(apk)
            val emptyAssets = mock(AssetManager::class.java)
            `when`(emptyAssets.open(anyString())).thenThrow(FileNotFoundException("source-only APK"))
            val oldContext = object : ContextWrapper(context) {
                override fun getAssets(): AssetManager = emptyAssets
            }
            DiPlayPreferences.savePhone(context, "AA:BB:CC:DD:EE:01", "Saved phone")
            assertThrows(Exception::class.java) { DiPlayBootstrap.ensure(oldContext, MfiTarget.LOCAL) }
            assertFalse(File(context.noBackupFilesDir, "offline-mfi").exists())
            DiPlayBootstrap.ensure(context, MfiTarget.LOCAL)
            assertEquals("AA:BB:CC:DD:EE:01", DiPlayPreferences.phoneAddress(context))
            assertEquals(64, LocalMfiAuthenticationClient.load(
                File(context.noBackupFilesDir, "offline-mfi"),
            ).signChallenge(ByteArray(32)).size)
        }
    }

    private fun contextFor(apk: ZipFile): ContextWrapper {
        val output = temporary.newFolder()
        val assets = mock(AssetManager::class.java)
        `when`(assets.open(anyString())).thenAnswer { invocation ->
            val name = invocation.getArgument<String>(0)
            val entry = apk.getEntry("assets/$name") ?: throw FileNotFoundException(name)
            apk.getInputStream(entry)
        }
        return object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getAssets(): AssetManager = assets
            override fun getNoBackupFilesDir(): File = output
        }
    }
}

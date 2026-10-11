package com.shilapi.xcertplay.e01goc

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class BluetoothAssetsTest {
    private val signer = "db0dc2a34dc06f22db7a3d10103fa011167712ebe61985ca2103084ff542cb82"
    private val asset = "e01-goc/manage.enc"

    @Test fun materializationChecksBytesBeforeWritingAndPreservesActiveWatchdog() {
        val app = RuntimeEnvironment.getApplication()
        val base = java.io.File(app.filesDir, "e01-goc")
        val target = java.io.File(base, "manage.sh")
        val prepare = E01GocManager::class.java.getDeclaredMethod("prepareScript").apply { isAccessible = true }
        val manager = E01GocManager(app) {}
        prepare.invoke(manager)
        assertArrayEquals(java.io.File("src/main/bluetooth/manage.sh").readBytes(), target.readBytes())
        assertFalse(java.io.File(base, "manage.sh.new").exists())
        target.writeText("active watchdog")
        val lock = java.io.File(base, "lock").apply { mkdir() }
        prepare.invoke(manager)
        assertEquals("active watchdog", target.readText())
        lock.delete()
        val assets = org.mockito.Mockito.mock(android.content.res.AssetManager::class.java)
        val damaged = app.assets.open(asset).use { it.readBytes() }.also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        org.mockito.Mockito.`when`(assets.open(asset)).thenAnswer { damaged.inputStream() }
        val context = object : android.content.ContextWrapper(app) {
            override fun getApplicationContext(): android.content.Context = this
            override fun getAssets() = assets
        }
        assertThrows(java.lang.reflect.InvocationTargetException::class.java) { prepare.invoke(E01GocManager(context) {}) }
        assertEquals("active watchdog", target.readText())
        assertFalse(java.io.File(base, "manage.sh.new").exists())
    }

    @Test
    @Config(shadows = [com.shilapi.xcertplay.license.BluetoothLicenseLayoutTest.LicensedResources::class])
    fun licensedApkLoaderRequiresMatchingPackageSignerAndNonDebuggableFlags() {
        val path = System.getProperty("diplay.validationApk").orEmpty()
        org.junit.Assume.assumeTrue("Select the actual release APK", path.isNotEmpty())
        val certificate = java.util.jar.JarFile(path, true).use { jar ->
            val entry = jar.getJarEntry("AndroidManifest.xml")
            jar.getInputStream(entry).use { it.readBytes() }
            entry.certificates.single().encoded
        }
        java.util.zip.ZipFile(path).use { apk ->
            val assets = org.mockito.Mockito.mock(android.content.res.AssetManager::class.java)
            org.mockito.Mockito.`when`(assets.open(org.mockito.ArgumentMatchers.anyString())).thenAnswer {
                apk.getInputStream(apk.getEntry("assets/" + it.getArgument<String>(0)))
            }
            val pm = org.mockito.Mockito.mock(android.content.pm.PackageManager::class.java)
            val info = android.content.pm.PackageInfo().apply {
                signatures = arrayOf(android.content.pm.Signature(certificate))
            }
            val application = android.content.pm.ApplicationInfo().apply { flags = 0 }
            org.mockito.Mockito.`when`(pm.getPackageInfo(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(info)
            var name = "com.shihab.diplay.preface"
            val context = object : android.content.ContextWrapper(RuntimeEnvironment.getApplication()) {
                override fun getPackageName() = name
                override fun getPackageManager() = pm
                override fun getApplicationInfo() = application
                override fun getAssets() = assets
            }
            assertArrayEquals(java.io.File("src/main/bluetooth/manage.sh").readBytes(), BluetoothAssets.read(context, asset))
            application.flags = android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE
            assertThrows(IllegalStateException::class.java) { BluetoothAssets.read(context, asset) }
            application.flags = 0
            name = "other.package"
            assertThrows(IllegalStateException::class.java) { BluetoothAssets.read(context, asset) }
            name = "com.shihab.diplay.preface"
            info.signatures = arrayOf(android.content.pm.Signature(byteArrayOf(1, 2, 3)))
            assertThrows(IllegalStateException::class.java) { BluetoothAssets.read(context, asset) }
        }
    }

    @Test fun generatedAssetDecryptsToTheActualMaintenanceScript() {
        val app = RuntimeEnvironment.getApplication()
        val actual = BluetoothAssets.read(app, asset)
        val expected = java.io.File("src/main/bluetooth/manage.sh").readBytes()
        assertArrayEquals(expected, actual)
        assertThrows(java.io.FileNotFoundException::class.java) { app.assets.open("e01-goc/manage.sh") }
        val apk = System.getProperty("diplay.validationApk").orEmpty()
        if (apk.isNotEmpty()) java.util.zip.ZipFile(apk).use { zip ->
            assertNull(zip.getEntry("assets/e01-goc/manage.sh"))
            assertNull(zip.getEntry("assets/e01-goc/gocsdk-spp-uuid128-v2"))
            assertArrayEquals(expected, BluetoothAssets.decode(zip.getInputStream(zip.getEntry("assets/$asset")).readBytes(), asset, signer))
            val candidate = BluetoothAssets.decode(zip.getInputStream(zip.getEntry("assets/e01-goc/component.enc")).readBytes(), "e01-goc/component.enc", signer)
            assertEquals(2_841_668, candidate.size)
            assertEquals(E01GocManager.CANDIDATE_SHA, MessageDigest.getInstance("SHA-256").digest(candidate).joinToString("") { "%02x".format(it) })
        }
    }

    @Test fun changedCiphertextTagHeaderSignerAndAssetAreRejected() {
        val bytes = RuntimeEnvironment.getApplication().assets.open(asset).use { it.readBytes() }
        for (index in listOf(0, 5, 20, bytes.lastIndex)) {
            val corrupt = bytes.copyOf(); corrupt[index] = (corrupt[index].toInt() xor 1).toByte()
            assertThrows(Exception::class.java) { BluetoothAssets.decode(corrupt, asset, signer) }
        }
        assertThrows(Exception::class.java) { BluetoothAssets.decode(bytes, asset, "0".repeat(64)) }
        assertThrows(Exception::class.java) { BluetoothAssets.decode(bytes, "e01-goc/component.enc", signer) }
        assertThrows(Exception::class.java) { BluetoothAssets.decode(bytes.copyOf(30), asset, signer) }
    }

    @Test fun authenticatedButOversizedDecompressionIsRejected() {
        val context = "DiPlay Bluetooth v1\u0000$signer\u0000$asset".toByteArray()
        val key = MessageDigest.getInstance("SHA-256").run { update(BluetoothAssetKey.material()); digest(context) }
        val packed = ByteArrayOutputStream().also { GZIPOutputStream(it).use { gz -> gz.write(ByteArray(65_537)) } }.toByteArray()
        val nonce = ByteArray(12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce)); cipher.updateAAD(context)
        assertThrows(IllegalArgumentException::class.java) { BluetoothAssets.decode("DPG1".toByteArray() + nonce + cipher.doFinal(packed), asset, signer) }
    }
}

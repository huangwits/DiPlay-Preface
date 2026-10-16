package com.shilapi.xcertplay

import com.geelyplay.preface.E01MapConfigAssets
import com.geelyplay.preface.e01goc.BluetoothAssetKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class E01MapConfigAssetsTest {
    private val signer = "8accfdd43476ee44404140964c3bd5c38edde9fd3d47e76bdd7e48c2c394e418"
    private val name = "C04010181159_MultipleScreen.json"

    @Test
    fun encryptedConfigIsBoundToItsNameSignerAndExpansionLimit() {
        val expected = "{\"Screens\":{},\"SurfaceConfigs\":{}}".toByteArray()
        val envelope = seal(expected, name, signer)
        assertArrayEquals(expected, E01MapConfigAssets.decode(envelope, name, signer))
        assertThrows(Exception::class.java) { E01MapConfigAssets.decode(envelope, name, "0".repeat(64)) }
        assertThrows(Exception::class.java) {
            E01MapConfigAssets.decode(envelope, "MultipleScreen.json", signer)
        }

        val damaged = envelope.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertThrows(Exception::class.java) { E01MapConfigAssets.decode(damaged, name, signer) }
        assertThrows(IllegalArgumentException::class.java) {
            E01MapConfigAssets.decode(seal(ByteArray(1_048_577), name, signer), name, signer)
        }
    }

    private fun seal(plain: ByteArray, name: String, signer: String): ByteArray {
        val packed = ByteArrayOutputStream().also { output ->
            GZIPOutputStream(output).use { it.write(plain) }
        }.toByteArray()
        val context = "GeelyPlay FS11 flyscreen v1\u0000$signer\u0000$name".toByteArray()
        val master = BluetoothAssetKey.material()
        val key = MessageDigest.getInstance("SHA-256").run { update(master); digest(context) }
        val nonce = ByteArray(12)
        try {
            val ciphertext = Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
                updateAAD(context)
                doFinal(packed)
            }
            return "DPG1".toByteArray() + nonce + ciphertext
        } finally {
            master.fill(0)
            context.fill(0)
            key.fill(0)
            packed.fill(0)
            nonce.fill(0)
        }
    }
}

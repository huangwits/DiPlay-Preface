package com.shilapi.xcertplay.license

import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature

class OfflineLicenseProtocolTest {
    private val issuer = KeyPairGenerator.getInstance("RSA").apply { initialize(3072) }.generateKeyPair()
    private val device = "a".repeat(64)
    private val signer = "b".repeat(64)
    private val packageName = "com.shihab.diplay.preface"
    private fun payload(device: String = this.device, packageName: String = this.packageName,
                        signer: String = this.signer, mode: String = "permanent", domain: String = "DP-OFFLINE1") =
        "$domain\n$device\n$packageName\n$signer\n$mode\n12345678-1234-1234-1234-123456789abc".toByteArray()
    private fun sign(data: ByteArray) = Signature.getInstance("SHA256withRSA").run {
        initSign(issuer.private); update(data); sign()
    }
    private fun verify(data: ByteArray, signature: ByteArray = sign(data), key: ByteArray = issuer.public.encoded) =
        OfflineLicenseProtocol.verify(data, signature, key, device, packageName, signer)

    @Test fun permanentGrantVerifiesRepeatedlyWithoutNetworkClockOrChallenge() {
        val data = payload(); val signature = sign(data)
        verify(data, signature); verify(data, signature)
    }
    @Test fun tamperingIssuerSubstitutionAndUnsignedInputAreRejected() {
        val data = payload(); val signature = sign(data)
        assertThrows(Exception::class.java) { verify(data + byteArrayOf(32), signature) }
        signature[10] = (signature[10].toInt() xor 1).toByte()
        assertThrows(Exception::class.java) { verify(data, signature) }
        assertThrows(Exception::class.java) { verify(data, byteArrayOf()) }
        val other = KeyPairGenerator.getInstance("RSA").apply { initialize(3072) }.generateKeyPair()
        assertThrows(Exception::class.java) { verify(data, sign(data), other.public.encoded) }
    }
    @Test fun signedGrantsForOtherDevicePackageSignerOrProtocolAreRejected() {
        for (data in listOf(payload(device = "c".repeat(64)), payload(packageName = "com.other.app"),
            payload(signer = "c".repeat(64)), payload(mode = "expires"), payload(domain = "DP1"),
            payload() + "\nextra".toByteArray())) {
            assertThrows(Exception::class.java) { verify(data) }
        }
    }
}

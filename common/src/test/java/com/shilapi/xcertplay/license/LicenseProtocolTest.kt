package com.shilapi.xcertplay.license

import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature

class LicenseProtocolTest {
    private val issuer = KeyPairGenerator.getInstance("RSA").apply { initialize(3072) }.generateKeyPair()
    private val device = "a".repeat(64)
    private val signer = "b".repeat(64)
    private val challenge = "c".repeat(48)
    private val packageName = "com.shihab.diplay.preface"
    private fun payload(device: String = this.device, packageName: String = this.packageName,
                        signer: String = this.signer, challenge: String = this.challenge, expires: Long = 1300) =
        "DP1\n12345678-1234-1234-1234-123456789abc\n$device\n1000\n$expires\n$packageName\n$signer\n$challenge".toByteArray()
    private fun sign(payload: ByteArray) = Signature.getInstance("SHA256withRSA").run {
        initSign(issuer.private); update(payload); sign()
    }
    private fun verify(payload: ByteArray, signature: ByteArray = sign(payload)) = LicenseProtocol.verify(
        payload, signature, issuer.public.encoded, device, packageName, signer, challenge)

    @Test fun acceptsFreshBoundLeaseAndRejectsChangedSignatureOrIssuer() {
        val data = payload()
        assertEquals(300000, verify(data))
        assertEquals(1000, verify(payload(expires = 1001)))
        val signature = sign(data)
        signature[0] = (signature[0].toInt() xor 1).toByte()
        assertThrows(Exception::class.java) { verify(data, signature) }
        val other = KeyPairGenerator.getInstance("RSA").apply { initialize(3072) }.generateKeyPair()
        assertThrows(Exception::class.java) { LicenseProtocol.verify(data, sign(data), other.public.encoded, device, packageName, signer, challenge) }
    }

    @Test fun rejectsAnotherDeviceResignedPackageReplayAndInvalidLifetime() {
        for (data in listOf(payload(device = "d".repeat(64)), payload(packageName = "com.other.app"),
            payload(signer = "d".repeat(64)), payload(challenge = "d".repeat(48)),
            payload(expires = 1000), payload(expires = 1301), payload(expires = Long.MAX_VALUE))) {
            assertThrows(Exception::class.java) { verify(data) }
        }
    }

    @Test fun deviceProofMatchesServerCanonicalFormatAndNormalizesNoUntrustedFields() {
        val publicKey = byteArrayOf(1, 2, 3)
        val deviceHash = LicenseProtocol.hash(publicKey)
        val code = "0123456789ABCDEF0123456789ABCDEF"
        val proof = LicenseProtocol.proof("activate", challenge, "d".repeat(64), deviceHash, publicKey, code, packageName, signer)
        assertEquals(listOf("DP-LIC1", "activate", challenge, "d".repeat(64), deviceHash, deviceHash,
            LicenseProtocol.hash(code.toByteArray()), packageName, signer).joinToString("\n"), String(proof))
        assertThrows(IllegalArgumentException::class.java) { LicenseProtocol.proof("activate", "bad", "d".repeat(64), deviceHash, publicKey, code, packageName, signer) }
    }

    @Test fun onlineRequestProofDoesNotCarryAnActivationCode() {
        val key = byteArrayOf(1, 2, 3)
        val device = LicenseProtocol.hash(key)
        val data = LicenseProtocol.proof("request", challenge, "d".repeat(64), device, key, "", packageName, signer)
        assertEquals(listOf("DP-LIC1", "request", challenge, "d".repeat(64), device, device, "", packageName, signer).joinToString("\n"), String(data))
        assertThrows(IllegalArgumentException::class.java) {
            LicenseProtocol.proof("request", challenge, "d".repeat(64), device, key, "A".repeat(32), packageName, signer)
        }
    }
}

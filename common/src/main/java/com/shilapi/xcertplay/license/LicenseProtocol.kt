package com.shilapi.xcertplay.license

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/** Signed, challenge-bound leases. No issuer secret is part of the Android application. */
internal object LicenseProtocol {
    fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

    fun proof(action: String, challenge: String, nonce: String, device: String, publicKey: ByteArray,
              code: String, packageName: String, signer: String): ByteArray {
        require(action == "activate" || action == "refresh" || action == "request")
        require(Regex("[0-9a-f]{48}").matches(challenge) && Regex("[0-9a-f]{64}").matches(nonce))
        require(device == hash(publicKey))
        require(action == "activate" || code.isEmpty())
        require(code.isEmpty() || Regex("[0-9A-F]{32}").matches(code))
        return listOf("DP-LIC1", action, challenge, nonce, device, hash(publicKey),
            if (code.isEmpty()) "" else hash(code.toByteArray(Charsets.US_ASCII)), packageName, signer)
            .joinToString("\n").toByteArray(Charsets.US_ASCII)
    }

    fun verify(payload: ByteArray, signature: ByteArray, issuerKey: ByteArray, device: String,
               packageName: String, signer: String, challenge: String): Long {
        require(payload.size in 1..2048 && signature.size in 256..512)
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(issuerKey))
        require(Signature.getInstance("SHA256withRSA").run {
            initVerify(key); update(payload); verify(signature)
        }) { "授权签名无效" }
        val fields = String(payload, Charsets.US_ASCII).split('\n')
        require(fields.size == 8 && fields[0] == "DP1" && Regex("[0-9a-f-]{36}").matches(fields[1]))
        require(fields[2] == device && fields[5] == packageName && fields[6] == signer && fields[7] == challenge) {
            "授权与本机或本次请求不匹配"
        }
        val issued = fields[3].toLong(); val expires = fields[4].toLong()
        require(issued > 0 && expires > issued && expires <= issued + 300) { "授权有效期无效" }
        return (expires - issued) * 1000
    }
}

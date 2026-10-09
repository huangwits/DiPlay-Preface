package com.shilapi.xcertplay.license

import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec

/** Permanent installation-bound grants, using a distinct domain from online leases. */
internal object OfflineLicenseProtocol {
    fun verify(payload: ByteArray, signature: ByteArray, issuer: ByteArray,
               device: String, packageName: String, signer: String) {
        require(payload.size in 1..1024 && signature.size in 384..512 && issuer.size in 256..1024)
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(issuer)) as RSAPublicKey
        require(key.modulus.bitLength() in 3072..4096)
        require(Signature.getInstance("SHA256withRSA").run {
            initVerify(key); update(payload); verify(signature)
        }) { "激活码签名无效" }
        val fields = String(payload, Charsets.US_ASCII).split('\n')
        require(fields.size == 6 && fields[0] == "DP-OFFLINE1" && fields[4] == "permanent" &&
            Regex("[0-9a-f-]{36}").matches(fields[5])) { "激活码格式不支持" }
        require(Regex("[0-9a-f]{64}").matches(device) && Regex("[0-9a-f]{64}").matches(signer))
        require(fields[1] == device && fields[2] == packageName && fields[3] == signer) {
            "激活码与本机或安装包不匹配"
        }
    }
}

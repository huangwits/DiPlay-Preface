package com.shilapi.xcertplay

import android.util.Base64
import com.shilapi.xcertplay.license.LicenseProtocol
import com.shilapi.xcertplay.license.OnlineLicense
import java.security.KeyPairGenerator
import java.security.Signature
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class CloudDiagnosticUploadTest {
    @Test fun cloudRemovesPrivateAddressesAndSensitiveFieldsButKeepsDiagnosis() {
        val value = CloudDiagnosticUpload.sanitize("""
            Android 5.1 / API 22
            LOCAL_NETWORK path local=192.168.43.1 peer=10.0.0.2
            connection=fe80::1234 mac=AA:BB:CC:DD:EE:FF mail=owner@example.com
            password=secret-phrase
            phoneName=Private phone
            latitude=31.2 longitude=121.4
            nextRoad=My home road
            songTitle=Private music
            token=abc-secret
            TRACE raw protocol data
            NativeMap receiver=available status=requested
        """.trimIndent())
        for (secret in listOf("192.168", "10.0.0", "fe80", "AA:BB", "owner@", "secret-phrase", "Private", "31.2", "My home", "abc-secret", "raw protocol"))
            assertFalse(secret, value.contains(secret))
        assertTrue(value.contains("Android 5.1 / API 22"))
        assertTrue(value.contains("NativeMap receiver=available status=requested"))
        val hud = CloudDiagnosticUpload.sanitize(GeelyHudProjection.diagnosticReport(org.robolectric.RuntimeEnvironment.getApplication()))
        for (field in listOf("selectedId=", "attachedId=", "stage=", "availableDisplays=")) assertTrue(field, hud.contains(field))
        assertFalse(hud.contains("selectedName="))
    }

    @Test fun cloudLimitPreservesSummaryAndLatestLinesWithoutBreakingUtf8() {
        val body = "availableDisplays=main,instrument\n" +
            (1..12000).joinToString("\n") { "$it " + "中文日志".repeat(20) } + "\nLATEST wheel=NEXT map=requested"
        val bounded = CloudDiagnosticUpload.boundedReport(body)
        assertTrue(bounded.startsWith("availableDisplays=main,instrument"))
        assertTrue(bounded.endsWith("LATEST wheel=NEXT map=requested"))
        assertTrue(bounded.contains("Older log lines omitted"))
        assertTrue(bounded.toByteArray(Charsets.UTF_8).size <= CloudDiagnosticUpload.MAX_REPORT_BYTES)
        assertFalse(bounded.contains('\uFFFD'))
        assertEquals("small report", CloudDiagnosticUpload.boundedReport("small report"))
    }

    @Test fun submissionBindsExactDocumentToFreshProofWithoutAnyLicenseRequest() {
        val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val identity = OnlineLicense.DiagnosticIdentity("https://license.example", "com.shihab.diplay.preface", "b".repeat(64), keys.public.encoded, keys.private)
        val document = CloudDiagnosticUpload.document("0.2.16.13", "方向盘下一曲无效", "wheel=NEXT result=ignored")
        assertEquals("0.2.16.13", JSONObject(document).getString("version"))
        val paths = mutableListOf<String>()
        val receipt = CloudDiagnosticUpload.submit(identity, document) { path, value ->
            paths += path
            if (path == "/v1/challenge") JSONObject().put("id", "1".repeat(48)).put("nonce", "2".repeat(64))
            else {
                assertEquals(document, value.getString("document"))
                val device = LicenseProtocol.hash(keys.public.encoded)
                val canonical = listOf("DP-LIC1", "report:" + LicenseProtocol.hash(document.toByteArray()), "1".repeat(48),
                    "2".repeat(64), device, device, "", identity.packageName, identity.signer).joinToString("\n")
                assertTrue(Signature.getInstance("SHA256withRSA").run {
                    initVerify(keys.public); update(canonical.toByteArray()); verify(Base64.decode(value.getString("signature"), Base64.DEFAULT))
                })
                JSONObject().put("ok", true).put("receipt", "DPR-0123456789ABCDEF").put("expiresAt", 1800000000)
            }
        }
        assertEquals(listOf("/v1/challenge", "/v1/diagnostics"), paths)
        assertEquals("DPR-0123456789ABCDEF", receipt.id)
    }

    @Test fun rejectsMalformedChallengeAndUnconfirmedReceipts() {
        val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val identity = OnlineLicense.DiagnosticIdentity("https://license.example", "com.shihab.diplay.preface", "b".repeat(64), keys.public.encoded, keys.private)
        for (bad in listOf(JSONObject(), JSONObject().put("ok", false).put("receipt", "DPR-0123456789ABCDEF").put("expiresAt", 1800000000),
            JSONObject().put("ok", true).put("receipt", "<script>").put("expiresAt", 1800000000))) {
            try {
                CloudDiagnosticUpload.submit(identity, "{}") { path, _ ->
                    if (path == "/v1/challenge") JSONObject().put("id", "1".repeat(48)).put("nonce", "2".repeat(64)) else bad
                }; fail("Receipt must be validated")
            } catch (_: IllegalStateException) { }
        }
        try {
            CloudDiagnosticUpload.submit(identity, "{}") { _, _ -> JSONObject().put("id", "bad").put("nonce", "bad") }
            fail("Challenge must be validated")
        } catch (_: IllegalStateException) { }
    }
}

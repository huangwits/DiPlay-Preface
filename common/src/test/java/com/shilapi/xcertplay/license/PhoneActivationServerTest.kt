package com.shilapi.xcertplay.license

import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.Socket
import java.net.URI
import java.net.URLEncoder
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PhoneActivationServerTest {
    private val device = "DP-DEVICE1-" + "a".repeat(64)
    private fun server(activate: (String) -> Unit = {}) = PhoneActivationServer(
        InetAddress.getByName("127.0.0.1"), device, "starts181004", activate)

    private fun request(server: PhoneActivationServer, method: String = "GET", body: String = "",
                        path: String = URI(server.url).path, headers: Map<String, String> = emptyMap()): String {
        val uri = URI(server.url)
        val all = mutableMapOf("Host" to uri.rawAuthority)
        if (method == "POST") {
            all["Content-Type"] = "application/x-www-form-urlencoded"
            all["Content-Length"] = body.toByteArray().size.toString()
        }
        all.putAll(headers)
        return Socket(uri.host, uri.port).use { socket ->
            socket.soTimeout = 10000
            socket.getOutputStream().write(("$method $path HTTP/1.1\r\n" +
                all.entries.joinToString("") { "${it.key}: ${it.value}\r\n" } + "\r\n" + body).toByteArray())
            socket.getInputStream().readBytes().toString(Charsets.UTF_8)
        }
    }
    private fun csrf(server: PhoneActivationServer) = Regex("name=\"csrf\" value=\"([0-9a-f]+)\"")
        .find(request(server))!!.groupValues[1]
    private fun form(csrf: String, code: String) = "csrf=$csrf&code=" + URLEncoder.encode(code, "UTF-8")

    @Test fun pageContainsDeviceWithoutContactAndHasNoExternalResourcesOrCaching() {
        server().use { s ->
            val response = request(s)
            assertTrue(response.startsWith("HTTP/1.1 200"))
            assertTrue(response.contains(device))
            assertFalse(response.contains("starts181004"))
            assertFalse(response.contains("微信"))
            assertTrue(response.contains("Cache-Control: no-store"))
            assertTrue(response.contains("frame-ancestors 'none'"))
            assertTrue(response.contains("Referrer-Policy: same-origin"))
            assertTrue(response.contains("name=\"referrer\" content=\"same-origin\""))
            assertFalse(response.contains("https://"))
            assertFalse(response.contains("localStorage"))
            assertTrue(request(s, path = URI(s.url).path + "copy.js").contains("execCommand"))
        }
    }

    @Test fun aRealSignedGrantSucceedsAndForgeryOrWrongInstallationCannotActivate() {
        val key = KeyPairGenerator.getInstance("RSA").apply { initialize(3072) }.generateKeyPair()
        val signer = "b".repeat(64)
        fun grant(deviceId: String): String {
            val payload = "DP-OFFLINE1\n$deviceId\ncom.shihab.diplay.preface\n$signer\npermanent\n00000000-0000-0000-0000-000000000001".toByteArray()
            val signature = Signature.getInstance("SHA256withRSA").run { initSign(key.private); update(payload); sign() }
            return "DP-ACT1." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload) + "." +
                Base64.getUrlEncoder().withoutPadding().encodeToString(signature)
        }
        val accepted = AtomicInteger()
        server { text ->
            val parts = text.split('.')
            require(parts.size == 3 && parts[0] == "DP-ACT1")
            OfflineLicenseProtocol.verify(Base64.getUrlDecoder().decode(parts[1]), Base64.getUrlDecoder().decode(parts[2]),
                key.public.encoded, "a".repeat(64), "com.shihab.diplay.preface", signer)
            accepted.incrementAndGet()
        }.use { s ->
            val csrf = csrf(s)
            for (bad in listOf("forged", grant("c".repeat(64)))) {
                assertTrue(request(s, "POST", form(csrf, bad)).startsWith("HTTP/1.1 422"))
                assertEquals(0, accepted.get())
            }
            val response = request(s, "POST", form(csrf, grant("a".repeat(64))),
                headers = mapOf("Origin" to "http://${URI(s.url).rawAuthority}", "Sec-Fetch-Site" to "same-origin"))
            assertTrue(response.startsWith("HTTP/1.1 200"))
            assertTrue(response.contains("本机已永久激活"))
            assertFalse(response.contains("不要关闭车机授权页"))
            assertEquals(1, accepted.get())
            assertTrue(request(s, "POST", form(csrf, grant("a".repeat(64)))).contains("本机已永久激活"))
            assertEquals(1, accepted.get())
        }
    }

    @Test fun rejectsMissingTokenCsrfCrossOriginAndRebindingBeforeCallingVerifier() {
        val calls = AtomicInteger()
        server { calls.incrementAndGet() }.use { s ->
            val csrf = csrf(s)
            assertTrue(request(s, path = "/").startsWith("HTTP/1.1 404"))
            assertTrue(request(s, "POST", form("bad", "code")).startsWith("HTTP/1.1 403"))
            assertTrue(request(s, "POST", form(csrf, "code"), headers = mapOf("Origin" to "https://evil.invalid")).startsWith("HTTP/1.1 403"))
            assertTrue(request(s, "POST", form(csrf, "code"), headers = mapOf("Sec-Fetch-Site" to "cross-site")).startsWith("HTTP/1.1 403"))
            assertTrue(request(s, headers = mapOf("Host" to "evil.invalid")).startsWith("HTTP/1.1 400"))
            assertEquals(0, calls.get())
        }
    }

    @Test fun rejectsOversizedBodiesDuplicateFieldsAndUnsupportedFraming() {
        val calls = AtomicInteger()
        server { calls.incrementAndGet() }.use { s ->
            val body = form(csrf(s), "code")
            assertTrue(request(s, "POST", body, headers = mapOf("Content-Length" to "9999999")).startsWith("HTTP/1.1 413"))
            assertTrue(request(s, "POST", body + "&code=again").startsWith("HTTP/1.1 400"))
            assertTrue(request(s, "POST", body, headers = mapOf("Transfer-Encoding" to "chunked")).startsWith("HTTP/1.1 400"))
            assertTrue(request(s, "POST", form(csrf(s), "a".repeat(4097))).startsWith("HTTP/1.1 413"))
            assertTrue(request(s, "POST", body, headers = mapOf("Content-Type" to "text/plain")).startsWith("HTTP/1.1 403"))
            assertEquals(0, calls.get())
        }
    }

    @Test fun verifierErrorsAreEscapedAndSubmittedCodesAreNeverReflected() {
        server { error("<script>alert('x')</script>") }.use { s ->
            val result = request(s, "POST", form(csrf(s), "secret-submitted-grant"))
            assertTrue(result.contains("&lt;script&gt;"))
            assertFalse(result.contains("<script>alert"))
            assertFalse(result.contains("secret-submitted-grant"))
        }
    }

    @Test fun closingRevokesOldUrlClosesPendingSocketsAndDoesNotAffectNewSession() {
        val closed = CountDownLatch(1)
        val s = PhoneActivationServer(InetAddress.getByName("127.0.0.1"), device, "starts181004", {}, onClosed = { closed.countDown() })
        val old = URI(s.url)
        val pending = Socket(old.host, old.port)
        pending.soTimeout = 2000
        pending.getOutputStream().write("GET ".toByteArray())
        s.close(); s.close()
        assertTrue(closed.await(2, TimeUnit.SECONDS))
        assertTrue(s.isClosed)
        assertTrue(runCatching { Socket(old.host, old.port).close() }.isFailure)
        pending.use { assertTrue(runCatching { it.getInputStream().read() }.getOrDefault(-1) < 0) }
        server().use { newer ->
            assertNotEquals(old.path, URI(newer.url).path)
            assertTrue(request(newer, path = old.path).startsWith("HTTP/1.1 404"))
        }
    }

    @Test fun expiryStopsServerAndInvalidatesQr() {
        val closed = CountDownLatch(1)
        PhoneActivationServer(InetAddress.getByName("127.0.0.1"), device, "starts181004", {},
            onClosed = { closed.countDown() }, lifetimeMillis = 150).use { s ->
            assertTrue(closed.await(3, TimeUnit.SECONDS))
            assertTrue(s.isClosed)
            assertTrue(runCatching { request(s) }.isFailure)
        }
    }

    @Test fun onlyLocalNetworkInterfacesAreAdvertised() {
        for (name in listOf("wlan0", "ap0", "softap0", "p2p0", "eth0", "en0")) assertTrue(PhoneActivationServer.localInterface(name))
        for (name in listOf("rmnet0", "ccmni0", "tun0", "ppp0", "lo")) assertFalse(PhoneActivationServer.localInterface(name))
    }
}

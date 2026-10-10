package com.shilapi.xcertplay.update

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class UpdateClientTest {
    @get:Rule val temporary = TemporaryFolder()
    private val payload = "a complete update payload".toByteArray()
    private fun release(assetApiUrl: String? = null) = UpdateRelease("0.2.16.1", "DiPlay-Preface-v0.2.16.1.apk",
        "${UpdateCatalog.REPOSITORY}/releases/download/v0.2.16.1/DiPlay-Preface-v0.2.16.1.apk",
        payload.size.toLong(), MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }, "", assetApiUrl)

    private open class Response(url: URL, val bytes: ByteArray, val code: Int = 200,
        val headers: Map<String, String> = emptyMap()) : HttpURLConnection(url) {
        var closed = false
        override fun connect() {}
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun getResponseCode() = code
        override fun getInputStream(): java.io.InputStream = ByteArrayInputStream(bytes)
        override fun getHeaderField(name: String) = headers[name]
    }

    @Test fun followsGithubAssetRedirectAndVerifiesCompleteBytesWithoutModernContentLengthApi() {
        val replies = mutableListOf<Response>()
        val client = UpdateClient { url ->
            Response(url, if (replies.isEmpty()) byteArrayOf() else payload,
                if (replies.isEmpty()) 302 else 200,
                if (replies.isEmpty()) mapOf("Location" to "https://release-assets.githubusercontent.com/github-production-release-asset/test")
                else mapOf("Content-Length" to payload.size.toString())).also(replies::add)
        }
        val file = temporary.newFile()
        val progress = mutableListOf<Int>()
        client.download(release(), file, UpdateCancellation(), progress::add)
        assertArrayEquals(payload, file.readBytes())
        assertEquals(100, progress.last())
        assertTrue(replies.all { it.closed && !it.instanceFollowRedirects })
    }

    @Test fun fallsBackToGithubApiAssetWhenBrowserDownloadCannotOpen() {
        val apiUrl = "https://api.github.com/repos/huangwits/DiPlay-Preface/releases/assets/123"
        val opened = mutableListOf<String>()
        lateinit var response: Response
        val client = UpdateClient { url ->
            opened += url.toString()
            if (url.host == "github.com") throw IOException("release CDN unavailable")
            Response(url, payload, headers = mapOf("Content-Length" to payload.size.toString())).also { response = it }
        }
        val file = temporary.newFile()
        client.download(release(apiUrl), file, UpdateCancellation()) {}
        assertArrayEquals(payload, file.readBytes())
        assertEquals(2, opened.size)
        assertEquals(apiUrl, opened.last())
        assertEquals("application/octet-stream", response.getRequestProperty("Accept"))
    }

    @Test fun connectionAndReadTimeoutsTryTheBinaryApiInsteadOfBeingTreatedAsCancellation() {
        val apiUrl = "https://api.github.com/repos/huangwits/DiPlay-Preface/releases/assets/123"
        for (stage in listOf("open", "headers", "body")) {
            val opened = mutableListOf<String>()
            val responses = mutableListOf<Response>()
            val client = UpdateClient { url ->
                opened += url.toString()
                if (url.host == "github.com") {
                    if (stage == "open") throw java.net.SocketTimeoutException("connect timed out")
                    object : Response(url, payload) {
                        override fun getResponseCode(): Int {
                            if (stage == "headers") throw java.net.SocketTimeoutException("headers timed out")
                            return 200
                        }
                        override fun getInputStream(): java.io.InputStream = object : java.io.InputStream() {
                            var delivered = false
                            override fun read(): Int = throw java.net.SocketTimeoutException("body timed out")
                            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                                if (delivered) throw java.net.SocketTimeoutException("body timed out")
                                buffer[offset] = payload[0]; delivered = true; return 1
                            }
                        }
                    }.also(responses::add)
                } else Response(url, payload).also(responses::add)
            }
            val file = temporary.newFile()
            client.download(release(apiUrl), file, UpdateCancellation()) {}
            assertArrayEquals(stage, payload, file.readBytes())
            assertEquals(listOf(release().apkUrl, apiUrl), opened)
            assertEquals("application/octet-stream", responses.last().getRequestProperty("Accept"))
            assertTrue(responses.all { it.closed })
        }
    }

    @Test fun cancelledConnectionDoesNotOpenTheFallbackAndRemovesExistingPartialBytes() {
        val signal = UpdateCancellation()
        var opens = 0
        val client = UpdateClient {
            opens++; signal.cancel(); throw IOException("disconnected")
        }
        val file = temporary.newFile().apply { writeBytes(payload.copyOf(3)) }
        assertThrows(InterruptedIOException::class.java) {
            client.download(release("https://api.github.com/repos/huangwits/DiPlay-Preface/releases/assets/123"), file, signal) {}
        }
        assertEquals(1, opens)
        assertFalse(file.exists())
    }

    @Test fun releaseCatalogStillRequestsJson() {
        lateinit var response: Response
        val client = UpdateClient { url -> Response(url, byteArrayOf(), 500).also { response = it } }
        assertThrows(IOException::class.java) { client.latest(UpdateCancellation()) }
        assertEquals("application/vnd.github+json", response.getRequestProperty("Accept"))
    }

    @Test fun refusesRedirectsToPlainHttpOrUnrelatedHostsBeforeConnecting() {
        for (location in listOf("http://github.com/asset", "https://evil.test/asset", "https://github.com@evil.test/asset")) {
            var opens = 0
            val client = UpdateClient { url -> opens++; Response(url, byteArrayOf(), 302, mapOf("Location" to location)) }
            assertThrows(IOException::class.java) { client.download(release(), temporary.newFile(), UpdateCancellation()) {} }
            assertEquals(1, opens)
        }
    }

    @Test fun shortOversizedAndCorruptDownloadsAreRemoved() {
        for (data in listOf(payload.copyOf(3), payload + byteArrayOf(0), ByteArray(payload.size))) {
            val client = UpdateClient { url -> Response(url, data) }
            val file = temporary.newFile()
            assertThrows(IOException::class.java) { client.download(release(), file, UpdateCancellation()) {} }
            assertFalse(file.exists())
        }
    }

    @Test fun cancellationClosesConnectionAndRemovesPartialFile() {
        val signal = UpdateCancellation()
        lateinit var response: Response
        val client = UpdateClient { url -> Response(url, payload).also { response = it } }
        val file = temporary.newFile()
        assertThrows(InterruptedIOException::class.java) { client.download(release(), file, signal) { signal.cancel() } }
        assertTrue(response.closed)
        assertFalse(file.exists())
    }

    @Test fun failureAndRateLimitAreErrorsNotAnEmptyReleaseCatalog() {
        for (status in listOf(403, 429, 500)) {
            val client = UpdateClient { url -> Response(url, byteArrayOf(), status) }
            assertThrows(IOException::class.java) { client.latest(UpdateCancellation()) }
        }
    }
}

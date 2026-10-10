package com.shilapi.xcertplay.update

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class UpdateCatalogTest {
    private fun release(version: String, draft: Boolean = false, preview: Boolean = false): JSONObject {
        val name = "DiPlay-Preface-v$version.apk"
        return JSONObject().put("tag_name", "v$version").put("draft", draft).put("prerelease", preview)
            .put("body", "更新说明").put("assets", JSONArray().put(JSONObject().put("name", name)
                .put("browser_download_url", "${UpdateCatalog.REPOSITORY}/releases/download/v$version/$name")
                .put("size", 123).put("digest", "sha256:" + "a".repeat(64))))
    }

    @Test fun fullNumericVersionsIncludeEveryLocalRevision() {
        assertTrue(UpdateVersion.compare("v0.2.15.10", "0.2.15.9") > 0)
        assertTrue(UpdateVersion.compare("0.2.16.1", "0.2.15.99") > 0)
        assertTrue(UpdateVersion.compare("0.2.15.4.2", "0.2.15.4.1") > 0)
        assertEquals(0, UpdateVersion.compare("0.2.15", "0.2.15.0"))
        assertNull(UpdateVersion.parse("0.2.16.1-preview"))
        assertNull(UpdateVersion.parse("text 0.2.16"))
        assertNull(UpdateVersion.parse("../../0.2.16"))
    }

    @Test fun picksNewestStableRegardlessOfArrayOrderAndNeedsNoChecksumAttachment() {
        val json = JSONArray().put(release("0.2.15.9")).put(release("0.2.16.2", preview = true))
            .put(release("0.2.16.3", draft = true)).put(release("0.2.15.10"))
        val selected = UpdateCatalog.latest(json.toString())
        assertEquals("0.2.15.10", selected.version)
        assertEquals("a".repeat(64), selected.sha256)
        assertEquals(selected, UpdateCatalog.restore(selected.json().toString()))
    }

    @Test fun incompleteNewestReleaseIsAnErrorNotUpToDateOrAnOlderDownload() {
        val bad = release("0.2.16.1").put("assets", JSONArray())
        assertThrows(Exception::class.java) {
            UpdateCatalog.latest(JSONArray().put(bad).put(release("0.2.15.4")).toString())
        }
    }

    @Test fun refusesMissingDigestOtherRepositoriesAndAmbiguousAssets() {
        for (mutation in listOf<(JSONObject) -> Unit>(
            { it.remove("digest") },
            { it.put("digest", "sha256:invalid") },
            { it.put("browser_download_url", "https://github.com/carlito12345/DiPlay/releases/download/v0.2.16/DiPlay.apk") },
            { it.put("browser_download_url", "http://github.com/huangwits/DiPlay-Preface/releases/download/v0.2.16.1/DiPlay-Preface-v0.2.16.1.apk") },
            { it.put("name", "../../DiPlay.apk") },
            { it.put("size", UpdateCatalog.MAX_APK_SIZE + 1) }
        )) {
            val item = release("0.2.16.1")
            mutation(item.getJSONArray("assets").getJSONObject(0))
            assertThrows(Exception::class.java) { UpdateCatalog.latest(JSONArray().put(item).toString()) }
        }
        val duplicate = release("0.2.16.1")
        duplicate.getJSONArray("assets").put(duplicate.getJSONArray("assets").getJSONObject(0))
        assertThrows(Exception::class.java) { UpdateCatalog.latest(JSONArray().put(duplicate).toString()) }
    }
}

package com.shilapi.xcertplay

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.view.View
import com.shilapi.xcertplay.host.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 33], manifest = Config.NONE)
class AppLocaleTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun upgradeUsesChineseWithoutChangingOtherPreferencesOrSystemResources() {
        val base = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocale(Locale("ar")); setLayoutDirection(Locale("ar"))
        })
        val prefs = base.getSharedPreferences("diplay", Context.MODE_PRIVATE)
        prefs.edit().putString("app_language", "ar")
            .putBoolean("app_language_platform_migrated", true)
            .putString("unrelated_setting", "keep").commit()
        val wrapped = AppLocale.wrap(base)
        assertEquals(Locale.SIMPLIFIED_CHINESE, wrapped.resources.configuration.locale)
        assertEquals(View.LAYOUT_DIRECTION_LTR, wrapped.resources.configuration.layoutDirection)
        assertEquals("设置", wrapped.getString(R.string.settings))
        assertEquals("ar", base.resources.configuration.locale.language)
        assertFalse(prefs.contains("app_language"))
        assertFalse(prefs.contains("app_language_platform_migrated"))
        assertEquals("keep", prefs.getString("unrelated_setting", null))
    }

    @Test @Config(sdk = [33]) fun existingAndroidAppLanguageIsReplacedAndRepeatedWrapStaysChinese() {
        val manager = context.getSystemService(LocaleManager::class.java)
        manager.applicationLocales = LocaleList.forLanguageTags("ar")
        repeat(2) { AppLocale.wrap(context) }
        assertEquals("zh-CN", manager.applicationLocales.toLanguageTags())
    }

    @Test @Config(sdk = [23]) fun api22BranchDoesNotRequireLocaleManagerOrLocaleList() {
        val sdk = Build.VERSION.SDK_INT
        try {
            ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 22)
            val wrapped = AppLocale.wrap(context)
            assertEquals(Locale.SIMPLIFIED_CHINESE, wrapped.resources.configuration.locale)
            assertEquals("设置", wrapped.getString(R.string.settings))
        } finally {
            ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", sdk)
        }
    }

    @Test fun defaultResourcesStayChineseForServicesAndForeignSystemLanguages() {
        for (tag in listOf("en", "ar", "zh-TW")) {
            val base = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(tag))
            })
            assertEquals(tag, "设置", base.getString(R.string.settings))
        }
    }
}

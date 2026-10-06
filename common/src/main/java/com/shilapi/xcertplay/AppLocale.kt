// SPDX-License-Identifier: AGPL-3.0-only
package com.shilapi.xcertplay

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/** The Preface interface always uses Simplified Chinese, including upgrades from multilingual builds. */
object AppLocale {
    fun wrap(context: Context): Context {
        val preferences = context.getSharedPreferences("diplay", Context.MODE_PRIVATE)
        if (preferences.contains("app_language") || preferences.contains("app_language_platform_migrated")) {
            preferences.edit().remove("app_language").remove("app_language_platform_migrated").apply()
        }
        if (Build.VERSION.SDK_INT >= 33) {
            // Replace the old per-app language once; do not restore an RTL layout on upgrade.
            val manager = context.getSystemService(LocaleManager::class.java)
            if (manager != null && manager.applicationLocales.toLanguageTags() != "zh-CN") {
                manager.applicationLocales = LocaleList(Locale.SIMPLIFIED_CHINESE)
            }
        }
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(Locale.SIMPLIFIED_CHINESE)
            setLayoutDirection(Locale.SIMPLIFIED_CHINESE)
        }
        return context.createConfigurationContext(configuration)
    }
}

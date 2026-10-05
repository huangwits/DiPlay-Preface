package com.shilapi.xcertplay.compat

import android.content.Context
import android.os.Build

/** Typed Context.getSystemService was added in API 23; use the string overload on older Android. */
@Suppress("DEPRECATION")
fun <T> Context.systemService(type: Class<T>, name: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        getSystemService(type)
    } else {
        getSystemService(name) as? T
    }

/** Authentication and pairing state must stay outside Android backups. */
fun Context.appPrivateDir(): java.io.File =
    noBackupFilesDir

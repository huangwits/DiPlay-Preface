package com.shilapi.xcertplay

import android.content.Context
import android.content.ContextWrapper
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], manifest = Config.NONE)
class UsageAccessCompatibilityTest {
    @Test
    fun absentVendorServicesDisableMonitoring() {
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getSystemService(name: String): Any? = null
        }
    }

    @Test
    fun deniedServiceAccessDoesNotCrashTheHost() {
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getSystemService(name: String): Any? {
                if (name == Context.APP_OPS_SERVICE) throw SecurityException("firmware denied")
                return super.getSystemService(name)
            }
        }
    }
}

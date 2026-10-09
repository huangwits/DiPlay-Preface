package com.shilapi.xcertplay

import android.content.Context
import com.shilapi.xcertplay.adb.LocalAdb
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Resetter
import org.robolectric.shadows.ShadowSettings

/** One shadow type for the Kotlin singleton, including when Robolectric reuses its sandbox. */
@Implements(CarHotspotSetup::class, isInAndroidSdk = false)
internal class CarHotspotSetupShadow {
    @Implementation fun check(context: Context, adb: LocalAdb): LocalAdb.Access {
        workers += Thread.currentThread()
        checkEntered.countDown()
        return checkAccess
    }

    @Implementation fun grant(context: Context, permissions: List<CarHotspotSetup.Permission>, adb: LocalAdb): LocalAdb.Access {
        worker = Thread.currentThread()
        requested.addAll(permissions)
        entered.countDown()
        check(release.await(3, TimeUnit.SECONDS))
        check(!fail) { "ADB connection failed" }
        if (access == LocalAdb.Access.READY) {
            for (permission in permissions) {
                if (permission !in allowed) break
                when (permission) {
                    CarHotspotSetup.Permission.HOTSPOT -> CarHotspotAdbGrantTest.WritePermission.allowed = true
                    CarHotspotSetup.Permission.BOOT_LAUNCH -> ShadowSettings.setCanDrawOverlays(true)
                }
            }
        }
        return access
    }

    companion object {
        var entered = CountDownLatch(1)
        var release = CountDownLatch(1)
        var checkEntered = CountDownLatch(1)
        var allowed = CarHotspotSetup.Permission.entries.toSet()
        var access = LocalAdb.Access.READY
        var checkAccess = LocalAdb.Access.UNREACHABLE
        val requested = CopyOnWriteArrayList<CarHotspotSetup.Permission>()
        val workers = CopyOnWriteArrayList<Thread>()
        @Volatile var worker: Thread? = null
        @Volatile var fail = false

        @JvmStatic @Resetter fun reset() {
            entered = CountDownLatch(1)
            release = CountDownLatch(1)
            checkEntered = CountDownLatch(1)
            allowed = CarHotspotSetup.Permission.entries.toSet()
            access = LocalAdb.Access.READY
            checkAccess = LocalAdb.Access.UNREACHABLE
            requested.clear()
            workers.clear()
            worker = null
            fail = false
        }
    }
}

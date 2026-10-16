package com.shilapi.xcertplay.e01goc

import android.os.Binder
import android.os.Parcel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import com.geelyplay.preface.e01goc.E01RootAccess
import com.geelyplay.preface.e01goc.E01RootBridge

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], shadows = [RootServiceShadow::class])
class E01RootBridgeTest {
    @org.junit.Before fun resetService() { RootServiceShadow.factory = null }

    @Test fun probeRequiresRootUidAndSuccessfulCommandThroughFactoryBinder() {
        for ((output, expected) in listOf(
            "uid=0(root) gid=0(root)\n__DIPLAY_RC:0" to E01RootAccess.AVAILABLE,
            "uid=2000(shell) gid=2000(shell)\n__DIPLAY_RC:0" to E01RootAccess.NOT_ROOT,
            "gid=0 uid=0\n__DIPLAY_RC:0" to E01RootAccess.FAILED,
            "uid=0(root)\n__DIPLAY_RC:1" to E01RootAccess.FAILED,
            "uid=0(root)" to E01RootAccess.FAILED,
            "__DIPLAY_RC:0" to E01RootAccess.FAILED
        )) {
            service { output }
            assertEquals(output, expected, background { E01RootBridge.probeRoot() })
        }
    }

    @Test fun missingAndRejectedServicesDoNotReportRoot() {
        assertEquals(E01RootAccess.UNAVAILABLE, background { E01RootBridge.probeRoot() })
        service { throw SecurityException("denied") }
        assertEquals(E01RootAccess.FAILED, background { E01RootBridge.probeRoot() })
    }

    @Test fun timedOutBinderIsNotDuplicatedAndLaterChecksAreFresh() {
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        service {
            calls.incrementAndGet()
            check(release.await(5, TimeUnit.SECONDS))
            "uid=0(root)\n__DIPLAY_RC:0"
        }
        try {
            assertEquals(E01RootAccess.TIMED_OUT, background { E01RootBridge.probeRoot(100) })
            assertEquals(E01RootAccess.TIMED_OUT, background { E01RootBridge.probeRoot(100) })
            assertEquals(1, calls.get())
        } finally { release.countDown() }
        assertEquals(E01RootAccess.AVAILABLE, background { E01RootBridge.probeRoot() })
        service { "uid=2000(shell)\n__DIPLAY_RC:0" }
        assertEquals(E01RootAccess.NOT_ROOT, background { E01RootBridge.probeRoot() })
    }

    @Test fun hotspotWaitsForTheFactoryServiceAndStopsOnTimeoutOrCancellation() {
        val now = AtomicLong()
        val attempts = AtomicInteger()
        val ready = E01RootBridge.awaitServiceReady(
            timeoutMillis = 1000,
            isCancelled = { false },
            serviceAvailable = { attempts.incrementAndGet() >= 3 },
            sleepMillis = { now.addAndGet(it * 1_000_000L) },
            nowNanos = now::get,
        )
        assertTrue(ready)
        assertEquals(3, attempts.get())

        assertFalse(E01RootBridge.awaitServiceReady(
            timeoutMillis = 100,
            isCancelled = { true },
            serviceAvailable = { fail("cancelled startup must not query the service"); false },
            sleepMillis = {},
            nowNanos = { 0L },
        ))
        val timedOutAt = AtomicLong()
        val timeoutAttempts = AtomicInteger()
        assertFalse(E01RootBridge.awaitServiceReady(
            timeoutMillis = 100,
            isCancelled = { false },
            serviceAvailable = { timeoutAttempts.incrementAndGet(); false },
            sleepMillis = { timedOutAt.addAndGet(it * 1_000_000L) },
            nowNanos = timedOutAt::get,
        ))
        assertEquals(100_000_000L, timedOutAt.get())
        assertEquals(2, timeoutAttempts.get())
    }

    @Test fun savedHotspotRequiresBothTheHelperConfirmationAndSuccessfulCommand() {
        val apk = "/data/app/com.shihab.diplay.preface-1/base.apk"
        val command = "{ umask 077; CLASSPATH=$apk app_process /system/bin " +
            "com.geelyplay.preface.e01goc.E01RootHotspotMain; } 2>&1; rc=\$?; echo __DIPLAY_RC:\$rc"
        for ((output, expected) in listOf(
            "DIPLAY_HOTSPOT_ROOT:ready\n__DIPLAY_RC:0" to true,
            "DIPLAY_HOTSPOT_ROOT:failed\n__DIPLAY_RC:0" to false,
            "DIPLAY_HOTSPOT_ROOT:ready\n__DIPLAY_RC:1" to false,
            "DIPLAY_HOTSPOT_ROOT:ready" to false,
        )) {
            service(expectedCommand = command) { output }
            assertEquals(output, expected, background { E01RootBridge.startSavedHotspot(apk) })
        }
    }

    @Test fun cancelledSavedHotspotDoesNotExecuteTheFactoryCommand() {
        service { fail("cancelled hotspot must not execute a command"); "" }
        assertFalse(background {
            E01RootBridge.startSavedHotspot("/data/app/com.shihab.diplay.preface-1/base.apk") { true }
        })
    }

    @Test fun timedOutHotspotBinderIsNotDuplicatedAndALaterLaunchCanSucceed() {
        val apk = "/data/app/com.shihab.diplay.preface-1/base.apk"
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        service(expectedCommand = E01RootBridge.savedHotspotCommand(apk)) {
            calls.incrementAndGet()
            check(release.await(5, TimeUnit.SECONDS))
            "DIPLAY_HOTSPOT_ROOT:ready\n__DIPLAY_RC:0"
        }
        try {
            assertFalse(background { E01RootBridge.startSavedHotspot(apk, timeoutMillis = 100) })
            assertFalse(background { E01RootBridge.startSavedHotspot(apk, timeoutMillis = 100) })
            assertEquals(1, calls.get())
        } finally { release.countDown() }
        assertTrue(background { E01RootBridge.startSavedHotspot(apk) })
    }

    @Test fun cancellationReturnsWhileTheHotspotBinderIsStillRunning() {
        val apk = "/data/app/com.shihab.diplay.preface-1/base.apk"
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val cancelled = AtomicBoolean()
        service(expectedCommand = E01RootBridge.savedHotspotCommand(apk)) {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            "DIPLAY_HOTSPOT_ROOT:ready\n__DIPLAY_RC:0"
        }
        val request = FutureTask {
            E01RootBridge.startSavedHotspot(apk, timeoutMillis = 2000, isCancelled = cancelled::get)
        }
        Thread(request).start()
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            cancelled.set(true)
            assertFalse(request.get(1, TimeUnit.SECONDS))
            assertEquals(1L, release.count)
        } finally { release.countDown() }
        assertTrue(background { E01RootBridge.startSavedHotspot(apk) })
    }

    @Test fun savedHotspotCommandRejectsShellInputTraversalAndOversizedPaths() {
        for (path in listOf("/data/app/base.apk;id", "/data/app/../base.apk",
            "/data/app/" + "a".repeat(220) + "/base.apk")) {
            assertThrows(path, IllegalArgumentException::class.java) { E01RootBridge.savedHotspotCommand(path) }
        }
    }

    private fun service(
        expectedCommand: String = "{ /system/bin/id; } 2>&1; rc=\$?; echo __DIPLAY_RC:\$rc",
        output: () -> String,
    ) {
        val binder = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                assertEquals(6, code)
                data.enforceInterface("com.neusoft.alfus.os.IExtraUtilsService")
                assertEquals(8, data.readInt()); assertEquals(0, data.readInt())
                assertEquals(expectedCommand, String(data.createByteArray()!!, Charsets.UTF_8))
                val result = output()
                reply!!.writeNoException()
                reply.writeByteArray(result.toByteArray(Charsets.UTF_8))
                return true
            }
        }
        RootServiceShadow.factory = binder
    }

    private fun <T> background(block: () -> T): T {
        val task = FutureTask(block)
        Thread(task).start()
        return task.get(8, TimeUnit.SECONDS)
    }
}

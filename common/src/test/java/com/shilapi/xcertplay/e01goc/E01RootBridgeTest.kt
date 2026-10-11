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
import java.util.concurrent.atomic.AtomicInteger

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

    private fun service(output: () -> String) {
        val binder = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                assertEquals(6, code)
                data.enforceInterface("com.neusoft.alfus.os.IExtraUtilsService")
                assertEquals(8, data.readInt()); assertEquals(0, data.readInt())
                assertEquals("{ /system/bin/id; } 2>&1; rc=\$?; echo __DIPLAY_RC:\$rc",
                    String(data.createByteArray()!!, Charsets.UTF_8))
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

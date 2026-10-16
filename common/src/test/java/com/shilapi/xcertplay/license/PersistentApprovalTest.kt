package com.shilapi.xcertplay.license

import android.content.Context
import android.os.Looper
import com.shilapi.xcertplay.CarPlayHostActivity
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.io.IOException
import java.time.Duration
import com.geelyplay.preface.license.AppLicense
import com.geelyplay.preface.license.OnlineLicense

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], shadows = [BluetoothLicenseLayoutTest.LicensedResources::class])
class PersistentApprovalTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val prefs get() = app.getSharedPreferences("diplay-license", Context.MODE_PRIVATE)
    @Before fun setup() { prefs.edit().clear().commit(); coldRestart() }
    @After fun cleanup() { prefs.edit().clear().commit(); coldRestart() }
    private fun coldRestart() = ReflectionHelpers.setStaticField(OnlineLicense::class.java, "validUntilElapsed", 0L)

    @Test fun approvalSurvivesProcessCacheLossAndMonthsWithoutNetwork() {
        OnlineLicense.saveDecision(app, true, "ABCDEF123456", "same-installation")
        coldRestart()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofDays(180))
        assertTrue(AppLicense.canStart(app))
        assertEquals("ABCDEF123456", OnlineLicense.requestLabel(app))
        assertEquals("same-installation", OnlineLicense.deviceLabel(app))
        // A separate Context/SharedPreferences reload uses the same persisted decision.
        val reloaded = app.createPackageContext(app.packageName, 0)
        assertTrue(OnlineLicense.hasSavedApproval(reloaded))
    }

    @Test fun oldApprovedFlagMigratesWithoutRequiringAnOnlineVisit() {
        prefs.edit().putBoolean("activated", true).putBoolean("requested", true).commit()
        assertTrue(AppLicense.canStart(app))
        assertFalse(prefs.contains("authorized"))
    }

    @Test fun newAndPendingInstallationsCannotUseTheAppOffline() {
        assertFalse(AppLicense.canStart(app))
        prefs.edit().putBoolean("requested", true).commit()
        assertFalse(AppLicense.canStart(app))
    }

    @Test fun outageTlsAndMalformedResponsesPreserveApprovalButNeverGrantIt() {
        val failures = listOf(IOException("offline"), javax.net.ssl.SSLHandshakeException("TLS"),
            IllegalStateException("invalid signature"), org.json.JSONException("invalid response"))
        failures.forEach { OnlineLicense.refreshFailed(app, it); assertFalse(AppLicense.canStart(app)) }
        OnlineLicense.saveDecision(app, true)
        failures.forEach {
            OnlineLicense.refreshFailed(app, it); coldRestart()
            assertTrue(AppLicense.canStart(app))
        }
    }

    @Test fun denialSurvivesRestartAndCannotRemigrateTheHistoricalApproval() {
        prefs.edit().putBoolean("activated", true).commit()
        OnlineLicense.refreshFailed(app, OnlineLicense.AdmissionDenied())
        coldRestart()
        assertTrue(OnlineLicense.wasActivated(app))
        assertFalse(AppLicense.canStart(app))
        OnlineLicense.refreshFailed(app, IOException("offline"))
        assertFalse(AppLicense.canStart(app))
        OnlineLicense.saveDecision(app, true)
        coldRestart()
        assertTrue(AppLicense.canStart(app))
    }

    @Test fun pendingResponseClearsPriorApprovalDurably() {
        OnlineLicense.saveDecision(app, true)
        OnlineLicense.saveDecision(app, false, "ABCDEF123456", "same-installation")
        coldRestart()
        assertFalse(AppLicense.canStart(app))
        assertTrue(OnlineLicense.wasRequested(app))
    }

    @Test fun genericForbiddenAndExpiredChallengesDoNotBecomePersistentRevocation() {
        OnlineLicense.saveDecision(app, true)
        for (reason in listOf(null, "challenge_expired", "invalid_device_proof", "app_mismatch")) {
            OnlineLicense.refreshFailed(app, OnlineLicense.responseFailure(403, "/v1/refresh", reason))
            assertTrue(AppLicense.canStart(app))
        }
        OnlineLicense.refreshFailed(app, OnlineLicense.responseFailure(503, "/v1/refresh", "license_unavailable"))
        assertTrue(AppLicense.canStart(app))
        OnlineLicense.refreshFailed(app, OnlineLicense.responseFailure(403, "/v1/challenge", "license_unavailable"))
        assertTrue(AppLicense.canStart(app))
        for (reason in listOf("license_unavailable", "request_required")) {
            OnlineLicense.saveDecision(app, true)
            OnlineLicense.refreshFailed(app, OnlineLicense.responseFailure(403, "/v1/refresh", reason))
            coldRestart(); assertFalse(AppLicense.canStart(app))
        }
    }

    @Test fun currentHostKeepsAdmissionDuringRecoveryButNewHostHonorsDenial() {
        OnlineLicense.saveDecision(app, true)
        val host = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        ReflectionHelpers.setField(host, "wirelessEnabled", true)
        assertFalse(ReflectionHelpers.callInstanceMethod<Boolean>(host, "requireSessionAdmission"))
        OnlineLicense.refreshFailed(app, OnlineLicense.AdmissionDenied())
        assertFalse(AppLicense.canStart(app))
        assertFalse(ReflectionHelpers.callInstanceMethod<Boolean>(host, "requireSessionAdmission"))
        assertFalse(host.isFinishing)
        assertNull(shadowOf(host).nextStartedActivity)
        val fresh = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        ReflectionHelpers.setField(fresh, "wirelessEnabled", true)
        assertTrue(ReflectionHelpers.callInstanceMethod<Boolean>(fresh, "requireSessionAdmission"))
    }

    @Test fun usbNeverNeedsAnApproval() {
        val host = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        ReflectionHelpers.setField(host, "wirelessEnabled", false)
        assertFalse(ReflectionHelpers.callInstanceMethod<Boolean>(host, "requireSessionAdmission"))
        assertNull(shadowOf(host).nextStartedActivity)
    }
}

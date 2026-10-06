package com.shilapi.xcertplay

import android.os.Handler
import android.widget.TextView
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.orchestration.CarPlayStatus
import com.shilapi.xcertplay.network.WirelessStartupFailure
import java.util.concurrent.ExecutorService
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], qualifiers = "zh-rCN")
class CarPlayConnectionStageTest {
    private lateinit var activity: CarPlayHostActivity
    private lateinit var stage: TextView
    private lateinit var report: (CarPlayStatus) -> Unit

    @Before fun setup() {
        CarPlayBackgroundSession.clear()
        activity = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        stage = TextView(activity)
        activity.javaClass.getDeclaredField("stageStatusView").apply { isAccessible = true }.set(activity, stage)
        @Suppress("UNCHECKED_CAST")
        report = activity.javaClass.getDeclaredMethod("createStatusReporter", Int::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(activity, 0) as (CarPlayStatus) -> Unit
    }

    @After fun cleanup() {
        (activity.javaClass.getDeclaredField("mainHandler").apply { isAccessible = true }
            .get(activity) as Handler).removeCallbacksAndMessages(null)
        for (name in listOf("teardownExecutor", "airPlayCommandExecutor")) {
            (activity.javaClass.getDeclaredField(name).apply { isAccessible = true }
                .get(activity) as ExecutorService).shutdownNow()
        }
        CarPlayBackgroundSession.clear()
    }

    @Test fun chineseConnectionProgressSurvivesTheActualStatusReporter() = assertProgress()

    @Test @Config(qualifiers = "en")
    fun englishConnectionProgressKeepsItsSpecificStage() = assertProgress()

    @Test @Config(qualifiers = "es")
    fun spanishConnectionProgressSurvivesTheActualStatusReporter() = assertProgress()

    private fun assertProgress() {
        for ((status, resource) in listOf(
            CarPlayStatus.DiscoveringMfi to R.string.preparing_mfi_authentication,
            CarPlayStatus.MfiReady to R.string.mfi_authentication_ready,
            CarPlayStatus.StartingHotspot to R.string.starting_wireless_hotspot,
            CarPlayStatus.ConnectingBluetooth to R.string.connecting_bluetooth,
            CarPlayStatus.RunningWireless to R.string.wireless_carplay_control_running,
            CarPlayStatus.WirelessActive to R.string.wireless_carplay_active,
            CarPlayStatus.Pairing to R.string.pairing_with_iphone,
        )) {
            report(status)
            assertEquals(status.javaClass.simpleName, activity.getString(resource), stage.text.toString())
        }
    }

    @Test fun wifiTimeoutAndGenericFailureRemainVisibleInChinese() {
        report(CarPlayStatus.Failed("No AirPlay TCP after CarPlay StartSession",
            startupFailure = WirelessStartupFailure.FIRST_TCP_TIMEOUT))
        assertEquals(activity.getString(R.string.first_tcp_timeout), stage.text.toString())
        report(CarPlayStatus.Failed("Wireless CarPlay iAP2 control timed out",
            startupFailure = WirelessStartupFailure.IAP2_TIMEOUT))
        assertEquals(activity.getString(R.string.wireless_iap2_timeout), stage.text.toString())
        report(CarPlayStatus.Failed("iAP2 identification timed out"))
        assertTrue(stage.text.toString().contains("iAP2 identification timed out"))
    }

    @Test fun localizedPermissionDenialAndStoppedRetryExplanationArePreserved() {
        val setStage = activity.javaClass.getDeclaredMethod("setConnectionStage", String::class.java)
            .apply { isAccessible = true }
        for (message in listOf(activity.getString(R.string.vpn_consent_was_denied),
            activity.getString(R.string.first_tcp_timeout) + "\n" + activity.getString(R.string.wireless_startup_retries_exhausted))) {
            setStage.invoke(activity, message)
            assertEquals(message, stage.text.toString())
        }
    }
}

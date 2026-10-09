package com.shilapi.xcertplay

import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper

/** Uses the same preference, ambient sensor and hysteresis as the CarPlay waiting page. */
internal class ConnectionPageAppearance(private val context: Context, private val panel: ConnectionWaitingView,
    private val onThemeChanged: (Boolean) -> Unit = {}) {
    private val handler = Handler(Looper.getMainLooper())
    private var active = false
    private fun systemNight() = context.resources.configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    private val controller = CarPlayNightModeController(
        AndroidAmbientLight(context), MainThreadNightModeScheduler(), systemNight(), ::applyTheme,
    )
    private val poll = object : Runnable {
        override fun run() {
            if (!active) return
            controller.systemChanged(systemNight())
            handler.postDelayed(this, 2_000)
        }
    }
    fun resume() {
        controller.configure(AirPlayPersistence.loadCarPlayNightMode(context), systemNight(),
            AirPlayPersistence.loadAmbientLightThreshold(context), AirPlayPersistence.loadAmbientDelaySeconds(context),
            AirPlayPersistence.loadCarPlayNightSchedule(context))
        controller.resume(systemNight())
        applyTheme(controller.night)
        active = true
        handler.removeCallbacks(poll)
        handler.postDelayed(poll, 2_000)
    }
    private fun applyTheme(night: Boolean) { panel.applyTheme(night); onThemeChanged(night) }
    fun configurationChanged() { controller.systemChanged(systemNight()); applyTheme(controller.night) }
    fun pause() { active = false; handler.removeCallbacks(poll); controller.pause() }
}

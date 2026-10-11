package com.shilapi.xcertplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

/** The factory's ordered voice-key event. Register only while the CarPlay screen is resumed. */
internal class E01VoiceKeyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!isOrderedBroadcast || intent.action != ACTION ||
            intent.getIntExtra(EVENT, -1) != 200231 || intent.getIntExtra(PRESS, -1) !in 0..1) return
        // Leave the original assistant available if Siri is disabled, disconnected or rejects the request.
        if (CarPlayMediaKeys.requestSteeringSiri("factory_broadcast")) abortBroadcast()
    }

    fun register(context: Context) {
        val filter = IntentFilter(ACTION).apply { priority = 1000; addCategory(Intent.CATEGORY_DEFAULT) }
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(this, filter, Context.RECEIVER_EXPORTED)
        else {
            @Suppress("DEPRECATION")
            context.registerReceiver(this, filter)
        }
    }

    companion object {
        const val ACTION = "ecarx.intent.action.ECARX_KEY_RVOICEASSIST_EVENT"
        const val EVENT = "ecarx.extra.ECARX_KEY_EVENT_TYPE"
        const val PRESS = "ecarx.extra.ECARX_KEY_ACTION_TYPE"
    }
}

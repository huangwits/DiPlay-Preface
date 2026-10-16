package com.geelyplay.preface

import android.content.Context
import android.view.KeyEvent
import com.shilapi.xcertplay.host.R

internal enum class FactoryKeyAction(val label: String, val defaults: Set<Int>) {
    PREVIOUS("上一曲", setOf(89, 88)), NEXT("下一曲", setOf(87)), SIRI("Siri", setOf(231, 219)),
    FLYSCREEN("飞屏（OK）", setOf(KeyEvent.KEYCODE_DPAD_CENTER)),
}

/** One Android-key mapping, shared by foreground and MediaSession delivery. */
internal object FactorySteeringKeys {
    fun enabled(context: Context): Boolean = context.resources.getBoolean(R.bool.config_factory_bluetooth_default)
    private fun prefs(context: Context) = context.getSharedPreferences("factory_steering_keys", Context.MODE_PRIVATE)
    fun keys(context: Context, action: FactoryKeyAction): Set<Int> = prefs(context).getStringSet(action.name, null)
        ?.mapNotNull { it.toIntOrNull()?.takeIf(::learnable) }?.toSet() ?: action.defaults
    fun action(context: Context, key: Int): FactoryKeyAction? = FactoryKeyAction.entries.firstOrNull { key in keys(context, it) }
    fun learnable(key: Int): Boolean = key in 1..1000 && key !in setOf(
        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_POWER,
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
    )
    fun assign(context: Context, action: FactoryKeyAction, key: Int?) {
        require(key == null || learnable(key))
        val editor = prefs(context).edit()
        FactoryKeyAction.entries.forEach { candidate ->
            val next = if (candidate == action) setOfNotNull(key) else keys(context, candidate) - setOfNotNull(key)
            editor.putStringSet(candidate.name, next.map(Int::toString).toSet())
        }
        editor.apply()
    }
    fun reset(context: Context) { prefs(context).edit().clear().apply() }
    fun replacesDefault(context: Context, key: Int): Boolean = FactoryKeyAction.entries.any { key in it.defaults } || action(context, key) != null

    fun dispatch(context: Context, event: KeyEvent, siriEnabled: Boolean, send: (FactoryKeyAction) -> Unit): Boolean {
        val action = action(context, event.keyCode)
        if (action == FactoryKeyAction.SIRI && !siriEnabled) return false
        if (action != null && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && !event.isCanceled) send(action)
        return replacesDefault(context, event.keyCode)
    }
}

package com.shilapi.xcertplay

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import com.shilapi.xcertplay.host.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class CarHotspotUiTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun connectionSettingsDoNotExposeAnAutomaticHotspotSwitch() {
        val screen = Robolectric.buildActivity(
            DiPlayActivity::class.java,
            Intent(context, DiPlayActivity::class.java)
                .putExtra("page", "settings")
                .putExtra("settings_category", SettingsCategory.CONNECTION.name),
        ).setup().get()
        val title = screen.getString(R.string.auto_car_hotspot_title)
        val index = ReflectionHelpers.callInstanceMethod<List<Any>>(screen, "buildSettingsSearchIndex")

        assertFalse(index.any { ReflectionHelpers.getField<String>(it, "title") == title })
        assertFalse(descendants(screen.window.decorView).filterIsInstance<Switch>().any {
            it.contentDescription == title
        })
    }

    @Test fun homeKeepsThePrimaryConnectionActionVisiblySeparateFromItsStatus() {
        val screen = Robolectric.buildActivity(DiPlayActivity::class.java, Intent(context, DiPlayActivity::class.java))
            .setup().get()
        val status = ReflectionHelpers.getField<TextView>(screen, "status")!!
        val connect = ReflectionHelpers.getField<Button>(screen, "connectButton")!!
        val card = status.parent as ViewGroup
        val divider = card.getChildAt(card.indexOfChild(connect) - 1)

        assertSame(card, connect.parent)
        assertTrue(divider.tag == "home-primary-action-divider")
        val margins = divider.layoutParams as LinearLayout.LayoutParams
        val minGap = Math.round(32 * screen.resources.displayMetrics.density)
        assertTrue(margins.topMargin + margins.bottomMargin >= minGap)
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
        }
    }
}

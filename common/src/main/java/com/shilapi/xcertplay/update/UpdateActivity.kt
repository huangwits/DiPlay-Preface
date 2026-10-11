package com.shilapi.xcertplay.update

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.shilapi.xcertplay.DiPlayActivity

/** Keeps restored old tasks usable after moving updates into the existing settings cards. */
class UpdateActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, DiPlayActivity::class.java).putExtra("page", "about")
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }
}

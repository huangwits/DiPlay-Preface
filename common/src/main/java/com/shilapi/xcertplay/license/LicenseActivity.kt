package com.shilapi.xcertplay.license

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.shilapi.xcertplay.DiPlayActivity
import com.shilapi.xcertplay.e01goc.E01GocActivity

/** Legacy entry point: wireless authorization is in the tools; USB resumes ordinary connection. */
class LicenseActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val wireless = intent.getBooleanExtra("license_wireless", true)
        val destination = if (wireless) Intent(this, E01GocActivity::class.java)
            .putExtra("license_wireless", true)
        else Intent(this, DiPlayActivity::class.java)
            .putExtra("authorized_connection", "usb")
            .putExtra("authorization_return", true)
        startActivity(destination.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }
}

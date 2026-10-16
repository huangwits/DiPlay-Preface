package com.geelyplay.preface.license

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.Closeable

/** Owned by an established wireless CarPlay session, including background playback.
 * Revalidation only updates admission for future connections; it has no session control API.
 */
internal class LicenseRevalidation(
    context: Context,
    private val refresh: () -> Unit = { OnlineLicense.refresh(context.applicationContext) },
    private val eligible: () -> Boolean = { OnlineLicense.enabled(context) && OnlineLicense.wasActivated(context) },
    private val networkAvailable: () -> Boolean = {
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)
            ?.activeNetworkInfo?.isConnected == true
    },
    private val execute: (() -> Unit) -> Unit = { work -> Thread(work, "diplay-license-revalidation").start() },
) : Closeable {
    private val connectivity = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val handler = Handler(Looper.getMainLooper())
    private var started = false
    private var registered = false
    private var working = false
    private var generation = 0
    private var retryAfter = 0L
    private val poll = Runnable { checkNow() }
    internal val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { handler.post { if (started) checkNow() } }
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            handler.post { if (started) checkNow() }
        }
    }

    fun start() {
        if (started || !eligible()) return
        started = true
        // API 21 callback works on E01/API 22. A local CarPlay link alone is not proof of internet;
        // HTTPS still verifies the endpoint and retries later if the link has no usable route.
        registered = runCatching {
            val manager = connectivity ?: return@runCatching false
            manager.registerNetworkCallback(NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), networkCallback)
            true
        }.getOrDefault(false)
        checkNow()
    }

    private fun checkNow() {
        handler.removeCallbacks(poll)
        if (!started) return
        handler.postDelayed(poll, 60_000L)
        if (working || !eligible() || SystemClock.elapsedRealtime() < retryAfter ||
            !runCatching(networkAvailable).getOrDefault(false)) return
        working = true
        val current = generation
        retryAfter = SystemClock.elapsedRealtime() + 60_000L
        execute {
            runCatching(refresh)
            handler.post {
                working = false
                if (started && generation == current) {
                    retryAfter = SystemClock.elapsedRealtime() + 60_000L
                }
            }
        }
    }

    override fun close() {
        started = false
        generation++
        handler.removeCallbacks(poll)
        if (registered) runCatching { connectivity?.unregisterNetworkCallback(networkCallback) }
        registered = false
    }
}

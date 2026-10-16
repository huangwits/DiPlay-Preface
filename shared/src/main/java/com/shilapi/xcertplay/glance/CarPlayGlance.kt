package com.shilapi.xcertplay.glance

import com.geelyplay.preface.hud.CarPlayRouteState
import com.geelyplay.preface.hud.ClusterSongState
import com.shilapi.xcertplay.iap2.wire.Iap2Frame

/**
 * What CarPlay is doing, at a glance, for widgets and other screens outside CarPlay: the next
 * maneuver from iAP2 route guidance (0x5201/0x5202) and the song from NowPlayingUpdate (0x5001).
 * Fed by every CarPlay session, whatever the BYD output settings.
 */
object CarPlayGlance {
    data class RouteFrameStats(val guidanceUpdates: Long, val maneuverUpdates: Long)

    /** One state of the glance; [maneuverType] is Apple's RouteGuidanceManeuverType, null without a route. */
    data class Snapshot(
        val connected: Boolean = false,
        val navigationActive: Boolean = false,
        val navigationArrived: Boolean = false,
        val maneuverType: Int? = null,
        val drivingSide: Int = 0,
        val distanceMeters: Int = 0,
        val road: String = "",
        val arrivalEpochSeconds: Long? = null,
        val remainingSeconds: Long? = null,
        val remainingMeters: Long? = null,
        val song: String? = null,
        val playing: Boolean = false,
    )

    private val route = CarPlayRouteState()
    private val song = ClusterSongState()
    private var connected = false
    private var last = Snapshot()
    private var routeGuidanceFrames = 0L
    private var routeManeuverFrames = 0L

    /** Called with each new snapshot, on the thread that changed it. */
    @Volatile var listener: ((Snapshot) -> Unit)? = null

    /** Refresh time-dependent guidance even when no new metadata frame has arrived. */
    fun snapshot(): Snapshot {
        val (changed, current) = synchronized(this) { publishLocked() to last }
        changed?.let { listener?.invoke(it) }
        return current
    }

    fun onFrame(frame: Iap2Frame) {
        val changed = synchronized(this) {
            when (frame.messageId) {
                CarPlayRouteState.ROUTE_GUIDANCE_UPDATE -> {
                    routeGuidanceFrames++
                    runCatching { route.accept(frame.messageId, frame.payload) }
                }
                CarPlayRouteState.ROUTE_GUIDANCE_MANEUVER_UPDATE -> {
                    routeManeuverFrames++
                    runCatching { route.accept(frame.messageId, frame.payload) }
                }
                ClusterSongState.NOW_PLAYING_UPDATE -> runCatching { song.accept(frame) }
                else -> return
            }
            publishLocked()
        }
        changed?.let { listener?.invoke(it) }
    }

    fun setConnected(next: Boolean) {
        val changed = synchronized(this) {
            if (connected == next) return
            connected = next
            if (next) {
                routeGuidanceFrames = 0
                routeManeuverFrames = 0
            } else {
                route.clear()
                song.clear()
            }
            publishLocked()
        }
        changed?.let { listener?.invoke(it) }
    }

    /** Counts CarPlay route metadata frames without retaining road names or destination details. */
    fun routeFrameStats(): RouteFrameStats = synchronized(this) {
        RouteFrameStats(routeGuidanceFrames, routeManeuverFrames)
    }

    private fun publishLocked(): Snapshot? {
        val maneuver = route.currentApple()
        val current = song.current()
        val next = Snapshot(
            connected = connected,
            navigationActive = route.isNavigating(),
            navigationArrived = route.arrivalActive(),
            maneuverType = maneuver?.type,
            drivingSide = maneuver?.drivingSide ?: 0,
            distanceMeters = maneuver?.distanceMeters ?: 0,
            road = maneuver?.road.orEmpty(),
            arrivalEpochSeconds = maneuver?.arrivalEpochSeconds,
            remainingSeconds = maneuver?.remainingSeconds,
            remainingMeters = maneuver?.remainingMeters,
            song = current?.text,
            playing = current?.playing ?: false,
        )
        if (next == last) return null
        last = next
        return next
    }
}

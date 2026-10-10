package com.shilapi.xcertplay.hud

/** The next CarPlay maneuver in the compact form needed by secondary-display HUDs. */
data class CarPlayHudGuidance(
    val distanceMeters: Int,
    val maneuverCode: Int,
    val road: String,
    val appleManeuver: Int = 0,
    val drivingSide: Int = 0,
    val remainingSeconds: Long? = null,
    val remainingMeters: Long? = null,
)

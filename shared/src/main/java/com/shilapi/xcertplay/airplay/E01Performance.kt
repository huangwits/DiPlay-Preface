package com.shilapi.xcertplay.airplay

/** Conservative starting point for MT6735; actual smoothness still needs a head-unit test. */
object E01Performance {
    const val MAX_LONG_EDGE = 960
    const val MAX_SHORT_EDGE = 540
    const val FPS = 30
    private val mt6735 = Regex("(?:^|[^a-z0-9])mt6735[a-z]?(?:$|[^a-z0-9])", RegexOption.IGNORE_CASE)

    // E01 alone is not a unique Android model name. Do not guess from CPU bitness or RAM.
    fun matchesHardware(vararg identifiers: String): Boolean = identifiers.any { mt6735.containsMatchIn(it) }

    /** Apply before calculating screen-relative safe-area insets. Keep physical size and input semantics. */
    fun display(requested: AirPlayDisplayConfig): AirPlayDisplayConfig {
        require(requested.widthPixels > 0 && requested.heightPixels > 0)
        val longEdge = maxOf(requested.widthPixels, requested.heightPixels)
        val shortEdge = minOf(requested.widthPixels, requested.heightPixels)
        val ratio = minOf(1.0, MAX_LONG_EDGE.toDouble() / longEdge, MAX_SHORT_EDGE.toDouble() / shortEdge)
        fun scaled(pixels: Int): Int = ((pixels * ratio).toInt() / 2 * 2).coerceAtLeast(2)
        return requested.copy(widthPixels = scaled(requested.widthPixels),
            heightPixels = scaled(requested.heightPixels), fps = minOf(requested.fps, FPS))
    }
}

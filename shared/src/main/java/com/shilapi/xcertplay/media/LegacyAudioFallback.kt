package com.shilapi.xcertplay.media

/** A ROM can reject a legacy stream by throwing or returning an uninitialized track. */
internal object LegacyAudioFallback {
    fun <T> buildCandidates(
        streamTypes: List<Int>,
        create: (Int) -> T,
        isInitialized: (T) -> Boolean,
        release: (T) -> Unit,
    ): Pair<Int, T>? {
        for (streamType in streamTypes.distinct()) {
            val candidate = try { create(streamType) } catch (_: RuntimeException) { null } ?: continue
            if (isInitialized(candidate)) return streamType to candidate
            release(candidate)
        }
        return null
    }

    fun <T> build(
        createLegacy: () -> T,
        isInitialized: (T) -> Boolean,
        release: (T) -> Unit,
        createFallback: () -> T,
    ): T {
        val candidate = try { createLegacy() } catch (_: RuntimeException) { null }
        if (candidate != null) {
            if (isInitialized(candidate)) return candidate
            release(candidate)
        }
        return createFallback()
    }
}

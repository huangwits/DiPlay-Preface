package com.shilapi.xcertplay.airplay

/** Only product/version tokens, never the phone's name, addresses or pairing identifiers. */
object AirPlayPeerDiagnostics {
    private val modelToken = Regex("(?:iPhone|iPad|iPod)[0-9]{1,3},[0-9]{1,3}")
    private val versionToken = Regex("[0-9][0-9A-Za-z._-]{0,39}")

    fun summary(model: String, osVersion: String, sourceVersion: String): String {
        fun version(value: String) = value.takeIf(versionToken::matches) ?: "not_reported"
        return "Phone display peer model=${model.takeIf(modelToken::matches) ?: "not_reported"} " +
            "ios=${version(osVersion)} airplay=${version(sourceVersion)}"
    }

    /** Capability flags only: never retain arbitrary phone URLs or their parameters. */
    fun clusterContentSummary(value: Any?): String {
        val urls = value as? List<*> ?: return "Phone map offer list=unrecognized"
        val map = urls.contains(CarPlayClusterDisplay.MAP_URL)
        val card = urls.contains(CarPlayClusterDisplay.Content.TURN_CARD.url)
        val instruments = urls.contains(CarPlayClusterDisplay.Content.INSTRUMENTS.url)
        return "Phone map offer entries=${urls.size} mapUrlListed=$map turnCardListed=$card instrumentsListed=$instruments"
    }
}

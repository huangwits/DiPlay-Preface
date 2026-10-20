package com.shilapi.xcertplay.airplay

import org.junit.Assert.*
import org.junit.Test

class AirPlayPeerDiagnosticsTest {
    @Test fun productAndVersionDetailsAreAvailableWithoutIdentity() {
        assertEquals("Phone display peer model=iPhone11,2 ios=18.7.10 airplay=950.7.1",
            AirPlayPeerDiagnostics.summary("iPhone11,2", "18.7.10", "950.7.1"))
    }

    @Test fun absentOrUnexpectedFieldsCannotLeakPersonalOrMultilineValues() {
        val summary = AirPlayPeerDiagnostics.summary("Jane's iPhone", "26.0\npassword=secret", "")
        assertEquals("Phone display peer model=not_reported ios=not_reported airplay=not_reported", summary)
    }

    @Test fun mapContentFlagsNeverIncludeArbitraryPhoneUrls() {
        val privateUrl = "maps:/private/location?token=secret"
        assertEquals("Phone map offer entries=3 mapUrlListed=true turnCardListed=true instrumentsListed=false",
            AirPlayPeerDiagnostics.clusterContentSummary(listOf(CarPlayClusterDisplay.MAP_URL,
                CarPlayClusterDisplay.Content.TURN_CARD.url, privateUrl)))
        assertEquals("Phone map offer list=unrecognized", AirPlayPeerDiagnostics.clusterContentSummary(privateUrl))
    }
}

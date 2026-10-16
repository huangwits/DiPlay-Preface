package com.shilapi.xcertplay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.geelyplay.preface.CarPlayShutdownGate

class CarPlayShutdownGateTest {
    @Test fun applicationExitUpgradesAnAlreadyRunningSessionShutdown() {
        val gate = CarPlayShutdownGate()

        assertTrue(gate.begin(terminate = false))
        assertFalse(gate.shouldTerminateProcess())
        assertFalse(gate.begin(terminate = true))

        assertTrue(gate.get())
        assertTrue(gate.shouldTerminateProcess())
        assertFalse(gate.begin(terminate = true))
    }

    @Test fun repeatedExitRequestsRemainIdempotent() {
        val gate = CarPlayShutdownGate()

        assertTrue(gate.begin(terminate = true))
        repeat(3) { assertFalse(gate.begin(terminate = true)) }
        assertTrue(gate.shouldTerminateProcess())
    }
}

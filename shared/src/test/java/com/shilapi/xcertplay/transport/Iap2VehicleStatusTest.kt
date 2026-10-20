package com.shilapi.xcertplay.transport

import com.shilapi.xcertplay.iap2.wire.Iap2ParameterList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class Iap2VehicleStatusTest {
    private val reading = VehicleStatusSnapshot(
        outsideTemperatureCelsius = 18.0,
        barometricPressureMillibar = 1008,
        passengerSeatOccupied = true,
        wipers = VehicleWiperStatus(washerOn = false, wipeDurationMillis = 1200, waitDurationMillis = 5000),
    )

    @Test fun reportsOnlySignalsPresentInTheSelectedGenericProfile() {
        assertEquals(setOf(4, 17, 18, 20), Iap2VehicleStatus.capabilities(reading))
        assertTrue(Iap2VehicleStatus.capabilities(null).isEmpty())
        assertFalse(Iap2VehicleStatus.capabilities(VehicleStatusSnapshot()).isNotEmpty())
    }

    @Test fun onlyAdvertisedAndRequestedGenericSignalsReachThePhone() {
        val frame = Iap2VehicleStatus.update(reading,
            advertised = setOf(4, 17, 18, 20),
            requested = setOf(4, 20))
        val fields = Iap2ParameterList.parse(requireNotNull(frame).payload)
        assertEquals(18, ByteBuffer.wrap(fields.first(4)!!.payload).short.toInt())
        assertEquals(1, fields.first(20)!!.payload.single().toInt())
        assertNull(fields.first(11))
        assertNull(fields.first(24))
        assertNull(fields.first(17))
        assertNull(fields.first(18))
        assertNull(fields.first(21))
        assertNull(fields.first(22))
        assertNull(fields.first(23))
        assertNull(fields.first(25))
        assertNull(fields.first(30))
    }

    @Test fun identificationDeclaresGenericProfileFieldsWithoutEvConnectorOrEngineMetadata() {
        val config = identification().copy(vehicleStatusCapabilities = setOf(4))
        val fields = Iap2ParameterList.parse(Iap2IdentificationClient.identificationInformation(config).payload)
        val sent = u16Values(fields.first(6)!!.payload)
        val received = u16Values(fields.first(7)!!.payload)
        assertTrue(Iap2VehicleStatus.UPDATE in sent)
        assertTrue(Iap2VehicleStatus.START in received)
        assertTrue(Iap2VehicleStatus.STOP in received)
        val vehicle = Iap2ParameterList.parse(fields.first(20)!!.payload)
        assertNull(vehicle.first(2))
        assertTrue(vehicle.all(11).isEmpty())
        val reported = Iap2ParameterList.parse(fields.first(21)!!.payload)
        assertEquals(setOf(4), reported.asList().map { it.id }.filter { it !in setOf(0, 1) }.toSet())
    }

    @Test fun currentProtocolCapabilitiesAreDerivedFromAReadOnlyProviderSnapshot() {
        val config = identification()
        assertTrue(config.withVehicleStatusFrom(null).vehicleStatusCapabilities.isEmpty())
        val provider = object : VehicleStatusProvider {
            override fun snapshot() = reading
        }
        assertEquals(Iap2VehicleStatus.capabilities(reading),
            config.withVehicleStatusFrom(provider).vehicleStatusCapabilities)
    }

    private fun identification() = Iap2IdentificationConfig(
        name = "DiPlay",
        modelIdentifier = "Preface",
        manufacturer = "Geely",
        serialNumber = "test",
        firmwareVersion = "1",
        hardwareVersion = "1",
        carPlayUsbInterfaceNumber = 3,
    )

    private fun u16Values(bytes: ByteArray): List<Int> =
        List(bytes.size / 2) { index ->
            ((bytes[index * 2].toInt() and 0xff) shl 8) or (bytes[index * 2 + 1].toInt() and 0xff)
        }
}

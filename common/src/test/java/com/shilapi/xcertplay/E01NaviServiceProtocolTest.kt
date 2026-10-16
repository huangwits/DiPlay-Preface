package com.shilapi.xcertplay

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import com.shilapi.xcertplay.hud.CarPlayHudGuidance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.geelyplay.preface.E01NaviServiceProtocol

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], manifest = Config.NONE)
class E01NaviServiceProtocolTest {
    private data class NavigationInfo(
        val status: Int,
        val turnId: Int,
        val road: String?,
        val nextDistance: Long,
        val destinationDistance: Long,
        val eta: Long,
        val drivingDirection: Int,
        val mute: Int,
        val laneCount: Int,
        val lanes: List<*>?,
        val laneIcons: Map<*, *>?,
        val etaDistance: Int,
        val etaTime: Int,
        val cameraType: Int,
        val cameraDistance: Int,
        val speedLimit: Int,
    )

    private data class Call(val code: Int, val info: NavigationInfo? = null, val message: String? = null)

    private class RecordingBinder : Binder() {
        val calls = mutableListOf<Call>()

        @Suppress("DEPRECATION")
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            data.enforceInterface(E01NaviServiceProtocol.DESCRIPTOR)
            val call = when (code) {
                E01NaviServiceProtocol.TRANSACTION_UPDATE_NAVIGATION_INFO -> {
                    assertEquals(1, data.readInt())
                    val status = data.readInt()
                    val turnId = data.readInt()
                    data.readString()
                    val road = data.readString()
                    val nextDistance = data.readLong()
                    val destinationDistance = data.readLong()
                    val eta = data.readLong()
                    val direction = data.readInt()
                    val mute = data.readInt()
                    val laneCount = data.readInt()
                    val lanes = data.readArrayList(java.util.ArrayList::class.java.classLoader)
                    val laneIcons = data.readHashMap(java.util.HashMap::class.java.classLoader)
                    data.readString()
                    data.readString()
                    val etaDistance = data.readInt()
                    val etaTime = data.readInt()
                    data.readInt()
                    data.readString()
                    data.readInt()
                    data.readInt()
                    val cameraType = data.readInt()
                    val cameraDistance = data.readInt()
                    val speedLimit = data.readInt()
                    Call(code, NavigationInfo(status, turnId, road, nextDistance, destinationDistance, eta,
                        direction, mute, laneCount, lanes, laneIcons, etaDistance, etaTime,
                        cameraType, cameraDistance, speedLimit))
                }
                E01NaviServiceProtocol.TRANSACTION_DESTINATION_REACHED -> Call(code, message = data.readString())
                else -> Call(code)
            }
            calls += call
            reply?.writeNoException()
            if (code == E01NaviServiceProtocol.TRANSACTION_UPDATE_NAVIGATION_INFO) reply?.writeInt(0)
            return true
        }
    }

    @Test fun binderCodesAndParcelableFieldOrderMatchTheInspectedFirmware() {
        val remote: IBinder = RecordingBinder()
        val guidance = CarPlayHudGuidance(
            distanceMeters = 180,
            maneuverCode = 2,
            road = "人民路",
            appleManeuver = 2,
            remainingSeconds = 600,
            remainingMeters = 4_200,
        )

        E01NaviServiceProtocol.transact(remote, E01NaviServiceProtocol.TRANSACTION_TURN_BY_TURN_STARTED)
        E01NaviServiceProtocol.transact(remote, E01NaviServiceProtocol.TRANSACTION_UPDATE_NAVIGATION_INFO) {
            writeInt(1)
            E01NaviServiceProtocol.writeNavigationInfo(this, guidance, active = true)
        }
        E01NaviServiceProtocol.transact(remote, E01NaviServiceProtocol.TRANSACTION_DESTINATION_REACHED) {
            writeString("")
        }
        E01NaviServiceProtocol.transact(remote, E01NaviServiceProtocol.TRANSACTION_UPDATE_NAVIGATION_INFO) {
            writeInt(1)
            E01NaviServiceProtocol.writeNavigationInfo(this, null, active = false)
        }
        E01NaviServiceProtocol.transact(remote, E01NaviServiceProtocol.TRANSACTION_TURN_BY_TURN_STOPPED)

        val calls = (remote as RecordingBinder).calls
        assertEquals(listOf(5, 1, 2, 1, 6), calls.map { it.code })
        assertEquals("", calls[2].message)
        val info = calls[1].info!!
        assertEquals(1, info.status)
        assertEquals(102, info.turnId)
        assertEquals("人民路", info.road)
        assertEquals(180L, info.nextDistance)
        assertEquals(4_200L, info.destinationDistance)
        assertEquals(-1L, info.eta)
        assertEquals(-1, info.drivingDirection)
        assertEquals(-1, info.mute)
        assertEquals(0, info.laneCount)
        assertTrue(info.lanes!!.isEmpty())
        assertTrue(info.laneIcons!!.isEmpty())
        assertEquals(listOf(-1, -1, -1, -1, -1), listOf(
            info.etaDistance, info.etaTime, info.cameraType, info.cameraDistance, info.speedLimit,
        ))
        assertEquals(0, calls[3].info!!.status)
    }

    @Test fun onlyFirmwaresTurnIdsHandledByTheHudMapperAreProduced() {
        assertEquals(101, E01NaviServiceProtocol.navigationTurnId(1, 0))
        assertEquals(102, E01NaviServiceProtocol.navigationTurnId(2, 0))
        assertEquals(111, E01NaviServiceProtocol.navigationTurnId(3, 0))
        assertEquals(109, E01NaviServiceProtocol.navigationTurnId(4, 0))
        assertEquals(110, E01NaviServiceProtocol.navigationTurnId(4, 1))
        assertEquals(127, E01NaviServiceProtocol.navigationTurnId(30, 0))
        assertEquals(137, E01NaviServiceProtocol.navigationTurnId(30, 1))
        assertEquals(0, E01NaviServiceProtocol.navigationTurnId(6, 0))
        assertEquals(0, E01NaviServiceProtocol.navigationTurnId(99, 0))
    }

    @Test fun aRejectedBinderTransactionIsNotReportedAsDelivered() {
        val remote = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int) = false
        }
        assertFalse(E01NaviServiceProtocol.transact(remote,
            E01NaviServiceProtocol.TRANSACTION_TURN_BY_TURN_STARTED))
    }
}

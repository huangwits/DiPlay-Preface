package com.shilapi.xcertplay

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.hardware.usb.*
import android.os.Looper
import android.os.SystemClock
import com.shilapi.xcertplay.airplay.*
import com.shilapi.xcertplay.orchestration.*
import com.shilapi.xcertplay.transport.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 28], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class WiredUsbRecoveryTest {
    private val usb = mock(UsbManager::class.java)
    private val app = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
        override fun getApplicationContext(): Context = this
        override fun getSystemService(name: String): Any? = if (name == USB_SERVICE) usb else super.getSystemService(name)
        override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean = false
    }
    private val statuses = mutableListOf<CarPlayStatus>()
    private val controllers = mutableListOf<CarPlayController>()

    @After fun close() {
        controllers.forEach { it.close(); assertTrue(it.awaitClosed(2_000)) }
    }

    @Test fun configurationInspectionDoesNotOpenThePhoneOrRequirePermission() {
        val host = IphoneUsbHost(app, usb, IphoneUsbMatcher.appleVendor())
        assertTrue(host.hasCarPlayConfiguration(phone("old", configured = true)))
        assertFalse(host.hasCarPlayConfiguration(phone("old", configured = false)))
        verifyNoInteractions(usb)
    }

    @Test fun lostAttachBroadcastStillFindsNewDeviceAndRequestsItsPermissionOnce() {
        val controller = controller()
        val newPhone = phone("new")
        `when`(usb.deviceList).thenReturn(hashMapOf())
        poll(controller)
        `when`(usb.deviceList).thenReturn(hashMapOf("new" to newPhone))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        verify(usb, times(1)).requestPermission(eq(newPhone), any())
        attach(controller, newPhone)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        verify(usb, times(1)).requestPermission(eq(newPhone), any())
    }

    @Test fun reusedDeviceNameWithFreshCarPlayDescriptorsCanContinue() {
        val controller = controller()
        val refreshed = phone("old", configured = true)
        `when`(usb.deviceList).thenReturn(hashMapOf("old" to refreshed))
        poll(controller)
        shadowOf(Looper.getMainLooper()).idle()
        verify(usb).requestPermission(eq(refreshed), any())
        verify(usb, never()).openDevice(any())
    }

    @Test fun staleDescriptorDoesNotTriggerAnotherTransitionAndWaitTerminates() {
        val controller = controller()
        val stale = phone("old")
        `when`(usb.deviceList).thenReturn(hashMapOf("old" to stale))
        poll(controller, deadline = SystemClock.uptimeMillis() + 1_000)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_000))
        verify(usb, never()).requestPermission(any(UsbDevice::class.java), any())
        verify(usb, never()).openDevice(any())
        assertTrue(statuses.filterIsInstance<CarPlayStatus.Failed>().single().message.contains("未重新识别"))
        attach(controller, phone("late"))
        shadowOf(Looper.getMainLooper()).idle()
        verify(usb, never()).requestPermission(any(UsbDevice::class.java), any())
    }

    @Test fun oldPollCannotChangeAReplacementAttempt() {
        val controller = controller()
        ReflectionHelpers.getField<AtomicInteger>(controller, "availabilityPollGeneration").incrementAndGet()
        poll(controller)
        verifyNoInteractions(usb)
        assertTrue(statuses.isEmpty())
    }

    @Test fun closeCancelsScheduledRediscovery() {
        val controller = controller()
        `when`(usb.deviceList).thenReturn(hashMapOf())
        poll(controller)
        controller.close()
        assertTrue(controller.awaitClosed(2_000))
        clearInvocations(usb)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(16))
        verifyNoInteractions(usb)
        assertTrue(statuses.isEmpty())
    }

    @Test fun attachWhileVendorRequestIsInFlightDoesNotRaceItsConnection() {
        val controller = controller()
        attach(controller, phone("new"))
        shadowOf(Looper.getMainLooper()).idle()
        verifyNoInteractions(usb)
    }

    private fun poll(controller: CarPlayController, deadline: Long = SystemClock.uptimeMillis() + 15_000) {
        controller.javaClass.getDeclaredMethod("pollReenumeratedIphone", String::class.java,
            Int::class.javaPrimitiveType, Long::class.javaPrimitiveType).apply { isAccessible = true }
            .invoke(controller, "old", 0, deadline)
    }

    private fun attach(controller: CarPlayController, device: UsbDevice) {
        controller.javaClass.getDeclaredMethod("onIphoneAttached", UsbDevice::class.java)
            .apply { isAccessible = true }.invoke(controller, device)
    }

    private fun controller(): CarPlayController = CarPlayController(app, CarPlayRuntimeConfig(
        mfiTarget = MfiTarget.LOCAL, transport = CarPlayTransport.WIRED,
        identification = Iap2IdentificationConfig(name = "test", modelIdentifier = "test", manufacturer = "test",
            serialNumber = "test", firmwareVersion = "1", hardwareVersion = "1", carPlayUsbInterfaceNumber = 3),
    ), AirPlayConfig("test", "test", "", "1", AirPlayDisplayConfig(800, 480)),
        AirPlayIdentity(ByteArray(32), ByteArray(32), "test"), PairingStore(),
        object : AirPlaySessionListener {}, object : AirPlayMediaHandler {}, statuses::add).also {
        val field = it.javaClass.getDeclaredField("phase").apply { isAccessible = true }
        field.set(it, field.type.enumConstants.single { phase -> phase.toString() == "REENUMERATION" })
        controllers += it
    }

    private fun phone(name: String, configured: Boolean = false): UsbDevice {
        val device = mock(UsbDevice::class.java)
        `when`(device.deviceName).thenReturn(name)
        `when`(device.vendorId).thenReturn(0x05ac)
        `when`(device.productId).thenReturn(0x12a8)
        if (configured) {
            val mux = mock(UsbInterface::class.java)
            `when`(mux.interfaceClass).thenReturn(0xff)
            `when`(mux.interfaceSubclass).thenReturn(0xfe)
            `when`(mux.interfaceProtocol).thenReturn(2)
            val ncm = mock(UsbInterface::class.java)
            `when`(ncm.interfaceClass).thenReturn(2)
            `when`(ncm.interfaceSubclass).thenReturn(0x0d)
            val config = mock(UsbConfiguration::class.java)
            `when`(config.id).thenReturn(6)
            `when`(config.interfaceCount).thenReturn(2)
            `when`(config.getInterface(0)).thenReturn(mux)
            `when`(config.getInterface(1)).thenReturn(ncm)
            `when`(device.configurationCount).thenReturn(1)
            `when`(device.getConfiguration(0)).thenReturn(config)
        }
        return device
    }
}

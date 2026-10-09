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
        assertTrue(IphoneCarPlayConfiguration.find(phone("old", configured = true)) != null)
        assertFalse(IphoneCarPlayConfiguration.find(phone("old", configured = false)) != null)
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

    @Test fun transitionUsesFreshDeviceAndClosesConnection() {
        val stale = phone("old")
        val fresh = phone("old")
        val connection = mock(UsbDeviceConnection::class.java)
        `when`(usb.deviceList).thenReturn(hashMapOf("old" to fresh))
        `when`(usb.hasPermission(fresh)).thenReturn(true)
        `when`(usb.openDevice(fresh)).thenReturn(connection)
        `when`(connection.controlTransfer(eq(0xc0), eq(0x52), eq(0), eq(4), any(), eq(1), eq(1000))).thenReturn(1)
        var result: IphoneUsbHost.TransitionResult? = null
        IphoneUsbHost(app, usb, IphoneUsbMatcher.appleVendor())
            .requestCarPlayReenumerationAsync(stale, java.util.concurrent.Executor { it.run() }) { result = it }
        assertEquals(IphoneUsbHost.TransitionResult.ReenumerationRequested, result)
        verify(usb, never()).openDevice(stale)
        verify(connection).close()
    }

    @Test fun unavailableTransitionReportsPresenceAndDoesNotSendVendorRequest() {
        val device = phone("old")
        `when`(usb.deviceList).thenReturn(hashMapOf("old" to device))
        `when`(usb.hasPermission(device)).thenReturn(true)
        var result: IphoneUsbHost.TransitionResult? = null
        IphoneUsbHost(app, usb, IphoneUsbMatcher.appleVendor())
            .requestCarPlayReenumerationAsync(device, java.util.concurrent.Executor { it.run() }) { result = it }
        val error = (result as IphoneUsbHost.TransitionResult.Failed).error
        assertTrue(error is IphoneUsbException.DeviceUnavailable)
        assertTrue(error.message!!.contains("present=true permission=true"))
    }

    @Test fun disconnectedTransitionDoesNotOpenStaleDevice() {
        val device = phone("old")
        `when`(usb.deviceList).thenReturn(hashMapOf())
        var result: IphoneUsbHost.TransitionResult? = null
        IphoneUsbHost(app, usb, IphoneUsbMatcher.appleVendor())
            .requestCarPlayReenumerationAsync(device, java.util.concurrent.Executor { it.run() }) { result = it }
        assertTrue((result as IphoneUsbHost.TransitionResult.Failed).error is IphoneUsbException.DeviceUnavailable)
        verify(usb, never()).openDevice(any())
    }

    @Test fun failedOpenRediscoversReplacementAndRequestsFreshPermission() {
        val controller = controller()
        val original = phone("old")
        `when`(usb.deviceList).thenReturn(hashMapOf("old" to original))
        `when`(usb.hasPermission(original)).thenReturn(true)
        begin(controller, original)
        drainWorker(controller)
        val replacement = phone("new")
        `when`(usb.deviceList).thenReturn(hashMapOf("new" to replacement))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        verify(usb).requestPermission(eq(replacement), any())
        assertTrue(statuses.none { it is CarPlayStatus.Failed })
    }

    @Test fun persistentOpenFailureStopsAfterTwoAttempts() {
        val controller = controller()
        val device = phone("old")
        `when`(usb.deviceList).thenReturn(hashMapOf("old" to device))
        `when`(usb.hasPermission(device)).thenReturn(true)
        begin(controller, device)
        drainWorker(controller)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        drainWorker(controller)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20))
        verify(usb, times(2)).openDevice(device)
        assertEquals(1, statuses.filterIsInstance<CarPlayStatus.Failed>().size)
    }

    @Test @Config(sdk = [23])
    fun android51UnpluggedPhoneFailsTheOldPipeInsteadOfWaitingForever() = withAndroid51 {
        val session = openUsbSession()
        try {
            `when`(usb.deviceList).thenReturn(hashMapOf())
            try {
                session.read(100)
                fail("An unplugged USB device must retire its old pipe")
            } catch (_: IphoneUsbException.DeviceUnavailable) { }
        } finally { session.close() }
    }

    @Test @Config(sdk = [23])
    fun android51RepluggedPhoneDoesNotReviveThePreviousDevicePipe() = withAndroid51 {
        val session = openUsbSession()
        try {
            val replacement = phone("new", configured = true)
            `when`(usb.deviceList).thenReturn(hashMapOf("new" to replacement))
            try {
                session.read(100)
                fail("A replacement USB node requires a fresh connection")
            } catch (_: IphoneUsbException.DeviceUnavailable) { }
        } finally { session.close() }
    }

    @Test @Config(sdk = [23])
    fun android51IdleAttachedPhoneCanWaitWithoutReconnecting() = withAndroid51 {
        val session = openUsbSession()
        try { assertNull(session.read(100)) } finally { session.close() }
    }

    private fun withAndroid51(block: () -> Unit) {
        val sdk = android.os.Build.VERSION.SDK_INT
        try {
            ReflectionHelpers.setStaticField(android.os.Build.VERSION::class.java, "SDK_INT", 22)
            block()
        } finally {
            ReflectionHelpers.setStaticField(android.os.Build.VERSION::class.java, "SDK_INT", sdk)
        }
    }

    private fun openUsbSession(): Iap2UsbSession {
        val device = phone("old", configured = true)
        val connection = mock(UsbDeviceConnection::class.java)
        `when`(usb.deviceList).thenReturn(hashMapOf("old" to device))
        `when`(usb.hasPermission(device)).thenReturn(true)
        `when`(usb.openDevice(device)).thenReturn(connection)
        `when`(connection.claimInterface(any(), eq(true))).thenReturn(true)
        `when`(connection.bulkTransfer(any(UsbEndpoint::class.java), any(ByteArray::class.java),
            anyInt(), anyInt(), anyInt())).thenAnswer {
            assertTrue(it.getArgument<Int>(3) <= 16_384)
            assertTrue(it.getArgument<Int>(4) > 0)
            -1 // Android's ambiguous timeout/disconnection result.
        }
        var result: IphoneUsbHost.Iap2SessionResult? = null
        IphoneUsbHost(app, usb, IphoneUsbMatcher.appleVendor()).openIap2UsbSessionAsync(
            device, java.util.concurrent.Executor { it.run() }) { result = it }
        return (result as IphoneUsbHost.Iap2SessionResult.Connected).session
    }

    private fun begin(controller: CarPlayController, device: UsbDevice) {
        controller.javaClass.getDeclaredMethod("beginReenumeration", UsbDevice::class.java)
            .apply { isAccessible = true }.invoke(controller, device)
    }

    private fun drainWorker(controller: CarPlayController) {
        ReflectionHelpers.getField<java.util.concurrent.ExecutorService>(controller, "executor")
            .submit {}.get(5, java.util.concurrent.TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()
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
        field.set(it, field.type.enumConstants!!.single { phase -> phase.toString() == "REENUMERATION" })
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
            val endpoints = listOf(0x04, 0x85).map { address ->
                mock(UsbEndpoint::class.java).also {
                    `when`(it.address).thenReturn(address)
                    `when`(it.direction).thenReturn(address and 0x80)
                    `when`(it.type).thenReturn(UsbConstants.USB_ENDPOINT_XFER_BULK)
                }
            }
            `when`(mux.endpointCount).thenReturn(2)
            endpoints.forEachIndexed { index, endpoint -> `when`(mux.getEndpoint(index)).thenReturn(endpoint) }
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

package com.shilapi.xcertplay

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.os.Build
import java.io.Closeable
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/** API 22 branches in an API 23 sandbox; this is not vehicle/runtime validation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], manifest = Config.NONE)
@Suppress("DEPRECATION")
class ManualHotspotInterfacesLegacyTest {
    private val context = mock(Context::class.java)
    private val connectivity = mock(ConnectivityManager::class.java)
    private val wifi = mock(WifiManager::class.java)
    private val network = mock(Network::class.java)

    init {
        `when`(context.applicationContext).thenReturn(context)
        `when`(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivity)
        `when`(context.getSystemService(Context.WIFI_SERVICE)).thenReturn(wifi)
        `when`(context.getSystemService(ConnectivityManager::class.java)).thenReturn(connectivity)
        `when`(context.getSystemService(WifiManager::class.java)).thenReturn(wifi)
        `when`(connectivity.allNetworks).thenReturn(emptyArray())
    }

    @Test fun legacyNoDefaultRouteRemainsAnObservableSnapshot() = legacy {
        val snapshot = sample()
        assertEquals(true, value(snapshot, "getConsistent"))
        assertNull(value(snapshot, "getDefaultInterface"))
        verify(connectivity, never()).activeNetwork
        verify(context, never()).getSystemService(ConnectivityManager::class.java)
        verify(context, never()).getSystemService(WifiManager::class.java)
    }

    @Test fun legacyDefaultWifiIsIdentifiedAsAnUpstream() = legacy {
        installNetwork(network, ConnectivityManager.TYPE_WIFI, "wlan0")
        `when`(connectivity.allNetworks).thenReturn(arrayOf(network))
        doReturn(info(ConnectivityManager.TYPE_WIFI)).`when`(connectivity).activeNetworkInfo
        val snapshot = sample()
        assertEquals(true, value(snapshot, "getConsistent"))
        assertEquals("wlan0", value(snapshot, "getDefaultInterface"))
        assertEquals(setOf("wlan0"), value(snapshot, "getWifiUpstreams"))
        verify(connectivity, never()).activeNetwork
    }

    @Test fun ambiguousLegacyDefaultTypeCannotPassReadiness() = legacy {
        val second = mock(Network::class.java)
        installNetwork(network, ConnectivityManager.TYPE_WIFI, "wlan0")
        installNetwork(second, ConnectivityManager.TYPE_WIFI, "wlan1")
        `when`(connectivity.allNetworks).thenReturn(arrayOf(network, second))
        doReturn(info(ConnectivityManager.TYPE_WIFI)).`when`(connectivity).activeNetworkInfo
        assertEquals(false, value(sample(), "getConsistent"))
    }

    @Test fun missingLegacyNetworkIdentityCannotPassReadiness() = legacy {
        doReturn(info(ConnectivityManager.TYPE_WIFI)).`when`(connectivity).activeNetworkInfo
        assertEquals(false, value(sample(), "getConsistent"))
    }

    @Test fun missingDefaultInterfaceCannotBeTreatedAsNoDefaultRoute() = legacy {
        `when`(connectivity.allNetworks).thenReturn(arrayOf(network))
        doReturn(info(ConnectivityManager.TYPE_WIFI)).`when`(connectivity).activeNetworkInfo
        doReturn(info(ConnectivityManager.TYPE_WIFI)).`when`(connectivity).getNetworkInfo(network)
        assertEquals(false, value(sample(), "getConsistent"))
    }

    @Test fun legacyRouteChangeDuringSamplingRejectsTheSnapshot() = legacy {
        val mobile = mock(Network::class.java)
        installNetwork(network, ConnectivityManager.TYPE_WIFI, "wlan0")
        installNetwork(mobile, ConnectivityManager.TYPE_MOBILE, "rmnet0")
        `when`(connectivity.allNetworks).thenReturn(arrayOf(network, mobile))
        doReturn(info(ConnectivityManager.TYPE_WIFI), info(ConnectivityManager.TYPE_MOBILE))
            .`when`(connectivity).activeNetworkInfo
        assertEquals(false, value(sample(), "getConsistent"))
    }

    @Test fun modernDefaultRouteUsesThePlatformNetworkIdentity() {
        installNetwork(network, ConnectivityManager.TYPE_WIFI, "wlan0")
        `when`(connectivity.allNetworks).thenReturn(arrayOf(network))
        `when`(connectivity.activeNetwork).thenReturn(network)
        val snapshot = sample()
        assertEquals(true, value(snapshot, "getConsistent"))
        assertEquals("wlan0", value(snapshot, "getDefaultInterface"))
        verify(connectivity, never()).activeNetworkInfo
    }

    private fun installNetwork(network: Network, type: Int, name: String) {
        doReturn(info(type)).`when`(connectivity).getNetworkInfo(network)
        `when`(connectivity.getLinkProperties(network)).thenReturn(LinkProperties().apply { interfaceName = name })
        val caps = mock(NetworkCapabilities::class.java)
        `when`(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)).thenReturn(type == ConnectivityManager.TYPE_WIFI)
        `when`(connectivity.getNetworkCapabilities(network)).thenReturn(caps)
    }

    private fun info(type: Int) = mock(NetworkInfo::class.java).also {
        `when`(it.isConnected).thenReturn(true)
        `when`(it.type).thenReturn(type)
    }

    private fun sample(): Any {
        val type = Class.forName("com.shilapi.xcertplay.network.ManualHotspotInterfaces")
        val log: (String) -> Unit = {}
        val sampler = type.getDeclaredConstructor(Context::class.java, kotlin.jvm.functions.Function1::class.java)
            .newInstance(context, log) as Closeable
        return sampler.use { type.getMethod("sample").invoke(it) }
    }

    private fun value(snapshot: Any, getter: String): Any? = snapshot.javaClass.getMethod(getter).invoke(snapshot)

    private fun legacy(action: () -> Unit) {
        val saved = Build.VERSION.SDK_INT
        try {
            ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 22)
            action()
        } finally {
            ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", saved)
        }
    }
}

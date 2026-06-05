package com.hightemp.proxy_switcher_vpn.vpn.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import io.nekohasekai.libbox.InterfaceUpdateListener
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultNetworkMonitor @Inject constructor(
    @ApplicationContext context: Context
) {
    private val connectivityManager =
        context.getSystemService(ConnectivityManager::class.java)

    @Volatile
    private var listener: InterfaceUpdateListener? = null
    private var callback: ConnectivityManager.NetworkCallback? = null

    @Volatile
    var defaultNetwork: Network? = null
        private set

    @Synchronized
    fun start(listener: InterfaceUpdateListener) {
        this.listener = listener
        if (callback == null) {
            val networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    defaultNetwork = network
                    publishDefaultInterface(network)
                }

                override fun onLinkPropertiesChanged(
                    network: Network,
                    linkProperties: LinkProperties
                ) {
                    defaultNetwork = network
                    publishDefaultInterface(network)
                }

                override fun onLost(network: Network) {
                    if (defaultNetwork == network) {
                        defaultNetwork = connectivityManager.activeNetwork
                        publishDefaultInterface(defaultNetwork)
                    }
                }
            }
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
            callback = networkCallback
        }
        defaultNetwork = connectivityManager.activeNetwork
        publishDefaultInterface(defaultNetwork)
    }

    @Synchronized
    fun close(listener: InterfaceUpdateListener) {
        if (this.listener === listener) {
            this.listener = null
        }
        if (this.listener == null) {
            stop()
        }
    }

    @Synchronized
    fun stop() {
        callback?.let { networkCallback ->
            runCatching {
                connectivityManager.unregisterNetworkCallback(networkCallback)
            }
        }
        callback = null
        listener = null
        defaultNetwork = null
    }

    private fun publishDefaultInterface(network: Network?) {
        val updateListener = listener ?: return
        val linkProperties = network?.let(connectivityManager::getLinkProperties)
        val interfaceName = linkProperties?.interfaceName
        if (interfaceName.isNullOrBlank()) {
            updateListener.updateDefaultInterface("", -1, false, false)
            return
        }

        val interfaceIndex = runCatching {
            NetworkInterface.getByName(interfaceName)?.index ?: -1
        }.getOrDefault(-1)
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        val isExpensive = capabilities
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) != true
        updateListener.updateDefaultInterface(interfaceName, interfaceIndex, isExpensive, false)
    }
}

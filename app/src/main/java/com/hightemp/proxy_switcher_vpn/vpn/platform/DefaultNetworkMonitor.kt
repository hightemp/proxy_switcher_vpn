package com.hightemp.proxy_switcher_vpn.vpn.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
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
                    updateDefaultNetwork(network)
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    if (defaultNetwork == network) {
                        updateDefaultNetwork(network)
                    }
                }

                override fun onLinkPropertiesChanged(
                    network: Network,
                    linkProperties: LinkProperties
                ) {
                    if (defaultNetwork == network) {
                        publishDefaultInterface(network)
                    }
                }

                override fun onLost(network: Network) {
                    if (defaultNetwork == network) {
                        defaultNetwork = currentNonVpnNetwork()
                        publishDefaultInterface(defaultNetwork)
                    }
                }
            }
            registerNonVpnNetworkCallback(networkCallback)
            callback = networkCallback
        }
        defaultNetwork = currentNonVpnNetwork()
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
        if (network != null && !network.isNonVpnInternetNetwork()) {
            updateListener.updateDefaultInterface("", -1, false, false)
            return
        }
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

    private fun updateDefaultNetwork(network: Network) {
        if (!network.isNonVpnInternetNetwork()) {
            if (defaultNetwork == network) {
                defaultNetwork = currentNonVpnNetwork()
                publishDefaultInterface(defaultNetwork)
            }
            return
        }
        defaultNetwork = network
        publishDefaultInterface(network)
    }

    private fun currentNonVpnNetwork(): Network? {
        return connectivityManager.allNetworks.firstOrNull { network ->
            network.isNonVpnInternetNetwork()
        }
    }

    private fun Network.isNonVpnInternetNetwork(): Boolean {
        val capabilities = connectivityManager.getNetworkCapabilities(this) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
            !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    private fun registerNonVpnNetworkCallback(
        networkCallback: ConnectivityManager.NetworkCallback
    ) {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        val handler = Handler(Looper.getMainLooper())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            connectivityManager.registerBestMatchingNetworkCallback(
                request,
                networkCallback,
                handler
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            connectivityManager.requestNetwork(request, networkCallback, handler)
        } else {
            connectivityManager.requestNetwork(request, networkCallback)
        }
    }
}

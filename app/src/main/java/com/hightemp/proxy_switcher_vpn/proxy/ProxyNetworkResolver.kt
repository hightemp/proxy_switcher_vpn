package com.hightemp.proxy_switcher_vpn.proxy

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException
import javax.inject.Inject

data class ProxySocketTarget(
    val socketAddress: InetSocketAddress,
    val serverHost: String = socketAddress.address?.hostAddress ?: socketAddress.hostString,
    private val socketBinder: (Socket) -> Unit = {}
) {
    fun bindSocket(socket: Socket) {
        socketBinder(socket)
    }
}

interface ProxyNetworkResolver {
    fun resolve(
        host: String,
        port: Int,
        preferNonVpnNetwork: Boolean
    ): ProxySocketTarget
}

class AndroidProxyNetworkResolver @Inject constructor(
    @ApplicationContext context: Context
) : ProxyNetworkResolver {
    private val connectivityManager =
        context.getSystemService(ConnectivityManager::class.java)

    override fun resolve(
        host: String,
        port: Int,
        preferNonVpnNetwork: Boolean
    ): ProxySocketTarget {
        val literalAddress = host.toNumericAddressOrNull()
        if (literalAddress != null) {
            return ProxySocketTarget(InetSocketAddress(literalAddress, port))
        }

        if (!preferNonVpnNetwork) {
            val address = InetAddress.getAllByName(host)
                .takeIf { it.isNotEmpty() }
                ?.preferredAddress()
                ?: throw UnknownHostException("Proxy host could not be resolved.")
            return ProxySocketTarget(InetSocketAddress(address, port))
        }

        val network = connectivityManager.allNetworks.firstOrNull { candidate ->
            val capabilities = connectivityManager.getNetworkCapabilities(candidate)
                ?: return@firstOrNull false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        } ?: throw UnknownHostException("No non-VPN network is available for proxy host resolution.")

        val address = network.getAllByName(host)
            .takeIf { it.isNotEmpty() }
            ?.preferredAddress()
            ?: throw UnknownHostException("Proxy host could not be resolved.")
        return ProxySocketTarget(InetSocketAddress(address, port)) { socket ->
            if (!address.isLoopbackAddress && !address.isAnyLocalAddress) {
                network.bindSocket(socket)
            }
        }
    }

    private fun Array<InetAddress>.preferredAddress(): InetAddress {
        return firstOrNull { it is Inet4Address } ?: first()
    }
}

private fun String.toNumericAddressOrNull(): InetAddress? {
    val value = trim()
    val looksIpv4 = IPV4_LITERAL.matches(value)
    val looksIpv6 = ':' in value
    if (!looksIpv4 && !looksIpv6) return null
    return runCatching { InetAddress.getByName(value) }.getOrNull()
}

private val IPV4_LITERAL = Regex("""\d{1,3}(\.\d{1,3}){3}""")

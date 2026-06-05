package com.hightemp.proxy_switcher_vpn.vpn.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Process
import android.system.OsConstants
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import io.nekohasekai.libbox.ConnectionOwner
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.NeighborUpdateListener
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.Notification
import io.nekohasekai.libbox.PlatformUser
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.ShellSession
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.net.InterfaceAddress
import java.net.NetworkInterface as JavaNetworkInterface
import java.security.KeyStore
import java.util.Enumeration
import javax.inject.Inject
import javax.inject.Singleton
import io.nekohasekai.libbox.NetworkInterface as LibboxNetworkInterface

@Singleton
class AndroidLibboxPlatformInterface @Inject constructor(
    @ApplicationContext private val context: Context,
    private val activeVpnServiceBridge: ActiveVpnServiceBridge,
    private val defaultNetworkMonitor: DefaultNetworkMonitor
) : PlatformInterface {
    private val connectivityManager =
        context.getSystemService(ConnectivityManager::class.java)

    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

    override fun autoDetectInterfaceControl(fd: Int) {
        activeVpnServiceBridge.protectSocket(fd)
    }

    override fun openTun(options: TunOptions): Int {
        return activeVpnServiceBridge.openTun(options)
    }

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        defaultNetworkMonitor.start(listener)
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        defaultNetworkMonitor.close(listener)
    }

    override fun getInterfaces(): NetworkInterfaceIterator {
        val javaInterfaces = JavaNetworkInterface.getNetworkInterfaces().toSafeList()
        val interfaces = connectivityManager.allNetworks.mapNotNull { network ->
            val linkProperties = connectivityManager.getLinkProperties(network) ?: return@mapNotNull null
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return@mapNotNull null
            val interfaceName = linkProperties.interfaceName ?: return@mapNotNull null
            val javaInterface = javaInterfaces.firstOrNull { it.name == interfaceName }
                ?: return@mapNotNull null

            LibboxNetworkInterface().apply {
                name = interfaceName
                index = javaInterface.index
                mtu = runCatching { javaInterface.mtu }.getOrDefault(DEFAULT_MTU)
                type = capabilities.toLibboxInterfaceType()
                dnsServer = LibboxStringArray(
                    linkProperties.dnsServers
                        .mapNotNull { it.hostAddress?.withoutIpv6Zone() }
                        .iterator()
                )
                addresses = LibboxStringArray(
                    javaInterface.interfaceAddresses
                        .mapNotNull { it.toPrefixOrNull() }
                        .iterator()
                )
                flags = javaInterface.toLibboxFlags(capabilities)
                metered = !capabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_NOT_METERED
                )
            }
        }
        return LibboxNetworkInterfaceArray(interfaces.iterator())
    }

    override fun useProcFS(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int
    ): ConnectionOwner {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            error("Connection owner lookup requires Android 10 or newer.")
        }

        val uid = connectivityManager.getConnectionOwnerUid(
            ipProtocol,
            InetSocketAddress(sourceAddress, sourcePort),
            InetSocketAddress(destinationAddress, destinationPort)
        )
        if (uid == Process.INVALID_UID) {
            error("Connection owner not found.")
        }
        val packageNames = context.packageManager.getPackagesForUid(uid)
            ?.toList()
            .orEmpty()
        return ConnectionOwner().apply {
            userId = uid
            userName = packageNames.firstOrNull().orEmpty()
            setAndroidPackageNames(LibboxStringArray(packageNames.iterator()))
        }
    }

    override fun includeAllNetworks(): Boolean = false

    override fun underNetworkExtension(): Boolean = false

    override fun clearDNSCache() = Unit

    override fun localDNSTransport(): LocalDNSTransport? = null

    override fun systemCertificates(): StringIterator {
        val certificates = runCatching {
            val keyStore = KeyStore.getInstance("AndroidCAStore")
            keyStore.load(null, null)
            val aliases = keyStore.aliases()
            buildList {
                while (aliases.hasMoreElements()) {
                    val certificate = keyStore.getCertificate(aliases.nextElement())
                    val encoded = Base64.encodeToString(certificate.encoded, Base64.NO_WRAP)
                    add("-----BEGIN CERTIFICATE-----\n$encoded\n-----END CERTIFICATE-----")
                }
            }
        }.getOrElse { emptyList() }
        return LibboxStringArray(certificates.iterator())
    }

    override fun readWIFIState(): WIFIState? = null

    override fun startNeighborMonitor(listener: NeighborUpdateListener?) = Unit

    override fun closeNeighborMonitor(listener: NeighborUpdateListener?) = Unit

    override fun usePlatformShell(): Boolean = false

    override fun checkPlatformShell() {
        error("Platform shell is not supported.")
    }

    override fun openShellSession(
        user: PlatformUser?,
        command: String?,
        environ: StringIterator?,
        term: String?,
        rows: Int,
        cols: Int
    ): ShellSession {
        error("Platform shell is not supported.")
    }

    override fun readSystemSSHHostKey(): String {
        error("System SSH host key is not supported.")
    }

    override fun lookupSFTPServer(): String {
        error("SFTP server lookup is not supported.")
    }

    override fun lookupUser(username: String?): PlatformUser {
        error("Platform user lookup is not supported.")
    }

    override fun tailscaleHostname(): String = "${Build.MANUFACTURER} ${Build.MODEL}"

    override fun registerMyInterface(name: String?) = Unit

    override fun sendNotification(notification: Notification?) = Unit

    private fun NetworkCapabilities.toLibboxInterfaceType(): Int {
        return when {
            hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Libbox.InterfaceTypeWIFI
            hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Libbox.InterfaceTypeCellular
            hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Libbox.InterfaceTypeEthernet
            else -> Libbox.InterfaceTypeOther
        }
    }

    private fun JavaNetworkInterface.toLibboxFlags(
        capabilities: NetworkCapabilities
    ): Int {
        var flags = 0
        if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            flags = flags or OsConstants.IFF_UP or OsConstants.IFF_RUNNING
        }
        if (runCatching { isLoopback }.getOrDefault(false)) {
            flags = flags or OsConstants.IFF_LOOPBACK
        }
        if (runCatching { isPointToPoint }.getOrDefault(false)) {
            flags = flags or OsConstants.IFF_POINTOPOINT
        }
        if (runCatching { supportsMulticast() }.getOrDefault(false)) {
            flags = flags or OsConstants.IFF_MULTICAST
        }
        return flags
    }

    private fun InterfaceAddress.toPrefixOrNull(): String? {
        val hostAddress = address?.hostAddress?.withoutIpv6Zone() ?: return null
        val normalizedAddress = if (address is Inet6Address) {
            Inet6Address.getByAddress(address.address).hostAddress
        } else {
            hostAddress
        }
        return "$normalizedAddress/$networkPrefixLength"
    }

    private fun String.withoutIpv6Zone(): String = substringBefore('%')

    private fun <T> Enumeration<T>?.toSafeList(): List<T> {
        if (this == null) return emptyList()
        return buildList {
            while (hasMoreElements()) {
                add(nextElement())
            }
        }
    }

    private companion object {
        const val DEFAULT_MTU = 1500
    }
}

internal class LibboxStringArray(
    private val iterator: Iterator<String>
) : StringIterator {
    override fun hasNext(): Boolean = iterator.hasNext()

    override fun len(): Int = 0

    override fun next(): String = iterator.next()
}

private class LibboxNetworkInterfaceArray(
    private val iterator: Iterator<LibboxNetworkInterface>
) : NetworkInterfaceIterator {
    override fun hasNext(): Boolean = iterator.hasNext()

    override fun next(): LibboxNetworkInterface = iterator.next()
}

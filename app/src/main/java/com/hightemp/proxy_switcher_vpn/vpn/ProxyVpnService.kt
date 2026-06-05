package com.hightemp.proxy_switcher_vpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.hightemp.proxy_switcher_vpn.MainActivity
import com.hightemp.proxy_switcher_vpn.R
import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import com.hightemp.proxy_switcher_vpn.data.repository.ProxyRepository
import com.hightemp.proxy_switcher_vpn.data.settings.SettingsRepository
import com.hightemp.proxy_switcher_vpn.proxy.ProxyReachabilityTester
import com.hightemp.proxy_switcher_vpn.service.VpnRuntimeController
import com.hightemp.proxy_switcher_vpn.service.VpnRuntimeControllerResult
import com.hightemp.proxy_switcher_vpn.service.VpnRuntimeState
import com.hightemp.proxy_switcher_vpn.service.VpnServiceStatus
import com.hightemp.proxy_switcher_vpn.utils.AppLogger
import com.hightemp.proxy_switcher_vpn.utils.LogType
import com.hightemp.proxy_switcher_vpn.vpn.platform.ActiveVpnService
import com.hightemp.proxy_switcher_vpn.vpn.platform.ActiveVpnServiceBridge
import dagger.hilt.android.AndroidEntryPoint
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.RoutePrefix
import io.nekohasekai.libbox.RoutePrefixIterator
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.TunOptions
import java.net.Socket
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProxyVpnService : VpnService(), ActiveVpnService {
    @Inject lateinit var proxyRepository: ProxyRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var runtimeController: VpnRuntimeController
    @Inject lateinit var activeVpnServiceBridge: ActiveVpnServiceBridge
    @Inject lateinit var proxyReachabilityTester: ProxyReachabilityTester

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var startJob: Job? = null
    private var stopJob: Job? = null
    private var upstreamMonitorJob: Job? = null
    private var tunFileDescriptor: ParcelFileDescriptor? = null

    override fun onCreate() {
        super.onCreate()
        activeVpnServiceBridge.attach(this)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent): IBinder? {
        return super.onBind(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        activeVpnServiceBridge.attach(this)
        when (intent?.action) {
            ACTION_START -> startVpn()
            ACTION_STOP -> stopVpn()
            else -> {
                if (VpnRuntimeState.state.value.isRunning) {
                    startVpn()
                } else {
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopVpn()
        super.onTaskRemoved(rootIntent)
    }

    override fun onRevoke() {
        startJob?.cancel()
        stopJob?.cancel()
        upstreamMonitorJob?.cancel()
        serviceScope.launch {
            runtimeController.stop()
            closeTun()
            activeVpnServiceBridge.detach(this@ProxyVpnService)
            val message = "VPN permission was revoked."
            VpnRuntimeState.markStopped(message)
            sendStatus(message)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        startJob?.cancel()
        stopJob?.cancel()
        upstreamMonitorJob?.cancel()
        closeTun()
        activeVpnServiceBridge.detach(this)
        serviceScope.cancel()
        if (VpnRuntimeState.state.value.isForegroundServiceActive) {
            VpnRuntimeState.markStopped("VPN service destroyed.")
            sendStatus("VPN service destroyed.")
        }
        super.onDestroy()
    }

    override fun protectSocket(fd: Int): Boolean = protect(fd)

    override fun protectSocket(socket: Socket): Boolean = protect(socket)

    @Synchronized
    override fun openTun(options: TunOptions): Int {
        if (prepare(this) != null) {
            error("Android VPN permission is not granted.")
        }

        closeTun()

        val builder = Builder()
            .setSession("Proxy Switcher VPN")
            .setMtu(options.mtu.takeIf { it > 0 } ?: DEFAULT_MTU)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        val ipv4Addresses = options.inet4Address.toRoutePrefixList()
        if (ipv4Addresses.isEmpty()) {
            error("libbox did not provide an IPv4 TUN address.")
        }
        ipv4Addresses.forEach { address ->
            builder.addAddress(address.address(), address.prefix())
        }

        val ignoredIpv6Addresses = options.inet6Address.toRoutePrefixList()
        val ignoredIpv6Routes = options.inet6RouteAddress.toRoutePrefixList() +
            options.inet6RouteRange.toRoutePrefixList()
        if (ignoredIpv6Addresses.isNotEmpty() || ignoredIpv6Routes.isNotEmpty()) {
            AppLogger.warning(
                message = "Ignoring IPv6 TUN options because IPv6 is unsupported for MVP.",
                type = LogType.VPN
            )
        }

        if (options.autoRoute) {
            if (options.dnsMode.value != Libbox.DNSModeDisabled) {
                val dnsServers = options.dnsServerAddress.toStringList()
                    .filterNot { it.contains(":") }
                dnsServers.forEach(builder::addDnsServer)
                if (dnsServers.isEmpty()) {
                    AppLogger.warning(
                        message = "libbox did not provide an IPv4 DNS server for the VPN.",
                        type = LogType.DNS
                    )
                }
            }

            val explicitRoutes = options.inet4RouteAddress.toRoutePrefixList()
            val rangeRoutes = options.inet4RouteRange.toRoutePrefixList()
            val routes = explicitRoutes.ifEmpty { rangeRoutes }
            if (routes.isEmpty()) {
                builder.addRoute("0.0.0.0", 0)
            } else {
                routes.forEach { route ->
                    builder.addRoute(route.address(), route.prefix())
                }
            }
        }

        val descriptor = builder.establish()
            ?: error("Android VPN interface could not be established.")
        tunFileDescriptor = descriptor
        AppLogger.info(
            message = "Android TUN interface established.",
            type = LogType.VPN
        )
        return descriptor.fd
    }

    @Synchronized
    override fun closeTun() {
        tunFileDescriptor?.let { descriptor ->
            runCatching { descriptor.close() }
        }
        tunFileDescriptor = null
    }

    override fun failClosedFromEngine(message: String) {
        startJob?.cancel()
        stopJob?.cancel()
        serviceScope.launch {
            closeTun()
            VpnRuntimeState.markFailedStopped(message)
            sendStatus(message)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startVpn() {
        val snapshot = VpnRuntimeState.state.value
        when (snapshot.status) {
            VpnServiceStatus.STARTING,
            VpnServiceStatus.RUNNING -> {
                sendStatus(snapshot.statusMessage ?: "VPN already active.")
                return
            }
            VpnServiceStatus.STOPPING -> {
                sendStatus(snapshot.statusMessage ?: "VPN stop is in progress.")
                return
            }
            VpnServiceStatus.STOPPED,
            VpnServiceStatus.ERROR -> Unit
        }
        if (startJob?.isActive == true) return

        VpnRuntimeState.markStarting("Starting VPN service.")
        startForeground(
            NOTIFICATION_ID,
            createNotification("VPN service starting.")
        )
        startJob = serviceScope.launch {
            val selectedProxy = loadSelectedProxy()
            if (selectedProxy == null) {
                failClosedAndStop("Select a valid proxy before starting VPN.")
                return@launch
            }

            when (val result = runtimeController.start(selectedProxy)) {
                VpnRuntimeControllerResult.Success -> {
                    val runningMessage = "VPN service running."
                    VpnRuntimeState.markRunning(runningMessage)
                    startUpstreamMonitor(selectedProxy)
                    notify(runningMessage)
                    sendStatus(runningMessage)
                }
                is VpnRuntimeControllerResult.Failure -> {
                    failClosedAndStop(result.message)
                }
            }
        }
    }

    private fun stopVpn() {
        startJob?.cancel()
        upstreamMonitorJob?.cancel()
        val snapshot = VpnRuntimeState.state.value
        when (snapshot.status) {
            VpnServiceStatus.STOPPED,
            VpnServiceStatus.ERROR -> {
                sendStatus(snapshot.statusMessage ?: "VPN service stopped.")
                stopSelf()
                return
            }
            VpnServiceStatus.STOPPING -> {
                sendStatus(snapshot.statusMessage ?: "VPN stop is in progress.")
                return
            }
            VpnServiceStatus.STARTING,
            VpnServiceStatus.RUNNING -> Unit
        }
        if (stopJob?.isActive == true) return

        stopJob = serviceScope.launch {
            VpnRuntimeState.markStopping("Stopping VPN service.")
            runtimeController.stop()
            closeTun()
            val stoppedMessage = "VPN service stopped."
            VpnRuntimeState.markStopped(stoppedMessage)
            sendStatus(stoppedMessage)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun loadSelectedProxy(): ProxyEntity? {
        val selectedProxyId = settingsRepository.settings.first().selectedProxyId ?: return null
        val proxy = proxyRepository.getProxyById(selectedProxyId) ?: return null
        return proxy.takeIf {
            it.isEnabled &&
                it.host.isNotBlank() &&
                it.port in 1..65535
        }
    }

    private fun failClosedAndStop(message: String) {
        failClosedAndStop(message, cancelMonitor = true)
    }

    private fun failClosedAndStop(
        message: String,
        cancelMonitor: Boolean
    ) {
        if (cancelMonitor) {
            upstreamMonitorJob?.cancel()
        }
        closeTun()
        VpnRuntimeState.markFailedStopped(message)
        sendStatus(message)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startUpstreamMonitor(selectedProxy: ProxyEntity) {
        upstreamMonitorJob?.cancel()
        upstreamMonitorJob = serviceScope.launch {
            while (isActive) {
                delay(UPSTREAM_MONITOR_INTERVAL_MILLIS)
                if (!VpnRuntimeState.state.value.isRunning) return@launch

                val probe = runCatching {
                    proxyReachabilityTester.test(
                        proxy = selectedProxy,
                        timeoutMillis = UPSTREAM_MONITOR_TIMEOUT_MILLIS
                    )
                }.getOrNull()

                if (probe?.success == true) continue

                val message = "Selected upstream proxy failed during runtime: ${
                    probe?.message ?: "Proxy test failed."
                }"
                AppLogger.error(
                    message = message,
                    type = LogType.PROXY,
                    sensitiveValues = selectedProxy.sensitiveValues()
                )
                runtimeController.stop()
                failClosedAndStop(message, cancelMonitor = false)
                return@launch
            }
        }
    }

    private fun notify(contentText: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, createNotification(contentText))
    }

    private fun sendStatus(message: String) {
        val snapshot = VpnRuntimeState.state.value
        val intent = Intent(ACTION_STATUS_CHANGED).apply {
            setPackage(packageName)
            putExtra(EXTRA_IS_RUNNING, snapshot.isRunning)
            putExtra(EXTRA_STATUS_MESSAGE, message)
        }
        sendBroadcast(intent)
    }

    private fun ProxyEntity.sensitiveValues(): List<String> {
        return listOfNotNull(username, password)
            .filter { it.isNotBlank() }
    }

    private fun createNotification(contentText: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ProxyVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Proxy Switcher VPN")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(R.drawable.ic_launcher_foreground, "Stop", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "VPN Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(serviceChannel)
        }
    }

    private fun RoutePrefixIterator.toRoutePrefixList(): List<RoutePrefix> {
        return buildList {
            while (hasNext()) {
                add(next())
            }
        }
    }

    private fun StringIterator.toStringList(): List<String> {
        return buildList {
            while (hasNext()) {
                add(next())
            }
        }
    }

    companion object {
        const val ACTION_START = "com.hightemp.proxy_switcher_vpn.action.START_VPN"
        const val ACTION_STOP = "com.hightemp.proxy_switcher_vpn.action.STOP_VPN"
        const val ACTION_STATUS_CHANGED =
            "com.hightemp.proxy_switcher_vpn.action.VPN_STATUS_CHANGED"
        const val EXTRA_IS_RUNNING = "extra_is_running"
        const val EXTRA_STATUS_MESSAGE = "extra_status_message"
        const val CHANNEL_ID = "vpn_foreground_service"
        private const val NOTIFICATION_ID = 1001
        private const val DEFAULT_MTU = 1500
        private const val UPSTREAM_MONITOR_INTERVAL_MILLIS = 5_000L
        private const val UPSTREAM_MONITOR_TIMEOUT_MILLIS = 2_000
    }
}

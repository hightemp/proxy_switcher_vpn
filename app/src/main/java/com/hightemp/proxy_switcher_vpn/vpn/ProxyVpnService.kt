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
import com.hightemp.proxy_switcher_vpn.service.VpnReconnectPolicy
import com.hightemp.proxy_switcher_vpn.service.VpnRuntimeController
import com.hightemp.proxy_switcher_vpn.service.VpnRuntimeControllerResult
import com.hightemp.proxy_switcher_vpn.service.VpnRuntimeState
import com.hightemp.proxy_switcher_vpn.service.VpnServiceStatus
import com.hightemp.proxy_switcher_vpn.utils.AppLogger
import com.hightemp.proxy_switcher_vpn.utils.LogType
import com.hightemp.proxy_switcher_vpn.vpn.platform.ActiveVpnService
import com.hightemp.proxy_switcher_vpn.vpn.platform.ActiveVpnServiceBridge
import com.hightemp.proxy_switcher_vpn.vpn.routing.VpnRouteSelection
import com.hightemp.proxy_switcher_vpn.vpn.routing.isValidForStart
import com.hightemp.proxy_switcher_vpn.vpn.routing.proxyOrNull
import com.hightemp.proxy_switcher_vpn.vpn.routing.sensitiveValues
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
    private var reconnectPolicy = VpnReconnectPolicy()
    private var activeRouteSelection: VpnRouteSelection? = null

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
            ACTION_SWITCH_ROUTE -> switchRoute(intent)
            else -> {
                if (VpnRuntimeState.state.value.isRunning) {
                    startVpn()
                } else {
                    restoreVpnIfEnabled()
                }
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // The VPN keeps running as a foreground service after the task is
        // swiped away from recents.
        super.onTaskRemoved(rootIntent)
    }

    override fun onRevoke() {
        startJob?.cancel()
        stopJob?.cancel()
        upstreamMonitorJob?.cancel()
        activeRouteSelection = null
        serviceScope.launch {
            runtimeController.stop()
            closeTun()
            activeVpnServiceBridge.detach(this@ProxyVpnService)
            val message = "VPN permission was revoked."
            setVpnEnabledFlag(false)
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
        activeRouteSelection = null
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
        upstreamMonitorJob?.cancel()
        val routeSelection = activeRouteSelection
        val snapshot = VpnRuntimeState.state.value
        if (routeSelection == null || !snapshot.isForegroundServiceActive) {
            serviceScope.launch {
                runtimeController.stop()
                failClosedAndStop(message)
            }
            return
        }

        VpnRuntimeState.markStarting("Recovering VPN after engine error.")
        notify("Recovering VPN after engine error.")
        AppLogger.error(
            message = "VPN engine reported runtime failure; reconnect will be attempted: $message",
            type = LogType.VPN,
            sensitiveValues = routeSelection.sensitiveValues()
        )
        startJob = serviceScope.launch {
            val result = startRouteWithRetries(
                routeSelection = routeSelection,
                reason = "engine runtime failure: $message",
                runningMessage = "VPN reconnected to ${routeSelection.displayLabel}.",
                restartMonitorOnSuccess = true
            )
            if (!result.success) {
                val finalMessage = "VPN engine failed and reconnect attempts were exhausted: ${
                    result.lastFailure ?: message
                }"
                runtimeController.stop()
                failClosedAndStop(finalMessage)
            }
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
            setVpnEnabledFlag(true)
            val routeSelection = loadRouteSelection()
            if (!routeSelection.isValidForStart()) {
                failClosedAndStop("Select Direct or a valid enabled proxy before starting VPN.")
                return@launch
            }

            val result = startRouteWithRetries(
                routeSelection = routeSelection!!,
                reason = "start request",
                runningMessage = "VPN service running.",
                restartMonitorOnSuccess = true
            )
            if (!result.success) {
                val message = "VPN could not start after ${
                    reconnectPolicy.attemptBudgetLabel
                } attempts: ${result.lastFailure ?: "Unknown error."}"
                runtimeController.stop()
                failClosedAndStop(message)
            }
        }
    }

    private fun switchRoute(intent: Intent) {
        val snapshot = VpnRuntimeState.state.value
        when (snapshot.status) {
            VpnServiceStatus.RUNNING -> Unit
            VpnServiceStatus.STARTING,
            VpnServiceStatus.STOPPING -> {
                sendStatus(snapshot.statusMessage ?: "VPN transition is in progress.")
                return
            }
            VpnServiceStatus.STOPPED,
            VpnServiceStatus.ERROR -> {
                sendStatus(snapshot.statusMessage ?: "VPN is not running.")
                return
            }
        }
        if (startJob?.isActive == true || stopJob?.isActive == true) return

        upstreamMonitorJob?.cancel()
        VpnRuntimeState.markStarting("Switching VPN route.")
        notify("Switching VPN route.")
        startJob = serviceScope.launch {
            val routeSelection = loadRouteSelection(intent)
            if (!routeSelection.isValidForStart()) {
                failClosedAndStop("Selected VPN route is not available.")
                return@launch
            }

            val result = startRouteWithRetries(
                routeSelection = routeSelection!!,
                reason = "route switch request",
                runningMessage = "VPN route switched to ${routeSelection.displayLabel}.",
                restartMonitorOnSuccess = true
            )
            if (!result.success) {
                val message = "VPN route switch failed after ${
                    reconnectPolicy.attemptBudgetLabel
                } attempts: ${result.lastFailure ?: "Unknown error."}"
                runtimeController.stop()
                failClosedAndStop(message)
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
                serviceScope.launch { setVpnEnabledFlag(false) }
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
            setVpnEnabledFlag(false)
            runtimeController.stop()
            closeTun()
            activeRouteSelection = null
            val stoppedMessage = "VPN service stopped."
            VpnRuntimeState.markStopped(stoppedMessage)
            sendStatus(stoppedMessage)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /**
     * Resumes the VPN after the system restarted this sticky service, for
     * example when the app process was killed while the VPN was active.
     */
    private fun restoreVpnIfEnabled() {
        if (startJob?.isActive == true || stopJob?.isActive == true) return

        startForeground(
            NOTIFICATION_ID,
            createNotification("Restoring VPN service.")
        )
        serviceScope.launch {
            val vpnEnabled = runCatching {
                settingsRepository.settings.first().vpnEnabled
            }.getOrDefault(false)
            if (!vpnEnabled) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }
            AppLogger.info(
                message = "Restoring VPN after service restart.",
                type = LogType.VPN
            )
            startVpn()
        }
    }

    private suspend fun setVpnEnabledFlag(enabled: Boolean) {
        runCatching { settingsRepository.setVpnEnabled(enabled) }
    }

    private suspend fun loadRouteSelection(intent: Intent? = null): VpnRouteSelection? {
        val requestedProxyId = intent
            ?.takeIf { it.hasExtra(EXTRA_PROXY_ID) }
            ?.getLongExtra(EXTRA_PROXY_ID, EXTRA_PROXY_ID_DIRECT)
        val selectedProxyId = requestedProxyId
            ?: settingsRepository.settings.first().selectedProxyId

        if (selectedProxyId == null || selectedProxyId == EXTRA_PROXY_ID_DIRECT) {
            return VpnRouteSelection.Direct
        }

        val proxy = proxyRepository.getProxyById(selectedProxyId) ?: return null
        return proxy.takeIf {
            it.isEnabled &&
                it.host.isNotBlank() &&
                it.port in 1..65535
        }?.let(VpnRouteSelection::Proxy)
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
        activeRouteSelection = null
        closeTun()
        AppLogger.error(
            message = "VPN service stopped fail-closed: $message",
            type = LogType.VPN
        )
        serviceScope.launch { setVpnEnabledFlag(false) }
        VpnRuntimeState.markFailedStopped(message)
        sendStatus(message)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startUpstreamMonitor(routeSelection: VpnRouteSelection) {
        upstreamMonitorJob?.cancel()
        val selectedProxy = routeSelection.proxyOrNull() ?: return
        upstreamMonitorJob = serviceScope.launch {
            AppLogger.info(
                message = "Upstream monitor started for ${routeSelection.displayLabel}; " +
                    "interval=${UPSTREAM_MONITOR_INTERVAL_MILLIS}ms, " +
                    "timeout=${UPSTREAM_MONITOR_TIMEOUT_MILLIS}ms, " +
                    "failureThreshold=${reconnectPolicy.monitorFailureThreshold}, " +
                    "maxReconnectAttempts=${reconnectPolicy.attemptBudgetLabel}.",
                type = LogType.PROXY,
                sensitiveValues = selectedProxy.sensitiveValues()
            )
            var consecutiveFailures = 0
            while (isActive) {
                delay(UPSTREAM_MONITOR_INTERVAL_MILLIS)
                val runtime = VpnRuntimeState.state.value
                if (
                    !runtime.isForegroundServiceActive ||
                    runtime.status == VpnServiceStatus.STOPPING ||
                    runtime.status == VpnServiceStatus.STOPPED ||
                    runtime.status == VpnServiceStatus.ERROR
                ) {
                    AppLogger.info(
                        message = "Upstream monitor stopped because VPN runtime is ${runtime.status}.",
                        type = LogType.PROXY,
                        sensitiveValues = selectedProxy.sensitiveValues()
                    )
                    return@launch
                }
                if (!runtime.isRunning) continue

                val startedAtMillis = System.currentTimeMillis()
                val probeResult = runCatching {
                    proxyReachabilityTester.test(
                        proxy = selectedProxy,
                        timeoutMillis = UPSTREAM_MONITOR_TIMEOUT_MILLIS
                    )
                }
                val elapsedMillis = System.currentTimeMillis() - startedAtMillis
                val probe = probeResult.getOrNull()

                if (probe?.success == true) {
                    if (consecutiveFailures > 0) {
                        AppLogger.info(
                            message = "Upstream proxy recovered after $consecutiveFailures " +
                                "failed monitor probes; probeLatency=${elapsedMillis}ms.",
                            type = LogType.PROXY,
                            sensitiveValues = selectedProxy.sensitiveValues()
                        )
                    } else {
                        AppLogger.debug(
                            message = "Upstream monitor probe succeeded; " +
                                "probeLatency=${elapsedMillis}ms.",
                            type = LogType.PROXY,
                            sensitiveValues = selectedProxy.sensitiveValues()
                        )
                    }
                    consecutiveFailures = 0
                    continue
                }

                consecutiveFailures += 1
                val failureMessage = probeResult.exceptionOrNull()
                    ?.toMonitorMessage()
                    ?: probe?.message
                    ?: "Proxy test failed."
                AppLogger.warning(
                    message = "Upstream monitor probe failed " +
                        "($consecutiveFailures/${reconnectPolicy.monitorFailureThreshold}); " +
                        "probeLatency=${elapsedMillis}ms; reason=$failureMessage. " +
                        "VPN remains active while retrying.",
                    type = LogType.PROXY,
                    sensitiveValues = selectedProxy.sensitiveValues()
                )

                if (consecutiveFailures < reconnectPolicy.monitorFailureThreshold) {
                    continue
                }

                VpnRuntimeState.markStarting("Reconnecting VPN route after upstream failures.")
                notify("Reconnecting VPN route.")
                val result = startRouteWithRetries(
                    routeSelection = routeSelection,
                    reason = "$consecutiveFailures upstream monitor failures",
                    runningMessage = "VPN reconnected to ${routeSelection.displayLabel}.",
                    restartMonitorOnSuccess = false
                )
                if (result.success) {
                    consecutiveFailures = 0
                    continue
                }

                val message = "Selected upstream proxy did not recover after " +
                    "$consecutiveFailures monitor failures and " +
                    "${reconnectPolicy.attemptBudgetLabel} reconnect attempts: " +
                    (result.lastFailure ?: failureMessage)
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

    private suspend fun startRouteWithRetries(
        routeSelection: VpnRouteSelection,
        reason: String,
        runningMessage: String,
        restartMonitorOnSuccess: Boolean
    ): RouteStartAttemptsResult {
        var lastFailure: String? = null
        refreshReconnectPolicy()
        val attemptBudget = reconnectPolicy.attemptBudgetLabel
        AppLogger.info(
            message = "VPN route start/reconnect sequence started for " +
                "${routeSelection.displayLabel}; reason=$reason; " +
                "maxAttempts=$attemptBudget.",
            type = LogType.VPN,
            sensitiveValues = routeSelection.sensitiveValues()
        )

        var attempt = 1
        while (reconnectPolicy.hasAttempt(attempt)) {
            AppLogger.info(
                message = "VPN route attempt $attempt/$attemptBudget " +
                    "for ${routeSelection.displayLabel}; reason=$reason.",
                type = LogType.VPN,
                sensitiveValues = routeSelection.sensitiveValues()
            )
            notify(
                if (attempt == 1) {
                    "Starting VPN route."
                } else {
                    "Retrying VPN route ($attempt/$attemptBudget)."
                }
            )

            when (val result = runtimeController.start(
                routeSelection = routeSelection,
                stopEngineOnFailure = false
            )) {
                VpnRuntimeControllerResult.Success -> {
                    activeRouteSelection = routeSelection
                    VpnRuntimeState.markRunning(runningMessage)
                    if (restartMonitorOnSuccess) {
                        startUpstreamMonitor(routeSelection)
                    }
                    notify(runningMessage)
                    sendStatus(runningMessage)
                    AppLogger.info(
                        message = "VPN route active after attempt " +
                            "$attempt/$attemptBudget: " +
                            routeSelection.displayLabel,
                        type = LogType.VPN,
                        sensitiveValues = routeSelection.sensitiveValues()
                    )
                    return RouteStartAttemptsResult(success = true)
                }
                is VpnRuntimeControllerResult.Failure -> {
                    lastFailure = result.message
                    AppLogger.warning(
                        message = "VPN route attempt " +
                            "$attempt/$attemptBudget failed: " +
                            result.message,
                        type = LogType.VPN,
                        sensitiveValues = routeSelection.sensitiveValues()
                    )
                }
            }

            if (reconnectPolicy.hasAttempt(attempt + 1)) {
                val backoffMillis = reconnectPolicy.backoffForAttempt(attempt)
                AppLogger.info(
                    message = "Waiting ${backoffMillis}ms before next VPN reconnect attempt.",
                    type = LogType.VPN,
                    sensitiveValues = routeSelection.sensitiveValues()
                )
                delay(backoffMillis)
            }
            attempt += 1
        }

        return RouteStartAttemptsResult(success = false, lastFailure = lastFailure)
    }

    private suspend fun refreshReconnectPolicy() {
        val unlimited = runCatching {
            settingsRepository.settings.first().infiniteReconnectEnabled
        }.getOrDefault(true)
        reconnectPolicy = reconnectPolicy.copy(unlimitedReconnectAttempts = unlimited)
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

    private fun Throwable.toMonitorMessage(): String {
        return "${javaClass.simpleName}: ${message ?: "No error message."}"
    }

    private data class RouteStartAttemptsResult(
        val success: Boolean,
        val lastFailure: String? = null
    )

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
        const val ACTION_SWITCH_ROUTE =
            "com.hightemp.proxy_switcher_vpn.action.SWITCH_ROUTE"
        const val ACTION_STATUS_CHANGED =
            "com.hightemp.proxy_switcher_vpn.action.VPN_STATUS_CHANGED"
        const val EXTRA_PROXY_ID = "extra_proxy_id"
        const val EXTRA_PROXY_ID_DIRECT = -1L
        const val EXTRA_IS_RUNNING = "extra_is_running"
        const val EXTRA_STATUS_MESSAGE = "extra_status_message"
        const val CHANNEL_ID = "vpn_foreground_service"
        private const val NOTIFICATION_ID = 1001
        private const val DEFAULT_MTU = 1500
        private const val UPSTREAM_MONITOR_INTERVAL_MILLIS = 5_000L
        private const val UPSTREAM_MONITOR_TIMEOUT_MILLIS = 2_000
    }
}

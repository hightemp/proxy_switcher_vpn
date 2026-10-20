package com.hightemp.proxy_switcher_vpn.vpn.background

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Android power settings that let the system stop the VPN in the background. */
data class BackgroundRestrictionStatus(
    val batteryOptimizationIgnored: Boolean? = null,
    val backgroundRestricted: Boolean? = null
) {
    val needsAttention: Boolean =
        batteryOptimizationIgnored == false || backgroundRestricted == true
}

/** System Always-on VPN state; only readable inside the running service. */
data class AlwaysOnVpnStatus(
    val alwaysOn: Boolean? = null,
    val lockdown: Boolean? = null
)

object AlwaysOnVpnState {
    private val _status = MutableStateFlow(AlwaysOnVpnStatus())
    val status: StateFlow<AlwaysOnVpnStatus> = _status.asStateFlow()

    fun update(status: AlwaysOnVpnStatus) {
        _status.value = status
    }
}

data class BackgroundHealth(
    val restrictions: BackgroundRestrictionStatus = BackgroundRestrictionStatus(),
    val alwaysOn: AlwaysOnVpnStatus = AlwaysOnVpnStatus(),
    val lastStop: VpnStopRecord? = null,
    val lastProcessExit: ProcessExitRecord? = null
)

interface BackgroundRestrictionChecker {
    fun current(): BackgroundRestrictionStatus
}

class AndroidBackgroundRestrictionChecker @Inject constructor(
    @ApplicationContext private val context: Context
) : BackgroundRestrictionChecker {
    override fun current(): BackgroundRestrictionStatus {
        val powerManager = context.getSystemService(PowerManager::class.java)
        val activityManager = context.getSystemService(ActivityManager::class.java)
        return BackgroundRestrictionStatus(
            batteryOptimizationIgnored = runCatching {
                powerManager?.isIgnoringBatteryOptimizations(context.packageName)
            }.getOrNull(),
            backgroundRestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                runCatching { activityManager?.isBackgroundRestricted }.getOrNull()
            } else {
                false
            }
        )
    }
}

internal fun formatDiagnosticTime(timestampMillis: Long, timeZone: TimeZone): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        .apply { this.timeZone = timeZone }
        .format(Date(timestampMillis))
}

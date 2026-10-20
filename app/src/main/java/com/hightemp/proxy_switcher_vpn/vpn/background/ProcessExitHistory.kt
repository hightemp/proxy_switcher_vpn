package com.hightemp.proxy_switcher_vpn.vpn.background

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.hightemp.proxy_switcher_vpn.R
import com.hightemp.proxy_switcher_vpn.i18n.UserText
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.TimeZone
import javax.inject.Inject

/**
 * A previous exit of the app process, from
 * `ActivityManager.getHistoricalProcessExitReasons`. Reason and importance
 * values mirror `ApplicationExitInfo` and `RunningAppProcessInfo` constants so
 * the record stays testable on the JVM.
 */
data class ProcessExitRecord(
    val timestampMillis: Long,
    val reason: Int,
    val status: Int,
    val importance: Int
) {
    val isCrash: Boolean =
        reason == REASON_CRASH || reason == REASON_CRASH_NATIVE || reason == REASON_ANR

    /** The system killed the process while it hosted the VPN foreground service. */
    val killedWhileServiceInForeground: Boolean =
        reason in SYSTEM_KILL_REASONS && importance <= IMPORTANCE_FOREGROUND_SERVICE

    fun summary(timeZone: TimeZone = TimeZone.getDefault()): UserText {
        val cause = when (reason) {
            REASON_SIGNALED -> UserText.of(R.string.exit_signaled, status)
            REASON_LOW_MEMORY -> UserText.of(R.string.exit_low_memory)
            REASON_EXCESSIVE_RESOURCE_USAGE -> UserText.of(R.string.exit_excessive_resources)
            REASON_FREEZER -> UserText.of(R.string.exit_freezer)
            REASON_OTHER -> UserText.of(R.string.exit_other)
            REASON_CRASH -> UserText.of(R.string.exit_crash)
            REASON_CRASH_NATIVE -> UserText.of(R.string.exit_crash_native)
            REASON_ANR -> UserText.of(R.string.exit_anr)
            REASON_EXIT_SELF -> UserText.of(R.string.exit_self)
            REASON_USER_REQUESTED, REASON_USER_STOPPED -> UserText.of(R.string.exit_user)
            REASON_PACKAGE_UPDATED -> UserText.of(R.string.exit_package_updated)
            REASON_PERMISSION_CHANGE -> UserText.of(R.string.exit_permission_change)
            else -> UserText.of(R.string.exit_reason_code, reason)
        }
        val time = formatDiagnosticTime(timestampMillis, timeZone)
        return when {
            reason !in SYSTEM_KILL_REASONS && !isCrash -> UserText.of(R.string.diag_time_and_text, time, cause)
            importance <= IMPORTANCE_FOREGROUND_SERVICE ->
                UserText.of(R.string.exit_summary_foreground, time, cause)
            else -> UserText.of(R.string.exit_summary_background, time, cause)
        }
    }

    companion object {
        const val REASON_EXIT_SELF = 1
        const val REASON_SIGNALED = 2
        const val REASON_LOW_MEMORY = 3
        const val REASON_CRASH = 4
        const val REASON_CRASH_NATIVE = 5
        const val REASON_ANR = 6
        const val REASON_PERMISSION_CHANGE = 8
        const val REASON_EXCESSIVE_RESOURCE_USAGE = 9
        const val REASON_USER_REQUESTED = 10
        const val REASON_USER_STOPPED = 11
        const val REASON_OTHER = 13
        const val REASON_FREEZER = 14
        const val REASON_PACKAGE_UPDATED = 16

        const val IMPORTANCE_FOREGROUND = 100
        const val IMPORTANCE_FOREGROUND_SERVICE = 125
        const val IMPORTANCE_CACHED = 400

        private val SYSTEM_KILL_REASONS = setOf(
            REASON_SIGNALED,
            REASON_LOW_MEMORY,
            REASON_EXCESSIVE_RESOURCE_USAGE,
            REASON_FREEZER,
            REASON_OTHER
        )

        /** The newest record that is newer than [lastSeenMillis], if any. */
        fun unseenLatest(
            records: List<ProcessExitRecord>,
            lastSeenMillis: Long?
        ): ProcessExitRecord? {
            val latest = records.maxByOrNull { it.timestampMillis } ?: return null
            return latest.takeIf { lastSeenMillis == null || it.timestampMillis > lastSeenMillis }
        }
    }
}

interface ProcessExitHistory {
    /** Recent exits of this app's processes, newest first; empty before API 30. */
    fun recentExits(): List<ProcessExitRecord>
}

class AndroidProcessExitHistory @Inject constructor(
    @ApplicationContext private val context: Context
) : ProcessExitHistory {
    override fun recentExits(): List<ProcessExitRecord> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        val activityManager = context.getSystemService(ActivityManager::class.java)
            ?: return emptyList()
        return runCatching {
            activityManager
                .getHistoricalProcessExitReasons(context.packageName, 0, MAX_EXIT_RECORDS)
                .map { info ->
                    ProcessExitRecord(
                        timestampMillis = info.timestamp,
                        reason = info.reason,
                        status = info.status,
                        importance = info.importance
                    )
                }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val MAX_EXIT_RECORDS = 5
    }
}

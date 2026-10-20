package com.hightemp.proxy_switcher_vpn.vpn.background

import android.app.ActivityManager
import android.content.Context
import android.os.Build
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

    fun summary(timeZone: TimeZone = TimeZone.getDefault()): String {
        val cause = when (reason) {
            REASON_SIGNALED -> "killed by system (signal $status)"
            REASON_LOW_MEMORY -> "killed by low memory killer"
            REASON_EXCESSIVE_RESOURCE_USAGE -> "killed for excessive resource use"
            REASON_FREEZER -> "killed by app freezer"
            REASON_OTHER -> "killed by system"
            REASON_CRASH -> "crash"
            REASON_CRASH_NATIVE -> "native crash"
            REASON_ANR -> "not responding (ANR)"
            REASON_EXIT_SELF -> "exited"
            REASON_USER_REQUESTED, REASON_USER_STOPPED -> "stopped by user"
            REASON_PACKAGE_UPDATED -> "app updated"
            REASON_PERMISSION_CHANGE -> "permission changed"
            else -> "exit reason $reason"
        }
        val state = when {
            reason !in SYSTEM_KILL_REASONS && !isCrash -> null
            importance <= IMPORTANCE_FOREGROUND_SERVICE ->
                "while the VPN service was in foreground"
            else -> "in background"
        }
        return listOfNotNull(
            "${formatDiagnosticTime(timestampMillis, timeZone)} · $cause",
            state
        ).joinToString(" ")
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

package com.hightemp.proxy_switcher_vpn.utils

import android.util.Log
import com.hightemp.proxy_switcher_vpn.vpn.engine.VpnEngineLogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object AppLogger {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    fun debug(
        message: String,
        timestampMillis: Long = System.currentTimeMillis(),
        type: LogType = LogType.GENERAL,
        sensitiveValues: Collection<String> = emptyList()
    ) {
        log(VpnEngineLogLevel.DEBUG, message, timestampMillis, type, sensitiveValues)
    }

    fun info(
        message: String,
        timestampMillis: Long = System.currentTimeMillis(),
        type: LogType = LogType.GENERAL,
        sensitiveValues: Collection<String> = emptyList()
    ) {
        log(VpnEngineLogLevel.INFO, message, timestampMillis, type, sensitiveValues)
    }

    fun warning(
        message: String,
        timestampMillis: Long = System.currentTimeMillis(),
        type: LogType = LogType.GENERAL,
        sensitiveValues: Collection<String> = emptyList()
    ) {
        log(VpnEngineLogLevel.WARNING, message, timestampMillis, type, sensitiveValues)
    }

    fun error(
        message: String,
        timestampMillis: Long = System.currentTimeMillis(),
        type: LogType = LogType.GENERAL,
        sensitiveValues: Collection<String> = emptyList()
    ) {
        log(VpnEngineLogLevel.ERROR, message, timestampMillis, type, sensitiveValues)
    }

    fun log(
        level: VpnEngineLogLevel,
        message: String,
        timestampMillis: Long = System.currentTimeMillis(),
        type: LogType = LogType.GENERAL,
        sensitiveValues: Collection<String> = emptyList()
    ) {
        val maskedMessage = maskSensitiveValues(message, sensitiveValues)
        val entry = LogEntry(
            timestampMillis = timestampMillis,
            level = level,
            type = type,
            message = maskedMessage
        )
        writeToAndroidLog(level = level, type = type, message = maskedMessage)
        _logs.update { current ->
            (current + entry).takeLast(MAX_LOG_ENTRIES)
        }
    }

    fun filteredLogs(
        level: VpnEngineLogLevel? = null,
        type: LogType? = null
    ): List<LogEntry> {
        return logs.value.filter { entry ->
            (level == null || entry.level == level) &&
                (type == null || entry.type == type)
        }
    }

    fun clear() {
        _logs.value = emptyList()
    }

    private fun maskSensitiveValues(
        message: String,
        sensitiveValues: Collection<String>
    ): String {
        return sensitiveValues
            .filter { it.isNotBlank() }
            .fold(message) { masked, secret ->
                masked.replace(secret, MASKED_SECRET)
        }
    }

    private fun writeToAndroidLog(
        level: VpnEngineLogLevel,
        type: LogType,
        message: String
    ) {
        val line = "[$type] $message"
        runCatching {
            when (level) {
                VpnEngineLogLevel.DEBUG -> Log.d(ANDROID_LOG_TAG, line)
                VpnEngineLogLevel.INFO -> Log.i(ANDROID_LOG_TAG, line)
                VpnEngineLogLevel.WARNING -> Log.w(ANDROID_LOG_TAG, line)
                VpnEngineLogLevel.ERROR -> Log.e(ANDROID_LOG_TAG, line)
            }
        }
    }

    private const val MAX_LOG_ENTRIES = 1000
    private const val MASKED_SECRET = "***"
    private const val ANDROID_LOG_TAG = "ProxySwitcherVPN"
}

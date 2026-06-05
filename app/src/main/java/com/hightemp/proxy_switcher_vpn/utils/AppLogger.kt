package com.hightemp.proxy_switcher_vpn.utils

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
        val entry = LogEntry(
            timestampMillis = timestampMillis,
            level = level,
            type = type,
            message = maskSensitiveValues(message, sensitiveValues)
        )
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

    private const val MAX_LOG_ENTRIES = 1000
    private const val MASKED_SECRET = "***"
}

package com.hightemp.proxy_switcher_vpn.vpn.tun

enum class TunSessionStatus {
    CONFIGURED,
    ESTABLISHED,
    CLOSED,
    ERROR
}

data class TunSession(
    val config: TunConfig,
    val status: TunSessionStatus = TunSessionStatus.CONFIGURED,
    val fileDescriptor: Int? = null,
    val establishedAtMillis: Long? = null,
    val lastError: String? = null
) {
    fun markEstablished(
        fileDescriptor: Int,
        nowMillis: Long
    ): TunSession {
        return copy(
            status = TunSessionStatus.ESTABLISHED,
            fileDescriptor = fileDescriptor,
            establishedAtMillis = nowMillis,
            lastError = null
        )
    }

    fun markClosed(): TunSession {
        return copy(
            status = TunSessionStatus.CLOSED,
            fileDescriptor = null,
            lastError = null
        )
    }

    fun markError(message: String): TunSession {
        return copy(
            status = TunSessionStatus.ERROR,
            fileDescriptor = null,
            lastError = message
        )
    }
}

package com.hightemp.proxy_switcher_vpn.vpn.udp

enum class Udp443Policy {
    BLOCK
}

enum class NonDnsUdpPolicy {
    BLOCK
}

data class UdpPolicy(
    val udp443Policy: Udp443Policy,
    val nonDnsUdpPolicy: NonDnsUdpPolicy,
    val blockedPorts: Set<Int>
) {
    val blocksUdp443: Boolean =
        udp443Policy == Udp443Policy.BLOCK && UDP_443_PORT in blockedPorts

    val allowsDirectNonDnsUdp: Boolean =
        nonDnsUdpPolicy != NonDnsUdpPolicy.BLOCK

    val diagnosticSummary: String
        get() = when {
            blocksUdp443 && !allowsDirectNonDnsUdp ->
                "UDP/443 blocked; non-DNS UDP blocked."
            blocksUdp443 ->
                "UDP/443 blocked."
            else ->
                "UDP/443 is not blocked."
        }

    companion object {
        const val UDP_443_PORT = 443

        fun mvpDefault(): UdpPolicy {
            return UdpPolicy(
                udp443Policy = Udp443Policy.BLOCK,
                nonDnsUdpPolicy = NonDnsUdpPolicy.BLOCK,
                blockedPorts = setOf(UDP_443_PORT)
            )
        }
    }
}

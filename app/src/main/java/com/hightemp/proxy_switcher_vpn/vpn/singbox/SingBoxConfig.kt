package com.hightemp.proxy_switcher_vpn.vpn.singbox

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

const val DEFAULT_PROXY_OUTBOUND_TAG = "proxy"
const val DEFAULT_DIRECT_OUTBOUND_TAG = "direct"
const val DEFAULT_BLOCK_OUTBOUND_TAG = "block"
const val DEFAULT_DNS_SERVER_TAG = "doh"
const val DEFAULT_PROXY_HOST_BOOTSTRAP_DNS_TAG = "proxy-host-bootstrap"
const val TUN_INBOUND_TAG = "tun-in"
private const val MASKED_SECRET = "***"

data class SingBoxConfig(
    val log: SingBoxLogConfig = SingBoxLogConfig(),
    val dns: SingBoxDnsConfig,
    val inbounds: List<SingBoxInbound>,
    val outbounds: List<SingBoxOutbound>,
    val route: SingBoxRouteConfig,
    val experimental: SingBoxExperimentalConfig = SingBoxExperimentalConfig()
)

data class SingBoxLogConfig(
    val level: String = "debug",
    val timestamp: Boolean = true
)

/**
 * Enables the in-process Clash API traffic manager without exposing any listener.
 * libbox only reports traffic totals when this component exists.
 */
data class SingBoxExperimentalConfig(
    val clashApiEnabled: Boolean = true
) {
    fun toJsonObject(): JsonObject {
        return buildJsonObject {
            if (clashApiEnabled) {
                put("clash_api", buildJsonObject { })
            }
        }
    }
}

data class SingBoxDnsConfig(
    val servers: List<SingBoxDnsServer>,
    val finalTag: String = DEFAULT_DNS_SERVER_TAG,
    val strategy: String = "ipv4_only"
) {
    fun toJsonObject(): JsonObject {
        return buildJsonObject {
            put("servers", JsonArray(servers.map { it.toJsonObject() }))
            put("rules", JsonArray(emptyList()))
            put("final", finalTag)
            put("strategy", strategy)
        }
    }
}

sealed interface SingBoxDnsServer {
    val tag: String

    fun toJsonObject(): JsonObject
}

data class SingBoxHttpsDnsServer(
    override val tag: String = DEFAULT_DNS_SERVER_TAG,
    val server: String,
    val serverPort: Int = 443,
    val path: String = "/dns-query",
    val detour: String?,
    val tls: SingBoxTlsConfig = SingBoxTlsConfig(enabled = true)
) : SingBoxDnsServer {
    override fun toJsonObject(): JsonObject {
        return buildJsonObject {
            put("type", "https")
            put("tag", tag)
            put("server", server)
            put("server_port", serverPort)
            put("path", path)
            put("tls", tls.toJsonObject())
            detour?.let { put("detour", it) }
        }
    }
}

data class SingBoxLocalDnsServer(
    override val tag: String = DEFAULT_PROXY_HOST_BOOTSTRAP_DNS_TAG
) : SingBoxDnsServer {
    override fun toJsonObject(): JsonObject {
        return buildJsonObject {
            put("type", "local")
            put("tag", tag)
        }
    }
}

sealed interface SingBoxInbound {
    val tag: String

    fun toJsonObject(): JsonObject
}

data class SingBoxTunInbound(
    override val tag: String = TUN_INBOUND_TAG,
    val address: List<String>,
    val mtu: Int,
    val dnsMode: String = "hijack",
    val dnsAddress: List<String>,
    val autoRoute: Boolean = true,
    val strictRoute: Boolean = true,
    val routeAddress: List<String>,
    val stack: String = "gvisor"
) : SingBoxInbound {
    override fun toJsonObject(): JsonObject {
        return buildJsonObject {
            put("type", "tun")
            put("tag", tag)
            putStringArray("address", address)
            put("mtu", mtu)
            put("dns_mode", dnsMode)
            putStringArray("dns_address", dnsAddress)
            put("auto_route", autoRoute)
            put("strict_route", strictRoute)
            putStringArray("route_address", routeAddress)
            put("stack", stack)
        }
    }
}

sealed interface SingBoxOutbound {
    val tag: String

    fun toJsonObject(maskSecrets: Boolean): JsonObject
}

data class SingBoxDirectOutbound(
    override val tag: String = DEFAULT_DIRECT_OUTBOUND_TAG
) : SingBoxOutbound {
    override fun toJsonObject(maskSecrets: Boolean): JsonObject {
        return buildJsonObject {
            put("type", "direct")
            put("tag", tag)
        }
    }
}

data class SingBoxSocksOutbound(
    override val tag: String,
    val server: String,
    val serverPort: Int,
    val version: String = "5",
    val network: String = "tcp",
    val domainResolver: String? = null,
    val username: String? = null,
    val password: String? = null
) : SingBoxOutbound {
    override fun toJsonObject(maskSecrets: Boolean): JsonObject {
        return buildJsonObject {
            put("type", "socks")
            put("tag", tag)
            put("server", server)
            put("server_port", serverPort)
            put("version", version)
            put("network", network)
            domainResolver?.let { put("domain_resolver", it) }
            putOptionalSecret("username", username, maskSecrets)
            putOptionalSecret("password", password, maskSecrets)
        }
    }
}

data class SingBoxHttpOutbound(
    override val tag: String,
    val server: String,
    val serverPort: Int,
    val username: String? = null,
    val password: String? = null,
    val domainResolver: String? = null,
    val tls: SingBoxTlsConfig? = null
) : SingBoxOutbound {
    override fun toJsonObject(maskSecrets: Boolean): JsonObject {
        return buildJsonObject {
            put("type", "http")
            put("tag", tag)
            put("server", server)
            put("server_port", serverPort)
            putOptionalSecret("username", username, maskSecrets)
            putOptionalSecret("password", password, maskSecrets)
            domainResolver?.let { put("domain_resolver", it) }
            tls?.let { put("tls", it.toJsonObject()) }
        }
    }
}

data class SingBoxTlsConfig(
    val enabled: Boolean = true,
    val serverName: String? = null,
    val insecure: Boolean? = null
) {
    fun toJsonObject(): JsonObject {
        return buildJsonObject {
            put("enabled", enabled)
            serverName?.let { put("server_name", it) }
            insecure?.let { put("insecure", it) }
        }
    }
}

data class SingBoxRouteConfig(
    val finalTag: String = DEFAULT_PROXY_OUTBOUND_TAG,
    val autoDetectInterface: Boolean = true,
    val rules: List<SingBoxRouteRule> = emptyList()
) {
    fun toJsonObject(): JsonObject {
        return buildJsonObject {
            put("rules", JsonArray(rules.map { it.toJsonObject() }))
            put("final", finalTag)
            put("auto_detect_interface", autoDetectInterface)
        }
    }
}

sealed interface SingBoxRouteRule {
    fun toJsonObject(): JsonObject
}

data class SingBoxSniffRouteRule(
    val timeout: String? = null
) : SingBoxRouteRule {
    override fun toJsonObject(): JsonObject {
        return buildJsonObject {
            put("action", "sniff")
            timeout?.let { put("timeout", it) }
        }
    }
}

data class SingBoxDnsHijackRouteRule(
    val protocol: String = "dns"
) : SingBoxRouteRule {
    override fun toJsonObject(): JsonObject {
        return buildJsonObject {
            put("protocol", protocol)
            put("action", "hijack-dns")
        }
    }
}

data class SingBoxRejectRouteRule(
    val network: String,
    val port: Int,
    val ipCidrs: List<String> = emptyList(),
    val method: String = "drop"
) : SingBoxRouteRule {
    override fun toJsonObject(): JsonObject {
        return buildJsonObject {
            put("network", network)
            if (ipCidrs.isNotEmpty()) {
                putStringArray("ip_cidr", ipCidrs)
            }
            put("port", port)
            put("action", "reject")
            put("method", method)
        }
    }
}

class SingBoxConfigSerializer(
    private val json: Json = Json
) {
    fun serialize(config: SingBoxConfig, maskSecrets: Boolean = false): String {
        return json.encodeToString(JsonObject.serializer(), config.toJsonObject(maskSecrets))
    }

    private fun SingBoxConfig.toJsonObject(maskSecrets: Boolean): JsonObject {
        return buildJsonObject {
            put("log", log.toJsonObject())
            put("dns", dns.toJsonObject())
            put("inbounds", JsonArray(inbounds.map { it.toJsonObject() }))
            put("outbounds", JsonArray(outbounds.map { it.toJsonObject(maskSecrets) }))
            put("route", route.toJsonObject())
            put("experimental", experimental.toJsonObject())
        }
    }

    private fun SingBoxLogConfig.toJsonObject(): JsonObject {
        return buildJsonObject {
            put("level", level)
            put("timestamp", timestamp)
        }
    }
}

private fun JsonObjectBuilder.putStringArray(
    key: String,
    values: List<String>
) {
    put(key, JsonArray(values.map { JsonPrimitive(it) }))
}

private fun JsonObjectBuilder.putOptionalSecret(
    key: String,
    value: String?,
    maskSecrets: Boolean
) {
    if (value.isNullOrBlank()) return
    put(key, if (maskSecrets) MASKED_SECRET else value)
}

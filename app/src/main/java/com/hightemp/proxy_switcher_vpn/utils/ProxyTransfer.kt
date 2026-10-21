package com.hightemp.proxy_switcher_vpn.utils

import com.hightemp.proxy_switcher_vpn.R
import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import com.hightemp.proxy_switcher_vpn.data.local.ProxyType
import com.hightemp.proxy_switcher_vpn.i18n.UserText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Compatible proxy list transfer format shared with the reference proxy_switcher app.
 *
 * The format is a JSON array of proxy objects containing host, port, type,
 * optional username/password/label, and isEnabled. The optional `group` field
 * is specific to this app; the reference app ignores it.
 */
object ProxyTransfer {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun export(proxies: List<ProxyEntity>): String {
        val array = buildJsonArray {
            proxies.forEach { proxy ->
                add(
                    buildJsonObject {
                        put("host", proxy.host)
                        put("port", proxy.port)
                        put("type", proxy.type.name)
                        proxy.username?.let { put("username", it) }
                        proxy.password?.let { put("password", it) }
                        proxy.label?.let { put("label", it) }
                        put("isEnabled", proxy.isEnabled)
                        proxy.groupName?.let { put("group", it) }
                    }
                )
            }
        }
        return json.encodeToString(JsonElement.serializer(), array)
    }

    data class ImportResult(
        val proxies: List<ProxyEntity>,
        val errorMessage: UserText? = null
    ) {
        val isSuccess: Boolean get() = errorMessage == null
    }

    fun import(text: String): ImportResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return ImportResult(emptyList(), UserText.of(R.string.import_error_empty))
        }

        return runCatching {
            val element = json.parseToJsonElement(trimmed)
            val array = element as? JsonArray
                ?: return ImportResult(emptyList(), UserText.of(R.string.import_error_not_array))
            val proxies = array.mapNotNull { proxyElement ->
                (proxyElement as? JsonObject)?.toProxyEntity()
            }
            if (proxies.isEmpty()) {
                ImportResult(emptyList(), UserText.of(R.string.import_error_no_proxies))
            } else {
                ImportResult(proxies)
            }
        }.getOrElse { error ->
            ImportResult(
                emptyList(),
                UserText.of(R.string.import_error_invalid, error.message.orEmpty())
            )
        }
    }

    private fun JsonObject.toProxyEntity(): ProxyEntity? {
        val host = optionalString("host")?.trim().orEmpty()
        val port = intValue("port") ?: -1
        if (host.isBlank() || port !in 1..65535) {
            return null
        }
        return ProxyEntity(
            id = 0L,
            host = host,
            port = port,
            type = proxyTypeValue(optionalString("type")),
            username = optionalString("username"),
            password = optionalString("password"),
            label = optionalString("label"),
            isEnabled = booleanValue("isEnabled") ?: true,
            groupName = optionalString("group")?.trim()
        )
    }

    private fun JsonObject.optionalString(key: String): String? {
        return primitive(key)?.content?.takeIf { it.isNotBlank() }
    }

    private fun JsonObject.intValue(key: String): Int? {
        val primitive = primitive(key) ?: return null
        return primitive.intOrNull ?: primitive.content.toIntOrNull()
    }

    private fun JsonObject.booleanValue(key: String): Boolean? {
        val primitive = primitive(key) ?: return null
        return primitive.booleanOrNull ?: primitive.content.toBooleanStrictOrNull()
    }

    private fun JsonObject.primitive(key: String) = this[key]?.jsonPrimitive

    private fun proxyTypeValue(value: String?): ProxyType {
        return runCatching {
            ProxyType.valueOf(value.orEmpty().trim().uppercase())
        }.getOrDefault(ProxyType.HTTP)
    }
}

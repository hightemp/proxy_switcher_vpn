package com.hightemp.proxy_switcher_vpn.vpn.singbox

import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import com.hightemp.proxy_switcher_vpn.data.local.ProxyType
import com.hightemp.proxy_switcher_vpn.vpn.udp.UdpPolicy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SingBoxConfigGeneratorTest {
    private val generator = SingBoxConfigGenerator()

    @Test
    fun generatesSocks5OutboundForSelectedProxy() {
        val generated = generator.generate(
            proxy(type = ProxyType.SOCKS5, port = 1080)
        )

        val outbound = firstOutbound(generated.json)

        assertEquals("socks", outbound["type"]?.jsonPrimitive?.content)
        assertEquals(DEFAULT_PROXY_OUTBOUND_TAG, outbound["tag"]?.jsonPrimitive?.content)
        assertEquals("proxy.example", outbound["server"]?.jsonPrimitive?.content)
        assertEquals(1080, outbound["server_port"]?.jsonPrimitive?.int)
        assertEquals("5", outbound["version"]?.jsonPrimitive?.content)
        assertEquals("tcp", outbound["network"]?.jsonPrimitive?.content)
        assertEquals(
            DEFAULT_PROXY_HOST_BOOTSTRAP_DNS_TAG,
            outbound["domain_resolver"]?.jsonPrimitive?.content
        )
        assertNull(outbound["username"])
        assertNull(outbound["password"])
    }

    @Test
    fun generatesSocks5OutboundWithUsernamePasswordAuth() {
        val generated = generator.generate(
            proxy(
                type = ProxyType.SOCKS5,
                username = "user-1",
                password = "secret-password"
            )
        )

        val outbound = firstOutbound(generated.json)
        val maskedOutbound = firstOutbound(generated.maskedPreview)

        assertEquals("socks", outbound["type"]?.jsonPrimitive?.content)
        assertEquals("user-1", outbound["username"]?.jsonPrimitive?.content)
        assertEquals("secret-password", outbound["password"]?.jsonPrimitive?.content)
        assertEquals("***", maskedOutbound["username"]?.jsonPrimitive?.content)
        assertEquals("***", maskedOutbound["password"]?.jsonPrimitive?.content)
        assertFalse(generated.maskedPreview.contains("user-1"))
        assertFalse(generated.maskedPreview.contains("secret-password"))
    }

    @Test
    fun generatesHttpOutboundForHttpProxyWithoutTls() {
        val generated = generator.generate(
            proxy(type = ProxyType.HTTP, port = 8080)
        )

        val outbound = firstOutbound(generated.json)

        assertEquals("http", outbound["type"]?.jsonPrimitive?.content)
        assertEquals(8080, outbound["server_port"]?.jsonPrimitive?.int)
        assertEquals(
            DEFAULT_PROXY_HOST_BOOTSTRAP_DNS_TAG,
            outbound["domain_resolver"]?.jsonPrimitive?.content
        )
        assertNull(outbound["tls"])
        assertNull(outbound["username"])
        assertNull(outbound["password"])
    }

    @Test
    fun generatesHttpOutboundWithUsernamePasswordAuth() {
        val generated = generator.generate(
            proxy(
                type = ProxyType.HTTP,
                port = 8080,
                username = "http-user",
                password = "http-password"
            )
        )

        val outbound = firstOutbound(generated.json)
        val maskedOutbound = firstOutbound(generated.maskedPreview)

        assertEquals("http", outbound["type"]?.jsonPrimitive?.content)
        assertEquals(8080, outbound["server_port"]?.jsonPrimitive?.int)
        assertEquals("http-user", outbound["username"]?.jsonPrimitive?.content)
        assertEquals("http-password", outbound["password"]?.jsonPrimitive?.content)
        assertEquals("***", maskedOutbound["username"]?.jsonPrimitive?.content)
        assertEquals("***", maskedOutbound["password"]?.jsonPrimitive?.content)
        assertFalse(generated.maskedPreview.contains("http-user"))
        assertFalse(generated.maskedPreview.contains("http-password"))
    }

    @Test
    fun generatesHttpOutboundWithTlsForHttpsProxy() {
        val generated = generator.generate(
            proxy(type = ProxyType.HTTPS, port = 8443)
        )

        val outbound = firstOutbound(generated.json)
        val tls = outbound["tls"]?.jsonObject

        assertEquals("http", outbound["type"]?.jsonPrimitive?.content)
        assertEquals(8443, outbound["server_port"]?.jsonPrimitive?.int)
        assertEquals(
            DEFAULT_PROXY_HOST_BOOTSTRAP_DNS_TAG,
            outbound["domain_resolver"]?.jsonPrimitive?.content
        )
        assertEquals(true, tls?.get("enabled")?.jsonPrimitive?.boolean)
        assertNull(tls?.get("server_name"))
        assertNull(tls?.get("insecure"))
        assertNull(outbound["username"])
        assertNull(outbound["password"])
    }

    @Test
    fun generatedHttpsProxyUsesResolvedServerWithOriginalTlsServerName() {
        val generated = generator.generate(
            selectedProxy = proxy(type = ProxyType.HTTPS, port = 8443),
            proxyEndpoint = SingBoxProxyEndpoint.resolved(
                selectedProxy = proxy(type = ProxyType.HTTPS, port = 8443),
                resolvedServer = "203.0.113.10"
            )
        )

        val outbound = firstOutbound(generated.json)
        val tls = outbound["tls"]?.jsonObject
        val servers = dns(generated.json)
            .getValue("servers")
            .jsonArray
            .map { it.jsonObject }

        assertEquals("203.0.113.10", outbound["server"]?.jsonPrimitive?.content)
        assertEquals("proxy.example", tls?.get("server_name")?.jsonPrimitive?.content)
        assertNull(outbound["domain_resolver"])
        assertFalse(servers.any { it["type"]?.jsonPrimitive?.content == "local" })
    }

    @Test
    fun generatesHttpsProxyOutboundWithUsernamePasswordAuth() {
        val generated = generator.generate(
            proxy(
                type = ProxyType.HTTPS,
                port = 8443,
                username = "https-user",
                password = "https-password"
            )
        )

        val outbound = firstOutbound(generated.json)
        val tls = outbound["tls"]?.jsonObject
        val maskedOutbound = firstOutbound(generated.maskedPreview)

        assertEquals("http", outbound["type"]?.jsonPrimitive?.content)
        assertEquals(true, tls?.get("enabled")?.jsonPrimitive?.boolean)
        assertNull(tls?.get("insecure"))
        assertEquals("https-user", outbound["username"]?.jsonPrimitive?.content)
        assertEquals("https-password", outbound["password"]?.jsonPrimitive?.content)
        assertEquals("***", maskedOutbound["username"]?.jsonPrimitive?.content)
        assertEquals("***", maskedOutbound["password"]?.jsonPrimitive?.content)
        assertFalse(generated.maskedPreview.contains("https-user"))
        assertFalse(generated.maskedPreview.contains("https-password"))
    }

    @Test
    fun maskedPreviewHidesProxyCredentials() {
        val generated = generator.generate(
            proxy(
                type = ProxyType.SOCKS5,
                username = "user-1",
                password = "secret-password"
            )
        )

        val realOutbound = firstOutbound(generated.json)
        val maskedOutbound = firstOutbound(generated.maskedPreview)

        assertEquals("user-1", realOutbound["username"]?.jsonPrimitive?.content)
        assertEquals("secret-password", realOutbound["password"]?.jsonPrimitive?.content)
        assertEquals("***", maskedOutbound["username"]?.jsonPrimitive?.content)
        assertEquals("***", maskedOutbound["password"]?.jsonPrimitive?.content)
        assertFalse(generated.maskedPreview.contains("user-1"))
        assertFalse(generated.maskedPreview.contains("secret-password"))
    }

    @Test
    fun maskedPreviewHidesCredentialsForEveryProxyType() {
        ProxyType.entries.forEach { proxyType ->
            val generated = generator.generate(
                proxy(
                    type = proxyType,
                    username = "suite-user",
                    password = "suite-password"
                )
            )

            assertTrue(generated.json.contains("suite-user"))
            assertTrue(generated.json.contains("suite-password"))
            assertFalse(generated.maskedPreview.contains("suite-user"))
            assertFalse(generated.maskedPreview.contains("suite-password"))
            assertTrue(generated.maskedPreview.contains("***"))
        }
    }

    @Test
    fun generatedConfigContainsLogSectionAndParseableJson() {
        val generated = generator.generate(proxy(type = ProxyType.HTTP))

        val root = root(generated.json)
        val log = root["log"]?.jsonObject

        assertEquals("debug", log?.get("level")?.jsonPrimitive?.content)
        assertEquals(true, log?.get("timestamp")?.jsonPrimitive?.boolean)
        assertTrue(root["dns"]?.jsonObject?.isNotEmpty() == true)
        assertTrue(root["inbounds"]?.jsonArray?.isNotEmpty() == true)
        assertTrue(root["outbounds"]?.jsonArray?.isNotEmpty() == true)
        assertTrue(root["route"]?.jsonObject?.isNotEmpty() == true)
    }

    @Test
    fun generatedConfigContainsSpikeValidatedTunInbound() {
        val generated = generator.generate(proxy(type = ProxyType.SOCKS5))

        val inbound = firstInbound(generated.json)

        assertEquals("tun", inbound["type"]?.jsonPrimitive?.content)
        assertEquals(TUN_INBOUND_TAG, inbound["tag"]?.jsonPrimitive?.content)
        assertEquals(listOf("172.19.0.1/30"), inbound.stringArray("address"))
        assertEquals(9000, inbound["mtu"]?.jsonPrimitive?.int)
        assertEquals("hijack", inbound["dns_mode"]?.jsonPrimitive?.content)
        assertEquals(listOf("172.19.0.2"), inbound.stringArray("dns_address"))
        assertEquals(true, inbound["auto_route"]?.jsonPrimitive?.boolean)
        assertEquals(true, inbound["strict_route"]?.jsonPrimitive?.boolean)
        assertEquals(listOf("0.0.0.0/1", "128.0.0.0/1"), inbound.stringArray("route_address"))
        assertEquals("gvisor", inbound["stack"]?.jsonPrimitive?.content)
    }

    @Test
    fun generatedConfigRoutesTrafficToSelectedProxyByDefault() {
        val generated = generator.generate(proxy(type = ProxyType.HTTP))

        val route = route(generated.json)

        assertEquals(DEFAULT_PROXY_OUTBOUND_TAG, route["final"]?.jsonPrimitive?.content)
        assertEquals(true, route["auto_detect_interface"]?.jsonPrimitive?.boolean)
        assertTrue(route["rules"]?.jsonArray?.isNotEmpty() == true)
    }

    @Test
    fun generatedConfigDisablesAutoDetectInterfaceForResolvedLoopbackProxy() {
        val selectedProxy = proxy(type = ProxyType.HTTPS, port = 8443)
        val generated = generator.generate(
            selectedProxy = selectedProxy,
            proxyEndpoint = SingBoxProxyEndpoint.resolved(
                selectedProxy = selectedProxy,
                resolvedServer = "127.0.0.1"
            )
        )

        val route = route(generated.json)

        assertEquals(false, route["auto_detect_interface"]?.jsonPrimitive?.boolean)
    }

    @Test
    fun generatedConfigDoesNotDefineDirectOutboundFallback() {
        val generated = generator.generate(proxy(type = ProxyType.HTTP))

        val outbounds = root(generated.json)
            .getValue("outbounds")
            .jsonArray
            .map { it.jsonObject }

        assertEquals(
            listOf(DEFAULT_PROXY_OUTBOUND_TAG),
            outbounds.map { outbound -> outbound["tag"]?.jsonPrimitive?.content }
        )
        assertFalse(generated.json.contains("\"tag\":\"$DEFAULT_DIRECT_OUTBOUND_TAG\""))
        assertFalse(generated.json.contains("\"type\":\"direct\""))
    }

    @Test
    fun generatedDnsConfigUsesDohThroughSelectedProxy() {
        val generated = generator.generate(proxy(type = ProxyType.HTTP))

        val dns = dns(generated.json)
        val server = dns.getValue("servers").jsonArray.first().jsonObject
        val tls = server["tls"]?.jsonObject

        assertEquals("https", server["type"]?.jsonPrimitive?.content)
        assertEquals(DEFAULT_DNS_SERVER_TAG, server["tag"]?.jsonPrimitive?.content)
        assertEquals("1.1.1.1", server["server"]?.jsonPrimitive?.content)
        assertEquals(443, server["server_port"]?.jsonPrimitive?.int)
        assertEquals("/dns-query", server["path"]?.jsonPrimitive?.content)
        assertEquals(DEFAULT_PROXY_OUTBOUND_TAG, server["detour"]?.jsonPrimitive?.content)
        assertEquals(true, tls?.get("enabled")?.jsonPrimitive?.boolean)
        assertNull(tls?.get("insecure"))
        assertEquals(DEFAULT_DNS_SERVER_TAG, dns["final"]?.jsonPrimitive?.content)
        assertEquals("ipv4_only", dns["strategy"]?.jsonPrimitive?.content)
    }

    @Test
    fun generatedDnsConfigUsesLocalResolverOnlyForProxyHostBootstrap() {
        val generated = generator.generate(proxy(type = ProxyType.HTTPS))

        val servers = dns(generated.json)
            .getValue("servers")
            .jsonArray
            .map { it.jsonObject }
        val bootstrap = servers.first {
            it["tag"]?.jsonPrimitive?.content == DEFAULT_PROXY_HOST_BOOTSTRAP_DNS_TAG
        }
        val outbound = firstOutbound(generated.json)

        assertEquals("local", bootstrap["type"]?.jsonPrimitive?.content)
        assertEquals(
            DEFAULT_PROXY_HOST_BOOTSTRAP_DNS_TAG,
            outbound["domain_resolver"]?.jsonPrimitive?.content
        )
        assertEquals(DEFAULT_DNS_SERVER_TAG, dns(generated.json)["final"]?.jsonPrimitive?.content)
    }

    @Test
    fun generatedDnsConfigDoesNotUseDirectDnsByDefault() {
        val generated = generator.generate(proxy(type = ProxyType.SOCKS5))

        val server = dns(generated.json)
            .getValue("servers")
            .jsonArray
            .first()
            .jsonObject

        assertEquals(DEFAULT_PROXY_OUTBOUND_TAG, server["detour"]?.jsonPrimitive?.content)
        assertFalse(generated.json.contains("\"detour\":\"$DEFAULT_DIRECT_OUTBOUND_TAG\""))
    }

    @Test
    fun generatedRouteHijacksTunDnsToDnsModule() {
        val generated = generator.generate(proxy(type = ProxyType.HTTP))

        val rules = route(generated.json)
            .getValue("rules")
            .jsonArray
            .map { it.jsonObject }
        val sniffRule = rules[0]
        val dnsHijackRule = rules[1]

        assertEquals("sniff", sniffRule["action"]?.jsonPrimitive?.content)
        assertEquals("dns", dnsHijackRule["protocol"]?.jsonPrimitive?.content)
        assertEquals("hijack-dns", dnsHijackRule["action"]?.jsonPrimitive?.content)
        assertNull(dnsHijackRule["outbound"])
    }

    @Test
    fun generatedRouteRejectsAndroidPrivateDnsToTunDnsAddress() {
        val generated = generator.generate(proxy(type = ProxyType.HTTP))

        val rule = route(generated.json)
            .getValue("rules")
            .jsonArray[2]
            .jsonObject

        assertEquals("tcp", rule["network"]?.jsonPrimitive?.content)
        assertEquals(listOf("172.19.0.2/32"), rule.stringArray("ip_cidr"))
        assertEquals(853, rule["port"]?.jsonPrimitive?.int)
        assertEquals("reject", rule["action"]?.jsonPrimitive?.content)
        assertEquals("default", rule["method"]?.jsonPrimitive?.content)
    }

    @Test
    fun generatedRouteBlocksUdp443WithRejectDropRule() {
        val generated = generator.generate(proxy(type = ProxyType.HTTP))

        val rule = route(generated.json)
            .getValue("rules")
            .jsonArray
            .map { it.jsonObject }
            .first {
                it["network"]?.jsonPrimitive?.content == "udp" &&
                    it["port"]?.jsonPrimitive?.int == 443
            }

        assertEquals("udp", rule["network"]?.jsonPrimitive?.content)
        assertEquals(443, rule["port"]?.jsonPrimitive?.int)
        assertEquals("reject", rule["action"]?.jsonPrimitive?.content)
        assertEquals("drop", rule["method"]?.jsonPrimitive?.content)
        assertTrue(generated.udpPolicy.blocksUdp443)
    }

    @Test
    fun generatedConfigExposesMvpUdpPolicy() {
        val generated = generator.generate(proxy(type = ProxyType.HTTP))

        assertEquals(UdpPolicy.mvpDefault(), generated.udpPolicy)
        assertTrue(generated.udpPolicy.blocksUdp443)
        assertFalse(generated.udpPolicy.allowsDirectNonDnsUdp)
    }

    @Test
    fun generatedTunDefaultsAreIpv4Only() {
        val generated = generator.generate(proxy(type = ProxyType.HTTP))
        val inbound = firstInbound(generated.json)

        assertEquals(listOf("172.19.0.1/30"), inbound.stringArray("address"))
        assertEquals(listOf("0.0.0.0/1", "128.0.0.0/1"), inbound.stringArray("route_address"))
        assertFalse(generated.json.contains("::"))
        assertFalse(generated.json.contains("inet6"))
        assertFalse(generated.json.contains("fdfe"))
    }

    private fun root(json: String) =
        Json.parseToJsonElement(json).jsonObject

    private fun firstInbound(json: String) =
        root(json)
            .getValue("inbounds")
            .jsonArray
            .first()
            .jsonObject

    private fun firstOutbound(json: String) =
        root(json)
            .getValue("outbounds")
            .jsonArray
            .first()
            .jsonObject

    private fun route(json: String) =
        root(json)
            .getValue("route")
            .jsonObject

    private fun dns(json: String) =
        root(json)
            .getValue("dns")
            .jsonObject

    private fun JsonObject.stringArray(key: String): List<String> {
        return getValue(key).jsonArray.map { it.jsonPrimitive.content }
    }

    private fun proxy(
        type: ProxyType,
        port: Int = 1080,
        username: String? = null,
        password: String? = null
    ): ProxyEntity {
        return ProxyEntity(
            id = 1L,
            host = "proxy.example",
            port = port,
            type = type,
            username = username,
            password = password,
            label = "Test proxy"
        )
    }
}

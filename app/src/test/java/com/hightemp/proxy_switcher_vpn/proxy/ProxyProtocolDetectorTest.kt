package com.hightemp.proxy_switcher_vpn.proxy

import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import com.hightemp.proxy_switcher_vpn.data.local.ProxyType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProxyProtocolDetectorTest {
    private val parsed = ParsedProxyString(
        host = "proxy.example",
        port = 1080,
        type = null,
        username = "demo-user",
        password = "demo-pass"
    )

    @Test
    fun probesHttpsThenSocks5ThenHttp() = runTest {
        val tester = RecordingTester(workingType = null)

        ProxyProtocolDetector(tester).detect(parsed)

        assertEquals(
            listOf(ProxyType.HTTPS, ProxyType.SOCKS5, ProxyType.HTTP),
            tester.probes.map { it.type }
        )
    }

    @Test
    fun returnsFirstWorkingProtocolAndStopsProbing() = runTest {
        val tester = RecordingTester(workingType = ProxyType.SOCKS5)

        val detected = ProxyProtocolDetector(tester).detect(parsed)

        assertEquals(ProxyType.SOCKS5, detected)
        assertEquals(listOf(ProxyType.HTTPS, ProxyType.SOCKS5), tester.probes.map { it.type })
    }

    @Test
    fun probesUseParsedEndpointAndCredentials() = runTest {
        val tester = RecordingTester(workingType = ProxyType.HTTPS)

        ProxyProtocolDetector(tester).detect(parsed)

        val probe = tester.probes.single()
        assertEquals("proxy.example", probe.host)
        assertEquals(1080, probe.port)
        assertEquals("demo-user", probe.username)
        assertEquals("demo-pass", probe.password)
    }

    @Test
    fun returnsNullWhenNoProtocolWorks() = runTest {
        val detected = ProxyProtocolDetector(RecordingTester(workingType = null)).detect(parsed)

        assertNull(detected)
    }

    private class RecordingTester(
        private val workingType: ProxyType?
    ) : ProxyReachabilityTester {
        val probes = mutableListOf<ProxyEntity>()

        override suspend fun test(
            proxy: ProxyEntity,
            targetHost: String,
            targetPort: Int,
            timeoutMillis: Int
        ): ProxyTestResult {
            probes += proxy
            return ProxyTestResult(
                success = proxy.type == workingType,
                message = "probe"
            )
        }
    }
}

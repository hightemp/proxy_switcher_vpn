package com.hightemp.proxy_switcher_vpn.proxy

import com.hightemp.proxy_switcher_vpn.data.local.ProxyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyStringParserTest {
    @Test
    fun hostPortWithoutProtocolNeedsDetection() {
        assertParsed(
            input = "192.0.2.10:8080",
            expected = ParsedProxyString("192.0.2.10", 8080, type = null)
        )
    }

    @Test
    fun hostPortCredentials() {
        assertParsed(
            input = "192.0.2.10:64239:demo-user:demo-pass",
            expected = ParsedProxyString("192.0.2.10", 64239, null, "demo-user", "demo-pass")
        )
    }

    @Test
    fun credentialsBeforeHostWithAt() {
        assertParsed(
            input = "demo-user:demo-pass@proxy.example:1080",
            expected = ParsedProxyString("proxy.example", 1080, null, "demo-user", "demo-pass")
        )
    }

    @Test
    fun usernameOnlyBeforeHostWithAt() {
        assertParsed(
            input = "demo-user@proxy.example:1080",
            expected = ParsedProxyString("proxy.example", 1080, null, "demo-user", null)
        )
    }

    @Test
    fun uriSchemesMapToProxyTypes() {
        assertParsed(
            input = "http://proxy.example:8080",
            expected = ParsedProxyString("proxy.example", 8080, ProxyType.HTTP)
        )
        assertParsed(
            input = "https://demo-user:demo-pass@proxy.example:8443",
            expected = ParsedProxyString(
                "proxy.example", 8443, ProxyType.HTTPS, "demo-user", "demo-pass"
            )
        )
        assertParsed(
            input = "socks5://demo-user:demo-pass@proxy.example:1080",
            expected = ParsedProxyString(
                "proxy.example", 1080, ProxyType.SOCKS5, "demo-user", "demo-pass"
            )
        )
        assertParsed(
            input = "SOCKS5H://proxy.example:1080/",
            expected = ParsedProxyString("proxy.example", 1080, ProxyType.SOCKS5)
        )
    }

    @Test
    fun uriCredentialsArePercentDecodedOnce() {
        assertParsed(
            input = "http://demo%40user:p%3Aa%25ss+1@proxy.example:8080",
            expected = ParsedProxyString(
                "proxy.example", 8080, ProxyType.HTTP, "demo@user", "p:a%ss+1"
            )
        )
    }

    @Test
    fun compactCredentialsAreNotPercentDecoded() {
        assertParsed(
            input = "proxy.example:1080:demo%40user:pass",
            expected = ParsedProxyString("proxy.example", 1080, null, "demo%40user", "pass")
        )
    }

    @Test
    fun protocolTokenAfterRecord() {
        assertParsed(
            input = "192.0.2.10:1080 socks5",
            expected = ParsedProxyString("192.0.2.10", 1080, ProxyType.SOCKS5)
        )
        assertParsed(
            input = "192.0.2.10:64239:demo-user:demo-pass SOCKS5",
            expected = ParsedProxyString(
                "192.0.2.10", 64239, ProxyType.SOCKS5, "demo-user", "demo-pass"
            )
        )
        assertParsed(
            input = "demo-user:demo-pass@proxy.example:1080 http",
            expected = ParsedProxyString(
                "proxy.example", 1080, ProxyType.HTTP, "demo-user", "demo-pass"
            )
        )
    }

    @Test
    fun protocolTokenBeforeRecord() {
        assertParsed(
            input = "https proxy.example:8443",
            expected = ParsedProxyString("proxy.example", 8443, ProxyType.HTTPS)
        )
        assertParsed(
            input = "socks5 proxy.example:1080:demo-user:demo-pass",
            expected = ParsedProxyString(
                "proxy.example", 1080, ProxyType.SOCKS5, "demo-user", "demo-pass"
            )
        )
    }

    @Test
    fun matchingSchemeAndProtocolTokenAreAccepted() {
        assertParsed(
            input = "socks5h://proxy.example:1080 socks5",
            expected = ParsedProxyString("proxy.example", 1080, ProxyType.SOCKS5)
        )
    }

    @Test
    fun conflictingSchemeAndProtocolTokenAreRejected() {
        assertFailure("http://proxy.example:8080 socks5", "conflict")
    }

    @Test
    fun spaceSeparatedFields() {
        assertParsed(
            input = "proxy.example 1080",
            expected = ParsedProxyString("proxy.example", 1080, null)
        )
        assertParsed(
            input = "proxy.example 1080 socks5",
            expected = ParsedProxyString("proxy.example", 1080, ProxyType.SOCKS5)
        )
        assertParsed(
            input = "proxy.example 1080 demo-user demo-pass",
            expected = ParsedProxyString("proxy.example", 1080, null, "demo-user", "demo-pass")
        )
        assertParsed(
            input = "  proxy.example   1080 demo-user demo-pass http  ",
            expected = ParsedProxyString(
                "proxy.example", 1080, ProxyType.HTTP, "demo-user", "demo-pass"
            )
        )
    }

    @Test
    fun singleCsvAndTsvRows() {
        assertParsed(
            input = "proxy.example,1080,demo-user,demo-pass,socks5",
            expected = ParsedProxyString(
                "proxy.example", 1080, ProxyType.SOCKS5, "demo-user", "demo-pass"
            )
        )
        assertParsed(
            input = "proxy.example;1080;demo-user;demo-pass",
            expected = ParsedProxyString("proxy.example", 1080, null, "demo-user", "demo-pass")
        )
        assertParsed(
            input = "proxy.example\t1080\thttps",
            expected = ParsedProxyString("proxy.example", 1080, ProxyType.HTTPS)
        )
        assertParsed(
            input = "proxy.example,1080",
            expected = ParsedProxyString("proxy.example", 1080, null)
        )
        assertParsed(
            input = "proxy.example,1080,,,http",
            expected = ParsedProxyString("proxy.example", 1080, ProxyType.HTTP)
        )
    }

    @Test
    fun compactPasswordMayContainComma() {
        assertParsed(
            input = "demo-user:pa,ss@proxy.example:1080",
            expected = ParsedProxyString("proxy.example", 1080, null, "demo-user", "pa,ss")
        )
    }

    @Test
    fun reverseOrderIsDetectedWhenUnambiguous() {
        assertParsed(
            input = "demo-user:demo-pass:45.10.1.2:8000",
            expected = ParsedProxyString("45.10.1.2", 8000, null, "demo-user", "demo-pass")
        )
        assertParsed(
            input = "socks5 demo-user:demo-pass:proxy.example:8000",
            expected = ParsedProxyString(
                "proxy.example", 8000, ProxyType.SOCKS5, "demo-user", "demo-pass"
            )
        )
    }

    @Test
    fun hostFirstOrderIsDetectedWhenUnambiguous() {
        assertParsed(
            input = "45.10.1.2:8000:user:12345",
            expected = ParsedProxyString("45.10.1.2", 8000, null, "user", "12345")
        )
    }

    @Test
    fun fourFieldRecordValidInBothOrdersIsAmbiguous() {
        val result = ProxyStringParser.parse("1.2.3.4:8080:john.doe:12345 http")

        assertTrue(result is ProxyStringParseResult.Ambiguous)
        result as ProxyStringParseResult.Ambiguous
        assertEquals(
            ParsedProxyString("1.2.3.4", 8080, ProxyType.HTTP, "john.doe", "12345"),
            result.hostFirst
        )
        assertEquals(
            ParsedProxyString("john.doe", 12345, ProxyType.HTTP, "1.2.3.4", "8080"),
            result.credentialsFirst
        )
    }

    @Test
    fun fourFieldRecordValidInNoOrderIsRejected() {
        assertFailure("localhost:8080:user:pass", "format")
    }

    @Test
    fun hostIsNormalizedToLowercase() {
        assertParsed(
            input = "Demo-User:Pass@Proxy.EXAMPLE:1080",
            expected = ParsedProxyString("proxy.example", 1080, null, "Demo-User", "Pass")
        )
    }

    @Test
    fun singleLabelHostIsAllowedOutsideFourFieldRecords() {
        assertParsed(
            input = "localhost:1080",
            expected = ParsedProxyString("localhost", 1080, null)
        )
    }

    @Test
    fun socks4SchemesAreUnsupported() {
        assertFailure("socks4://192.0.2.10:1080", "SOCKS4")
        assertFailure("socks4a://demo-user@proxy.example:1080", "SOCKS4")
        assertFailure("192.0.2.10:1080 socks4", "SOCKS4")
    }

    @Test
    fun ambiguousSocksTokenIsRejected() {
        assertFailure("socks://proxy.example:1080", "socks5")
    }

    @Test
    fun unknownSchemeIsRejected() {
        assertFailure("ftp://proxy.example:21", "Unsupported")
    }

    @Test
    fun ipv6HostsAreUnsupported() {
        assertFailure("[2001:db8::10]:1080", "IPv6")
        assertFailure("socks5h://demo-user:demo-pass@[2001:db8::10]:1080", "IPv6")
        assertFailure("[2001:db8::10]:1080:demo-user:demo-pass socks5", "IPv6")
    }

    @Test
    fun portIsRequiredAndBounded() {
        assertFailure("proxy.example", "port")
        assertFailure("proxy.example:0", "port")
        assertFailure("proxy.example:65536", "port")
        assertFailure("proxy.example:80a", "port")
        assertFailure("http://proxy.example", "port")
    }

    @Test
    fun invalidHostsAreRejected() {
        assertFailure("999.1.1.1:8080", "host")
        assertFailure("-bad.example:8080", "host")
        assertFailure("bad_host.example:8080", "host")
    }

    @Test
    fun uriPathQueryAndFragmentAreRejected() {
        assertFailure("http://proxy.example:8080/path", "path")
        assertFailure("http://proxy.example:8080?x=1", "path")
        assertFailure("http://proxy.example:8080#frag", "path")
    }

    @Test
    fun invalidPercentEncodingIsRejected() {
        assertFailure("http://demo%4:pass@proxy.example:8080", "percent")
        assertFailure("http://demo%ZZ:pass@proxy.example:8080", "percent")
        assertFailure("http://demo%FF:pass@proxy.example:8080", "UTF-8")
    }

    @Test
    fun passwordWithoutUsernameIsRejected() {
        assertFailure("http://:pass@proxy.example:8080", "Username")
        assertFailure(":pass@proxy.example:8080", "Username")
    }

    @Test
    fun controlCharactersAreRejected() {
        assertFailure("http://demo%0Auser:pass@proxy.example:8080", "control")
        assertFailure("proxy.example:1080:us\u0001er:pass", "control")
    }

    @Test
    fun emptyInputIsRejected() {
        assertFailure("   ", "empty")
    }

    @Test
    fun malformedRecordsAreRejected() {
        assertFailure("proxy.example:1080:user", "format")
        assertFailure("a@b@proxy.example:1080", "format")
        assertFailure("proxy.example 1080 user", "format")
        assertFailure("one two three four five six", "format")
    }

    @Test
    fun failureMessagesDoNotContainCredentials() {
        val result = ProxyStringParser.parse("socks4://secret-user:secret-pass@proxy.example:1080")

        assertTrue(result is ProxyStringParseResult.Failure)
        val message = (result as ProxyStringParseResult.Failure).message
        assertFalse(message.contains("secret-user"))
        assertFalse(message.contains("secret-pass"))
    }

    private fun assertParsed(input: String, expected: ParsedProxyString) {
        val result = ProxyStringParser.parse(input)
        assertEquals(input, ProxyStringParseResult.Success(expected), result)
    }

    private fun assertFailure(input: String, messageFragment: String) {
        val result = ProxyStringParser.parse(input)
        assertTrue("Expected failure for '$input' but got $result", result is ProxyStringParseResult.Failure)
        val message = (result as ProxyStringParseResult.Failure).message
        assertTrue(
            "Expected '$messageFragment' in '$message' for '$input'",
            message.contains(messageFragment, ignoreCase = true)
        )
    }
}

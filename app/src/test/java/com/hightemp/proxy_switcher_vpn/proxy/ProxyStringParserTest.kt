package com.hightemp.proxy_switcher_vpn.proxy

import androidx.annotation.StringRes
import com.hightemp.proxy_switcher_vpn.R
import com.hightemp.proxy_switcher_vpn.data.local.ProxyType
import com.hightemp.proxy_switcher_vpn.i18n.UserText
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
        assertFailure("http://proxy.example:8080 socks5", R.string.proxy_string_error_conflict)
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
        assertFailure("localhost:8080:user:pass", R.string.proxy_string_error_four_fields)
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
        assertFailure("socks4://192.0.2.10:1080", R.string.proxy_string_error_socks4)
        assertFailure("socks4a://demo-user@proxy.example:1080", R.string.proxy_string_error_socks4)
        assertFailure("192.0.2.10:1080 socks4", R.string.proxy_string_error_socks4)
    }

    @Test
    fun ambiguousSocksTokenIsRejected() {
        assertFailure("socks://proxy.example:1080", R.string.proxy_string_error_socks)
    }

    @Test
    fun unknownSchemeIsRejected() {
        assertFailure("ftp://proxy.example:21", R.string.proxy_string_error_scheme)
    }

    @Test
    fun ipv6HostsAreUnsupported() {
        assertFailure("[2001:db8::10]:1080", R.string.proxy_string_error_ipv6)
        assertFailure("socks5h://demo-user:demo-pass@[2001:db8::10]:1080", R.string.proxy_string_error_ipv6)
        assertFailure("[2001:db8::10]:1080:demo-user:demo-pass socks5", R.string.proxy_string_error_ipv6)
    }

    @Test
    fun portIsRequiredAndBounded() {
        assertFailure("proxy.example", R.string.proxy_string_error_port_required)
        assertFailure("proxy.example:0", R.string.proxy_string_error_port_range)
        assertFailure("proxy.example:65536", R.string.proxy_string_error_port_range)
        assertFailure("proxy.example:80a", R.string.proxy_string_error_port_range)
        assertFailure("http://proxy.example", R.string.proxy_string_error_port_required)
    }

    @Test
    fun invalidHostsAreRejected() {
        assertFailure("999.1.1.1:8080", R.string.proxy_string_error_ipv4)
        assertFailure("-bad.example:8080", R.string.proxy_string_error_host_name)
        assertFailure("bad_host.example:8080", R.string.proxy_string_error_host_name)
    }

    @Test
    fun uriPathQueryAndFragmentAreRejected() {
        assertFailure("http://proxy.example:8080/path", R.string.proxy_string_error_path)
        assertFailure("http://proxy.example:8080?x=1", R.string.proxy_string_error_path)
        assertFailure("http://proxy.example:8080#frag", R.string.proxy_string_error_path)
    }

    @Test
    fun invalidPercentEncodingIsRejected() {
        assertFailure("http://demo%4:pass@proxy.example:8080", R.string.proxy_string_error_percent)
        assertFailure("http://demo%ZZ:pass@proxy.example:8080", R.string.proxy_string_error_percent)
        assertFailure("http://demo%FF:pass@proxy.example:8080", R.string.proxy_string_error_utf8)
    }

    @Test
    fun passwordWithoutUsernameIsRejected() {
        assertFailure("http://:pass@proxy.example:8080", R.string.proxy_string_error_username_required)
        assertFailure(":pass@proxy.example:8080", R.string.proxy_string_error_username_required)
    }

    @Test
    fun controlCharactersAreRejected() {
        assertFailure("http://demo%0Auser:pass@proxy.example:8080", R.string.proxy_string_error_credentials_control)
        assertFailure("proxy.example:1080:us\u0001er:pass", R.string.proxy_string_error_control)
    }

    @Test
    fun emptyInputIsRejected() {
        assertFailure("   ", R.string.proxy_string_error_empty)
    }

    @Test
    fun malformedRecordsAreRejected() {
        assertFailure("proxy.example:1080:user", R.string.proxy_string_error_format)
        assertFailure("a@b@proxy.example:1080", R.string.proxy_string_error_format)
        assertFailure("proxy.example 1080 user", R.string.proxy_string_error_format)
        assertFailure("one two three four five six", R.string.proxy_string_error_format)
    }

    @Test
    fun failureMessagesDoNotContainCredentials() {
        val result = ProxyStringParser.parse("socks4://secret-user:secret-pass@proxy.example:1080")

        assertTrue(result is ProxyStringParseResult.Failure)
        val message = (result as ProxyStringParseResult.Failure).message
        assertEquals(UserText.of(R.string.proxy_string_error_socks4), message)
    }

    private fun assertParsed(input: String, expected: ParsedProxyString) {
        val result = ProxyStringParser.parse(input)
        assertEquals(input, ProxyStringParseResult.Success(expected), result)
    }

    private fun assertFailure(input: String, @StringRes expectedMessage: Int) {
        val result = ProxyStringParser.parse(input)
        assertEquals(input, ProxyStringParseResult.Failure(UserText.of(expectedMessage)), result)
    }
}

package com.hightemp.proxy_switcher_vpn.ui.screens

import com.hightemp.proxy_switcher_vpn.proxy.ProxyStringParseResult
import com.hightemp.proxy_switcher_vpn.proxy.ProxyStringParser
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyStringFormatHelpTest {
    private val reservedHosts = listOf("192.0.2.", "198.51.100.", "203.0.113.", ".example")

    @Test
    fun helpExplainsWhatAProxyStringIs() {
        assertFalse(ProxyStringFormatHelp.intro.isBlank())
        assertTrue(ProxyStringFormatHelp.rules.isNotEmpty())
    }

    @Test
    fun everySupportedExampleParses() {
        val examples = ProxyStringFormatHelp.supportedGroups.flatMap { it.examples }
        assertTrue(examples.isNotEmpty())

        examples.forEach { example ->
            val result = ProxyStringParser.parse(example)
            assertTrue(
                "Help example '$example' must parse, got $result",
                result is ProxyStringParseResult.Success
            )
        }
    }

    @Test
    fun ambiguousExampleAsksForFieldOrder() {
        val result = ProxyStringParser.parse(ProxyStringFormatHelp.ambiguousExample)

        assertTrue(result is ProxyStringParseResult.Ambiguous)
    }

    @Test
    fun everyUnsupportedExampleFails() {
        assertTrue(ProxyStringFormatHelp.unsupportedExamples.isNotEmpty())

        ProxyStringFormatHelp.unsupportedExamples.forEach { example ->
            assertTrue(
                "Help example '$example' must be rejected",
                ProxyStringParser.parse(example) is ProxyStringParseResult.Failure
            )
        }
    }

    @Test
    fun examplesUseOnlyReservedDemoHosts() {
        val examples = ProxyStringFormatHelp.supportedGroups.flatMap { it.examples } +
            ProxyStringFormatHelp.ambiguousExample +
            ProxyStringFormatHelp.unsupportedExamples

        examples.forEach { example ->
            assertTrue(
                "Help example '$example' must use a reserved demo host",
                reservedHosts.any { example.contains(it) } || example.contains("2001:db8:")
            )
        }
    }
}

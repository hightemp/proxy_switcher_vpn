package com.hightemp.proxy_switcher_vpn.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

data class ProxyStringFormatGroup(
    val title: String,
    val description: String,
    val examples: List<String>
)

/**
 * Help content for the Add/Edit "Proxy string" field. Examples are checked
 * against [com.hightemp.proxy_switcher_vpn.proxy.ProxyStringParser] in unit tests.
 */
object ProxyStringFormatHelp {
    const val supportingText = "Paste a proxy line from your provider to fill the fields below."

    const val intro = "A proxy string is one line that describes a proxy server: " +
        "its address and port, and optionally a username, password and protocol, " +
        "usually in the form your proxy provider gives it. Parse only fills the form; " +
        "review the fields and press Save."

    val supportedGroups = listOf(
        ProxyStringFormatGroup(
            title = "Address and credentials",
            description = "Without a protocol, the app connects to the proxy and detects " +
                "it, trying HTTPS, SOCKS5 and HTTP.",
            examples = listOf(
                "192.0.2.10:8080",
                "192.0.2.10:8080:user:pass",
                "user:pass@proxy.example:1080"
            )
        ),
        ProxyStringFormatGroup(
            title = "Credentials first (proxyline)",
            description = "username:password:host:port is recognized when only one field " +
                "order makes sense.",
            examples = listOf("user:pass:192.0.2.10:8000")
        ),
        ProxyStringFormatGroup(
            title = "Proxy URLs",
            description = "Schemes: http, https, socks5 and socks5h (saved as SOCKS5).",
            examples = listOf(
                "http://proxy.example:8080",
                "https://user:pass@proxy.example:8443",
                "socks5://user:pass@proxy.example:1080",
                "socks5h://proxy.example:1080"
            )
        ),
        ProxyStringFormatGroup(
            title = "Protocol before or after",
            description = "Protocol names are case-insensitive and separated by a space.",
            examples = listOf(
                "https proxy.example:8443",
                "proxy.example:1080 socks5",
                "192.0.2.10:8080:user:pass socks5"
            )
        ),
        ProxyStringFormatGroup(
            title = "Space-separated fields",
            description = "host port [username password] [protocol], in this order.",
            examples = listOf(
                "proxy.example 1080",
                "proxy.example 1080 user pass http"
            )
        ),
        ProxyStringFormatGroup(
            title = "CSV or TSV row",
            description = "One row without a header: host, port, [username, password], " +
                "[protocol]. Separators: comma, semicolon or tab.",
            examples = listOf(
                "proxy.example,1080,user,pass,http",
                "proxy.example;1080;user;pass"
            )
        ),
        ProxyStringFormatGroup(
            title = "Special characters in credentials",
            description = "Use a URL and percent-encode reserved characters. This example " +
                "has username demo@user and password p:ss.",
            examples = listOf("http://demo%40user:p%3Ass@proxy.example:8080")
        )
    )

    const val ambiguousExample = "192.0.2.10:8080:demo.user:12345"

    val unsupportedExamples = listOf(
        "socks4://192.0.2.10:1080",
        "[2001:db8::10]:1080"
    )

    val rules = listOf(
        "A port is always required (1-65535).",
        "If a line fits both host:port:user:pass and user:pass:host:port, for example " +
            "$ambiguousExample, choose the field order before saving.",
        "SOCKS4/SOCKS4a and IPv6 proxy addresses are not supported, for example " +
            unsupportedExamples.joinToString(" and ") + ".",
        "Outside URLs, credentials cannot contain ':', '@' or spaces.",
        "If the protocol cannot be detected, select it manually."
    )
}

@Composable
fun ProxyStringFormatHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Supported formats") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(ProxyStringFormatHelp.intro, style = MaterialTheme.typography.bodyMedium)
                ProxyStringFormatHelp.supportedGroups.forEach { group ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(group.title, style = MaterialTheme.typography.titleSmall)
                        Text(group.description, style = MaterialTheme.typography.bodySmall)
                        group.examples.forEach { example ->
                            FormatExample(example)
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Rules", style = MaterialTheme.typography.titleSmall)
                    ProxyStringFormatHelp.rules.forEach { rule ->
                        Text("• $rule", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(
                    text = "Addresses and credentials above are examples.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun FormatExample(example: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = example,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

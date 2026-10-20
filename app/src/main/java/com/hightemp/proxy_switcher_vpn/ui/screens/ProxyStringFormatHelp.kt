package com.hightemp.proxy_switcher_vpn.ui.screens

import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.hightemp.proxy_switcher_vpn.R
import com.hightemp.proxy_switcher_vpn.i18n.UserText
import com.hightemp.proxy_switcher_vpn.i18n.asString

data class ProxyStringFormatGroup(
    @StringRes val title: Int,
    @StringRes val description: Int,
    val examples: List<String>
)

/**
 * Help content for the Add/Edit "Proxy string" field. Examples are checked
 * against [com.hightemp.proxy_switcher_vpn.proxy.ProxyStringParser] in unit
 * tests; they are not translated.
 */
object ProxyStringFormatHelp {
    val supportedGroups = listOf(
        ProxyStringFormatGroup(
            title = R.string.format_help_group_address_title,
            description = R.string.format_help_group_address_body,
            examples = listOf(
                "192.0.2.10:8080",
                "192.0.2.10:8080:user:pass",
                "user:pass@proxy.example:1080"
            )
        ),
        ProxyStringFormatGroup(
            title = R.string.format_help_group_credentials_first_title,
            description = R.string.format_help_group_credentials_first_body,
            examples = listOf("user:pass:192.0.2.10:8000")
        ),
        ProxyStringFormatGroup(
            title = R.string.format_help_group_urls_title,
            description = R.string.format_help_group_urls_body,
            examples = listOf(
                "http://proxy.example:8080",
                "https://user:pass@proxy.example:8443",
                "socks5://user:pass@proxy.example:1080",
                "socks5h://proxy.example:1080"
            )
        ),
        ProxyStringFormatGroup(
            title = R.string.format_help_group_protocol_title,
            description = R.string.format_help_group_protocol_body,
            examples = listOf(
                "https proxy.example:8443",
                "proxy.example:1080 socks5",
                "192.0.2.10:8080:user:pass socks5"
            )
        ),
        ProxyStringFormatGroup(
            title = R.string.format_help_group_spaces_title,
            description = R.string.format_help_group_spaces_body,
            examples = listOf(
                "proxy.example 1080",
                "proxy.example 1080 user pass http"
            )
        ),
        ProxyStringFormatGroup(
            title = R.string.format_help_group_csv_title,
            description = R.string.format_help_group_csv_body,
            examples = listOf(
                "proxy.example,1080,user,pass,http",
                "proxy.example;1080;user;pass"
            )
        ),
        ProxyStringFormatGroup(
            title = R.string.format_help_group_special_title,
            description = R.string.format_help_group_special_body,
            examples = listOf("http://demo%40user:p%3Ass@proxy.example:8080")
        )
    )

    const val ambiguousExample = "192.0.2.10:8080:demo.user:12345"

    val unsupportedExamples = listOf(
        "socks4://192.0.2.10:1080",
        "[2001:db8::10]:1080"
    )

    val rules: List<UserText> = listOf(
        UserText.of(R.string.format_help_rule_port),
        UserText.of(R.string.format_help_rule_ambiguous, ambiguousExample),
        UserText.of(
            R.string.format_help_rule_unsupported,
            unsupportedExamples[0],
            unsupportedExamples[1]
        ),
        UserText.of(R.string.format_help_rule_credentials),
        UserText.of(R.string.format_help_rule_detect)
    )
}

@Composable
fun ProxyStringFormatHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.format_help_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.format_help_intro), style = MaterialTheme.typography.bodyMedium)
                ProxyStringFormatHelp.supportedGroups.forEach { group ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(group.title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(group.description), style = MaterialTheme.typography.bodySmall)
                        group.examples.forEach { example ->
                            FormatExample(example)
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.format_help_rules), style = MaterialTheme.typography.titleSmall)
                    ProxyStringFormatHelp.rules.forEach { rule ->
                        Text("• ${rule.asString()}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(
                    text = stringResource(R.string.format_help_examples_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
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

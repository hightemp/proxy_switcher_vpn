package com.hightemp.proxy_switcher_vpn.ui.screens

import com.hightemp.proxy_switcher_vpn.R
import com.hightemp.proxy_switcher_vpn.i18n.UserText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyFormValidatorTest {
    @Test
    fun blankHostIsInvalid() {
        val result = ProxyFormValidator.validate(
            hostInput = " ",
            portInput = "1080",
            usernameInput = "",
            passwordInput = ""
        )

        assertFalse(result.isValid)
        assertEquals(UserText.of(R.string.validation_host_required), result.errorMessage)
    }

    @Test
    fun hostWithWhitespaceIsInvalid() {
        val result = ProxyFormValidator.validate(
            hostInput = "proxy example.com",
            portInput = "1080",
            usernameInput = "",
            passwordInput = ""
        )

        assertFalse(result.isValid)
        assertEquals(UserText.of(R.string.validation_host_whitespace), result.errorMessage)
    }

    @Test
    fun nonNumericPortIsInvalid() {
        val result = ProxyFormValidator.validate(
            hostInput = "proxy.example",
            portInput = "eighty",
            usernameInput = "",
            passwordInput = ""
        )

        assertFalse(result.isValid)
        assertEquals(UserText.of(R.string.validation_port_number), result.errorMessage)
    }

    @Test
    fun outOfRangePortIsInvalid() {
        val result = ProxyFormValidator.validate(
            hostInput = "proxy.example",
            portInput = "65536",
            usernameInput = "",
            passwordInput = ""
        )

        assertFalse(result.isValid)
        assertEquals(UserText.of(R.string.validation_port_range), result.errorMessage)
    }

    @Test
    fun passwordWithoutUsernameIsInvalid() {
        val result = ProxyFormValidator.validate(
            hostInput = "proxy.example",
            portInput = "1080",
            usernameInput = "",
            passwordInput = "secret"
        )

        assertFalse(result.isValid)
        assertEquals(UserText.of(R.string.validation_username_required), result.errorMessage)
    }

    @Test
    fun validProxyWithoutAuthTrimsHostAndOmitsCredentials() {
        val result = ProxyFormValidator.validate(
            hostInput = " proxy.example ",
            portInput = " 1080 ",
            usernameInput = "",
            passwordInput = ""
        )

        assertTrue(result.isValid)
        assertEquals("proxy.example", result.host)
        assertEquals(1080, result.port)
        assertNull(result.username)
        assertNull(result.password)
    }

    @Test
    fun validProxyWithAuthTrimsUsernameAndKeepsPassword() {
        val result = ProxyFormValidator.validate(
            hostInput = "proxy.example",
            portInput = "8443",
            usernameInput = " user ",
            passwordInput = "secret "
        )

        assertTrue(result.isValid)
        assertEquals("user", result.username)
        assertEquals("secret ", result.password)
    }
}

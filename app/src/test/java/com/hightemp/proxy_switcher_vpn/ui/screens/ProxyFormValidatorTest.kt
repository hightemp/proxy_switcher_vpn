package com.hightemp.proxy_switcher_vpn.ui.screens

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
        assertEquals("Host is required", result.errorMessage)
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
        assertEquals("Host must not contain whitespace", result.errorMessage)
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
        assertEquals("Port must be a number", result.errorMessage)
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
        assertEquals("Port must be between 1 and 65535", result.errorMessage)
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
        assertEquals("Username is required when password is set", result.errorMessage)
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

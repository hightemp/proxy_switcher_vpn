package com.hightemp.proxy_switcher_vpn.vpn.engine

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionVpnWiringTest {
    private val appDir: File = findAppDir()

    @Test
    fun libboxArtifactHashAndRequiredAbisArePresent() {
        val aar = appDir.resolve("libs/libbox.aar")
        val hashFile = appDir.resolve("libs/libbox.aar.sha256")

        assertTrue("libbox.aar must exist", aar.isFile)
        assertTrue("libbox.aar.sha256 must exist", hashFile.isFile)
        val expectedHash = hashFile.readText()
            .trim()
            .split(Regex("\\s+"))
            .first()
        assertEquals(expectedHash, aar.sha256Hex())

        ZipFile(aar).use { zipFile ->
            assertNotNull(zipFile.getEntry("jni/arm64-v8a/libbox.so"))
            assertNotNull(zipFile.getEntry("jni/x86_64/libbox.so"))
        }
    }

    @Test
    fun productionDiBindsVpnEngineToLibboxEngine() {
        val module = appDir.resolve(
            "src/main/java/com/hightemp/proxy_switcher_vpn/di/VpnEngineModule.kt"
        ).readText()

        assertTrue(module.contains("LibboxVpnEngine"))
        assertFalse(module.contains("FakeVpnEngine"))
    }

    @Test
    fun manifestDeclaresProxyVpnServiceAsVpnServicePath() {
        val manifest = appDir.resolve("src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("android:name=\".vpn.ProxyVpnService\""))
        assertTrue(manifest.contains("android.permission.BIND_VPN_SERVICE"))
        assertTrue(manifest.contains("android.net.VpnService"))
        assertFalse(manifest.contains(".service.VpnForegroundService"))
    }

    @Test
    fun defaultNetworkMonitorUsesNonVpnNetworkForLibboxOutbound() {
        val manifest = appDir.resolve("src/main/AndroidManifest.xml").readText()
        val monitor = appDir.resolve(
            "src/main/java/com/hightemp/proxy_switcher_vpn/vpn/platform/DefaultNetworkMonitor.kt"
        ).readText()

        assertTrue(manifest.contains("android.permission.CHANGE_NETWORK_STATE"))
        assertTrue(monitor.contains("NetworkCapabilities.NET_CAPABILITY_NOT_VPN"))
        assertTrue(monitor.contains("requestNetwork(request, networkCallback, handler)"))
        assertFalse(monitor.contains("registerDefaultNetworkCallback"))
    }

    private fun findAppDir(): File {
        val userDir = requireNotNull(System.getProperty("user.dir"))
        return generateSequence(File(userDir).absoluteFile) {
            it.parentFile
        }.mapNotNull { candidate ->
            when {
                candidate.resolve("src/main/AndroidManifest.xml").isFile -> candidate
                candidate.resolve("app/src/main/AndroidManifest.xml").isFile ->
                    candidate.resolve("app")
                else -> null
            }
        }.first()
    }

    private fun File.sha256Hex(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }
}

package com.hightemp.proxy_switcher_vpn.vpn.engine

import java.io.File
import java.security.MessageDigest
import java.util.Properties
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
        val aar = File(requireNotNull(System.getProperty("libbox.artifact.path")))
        val projectDir = requireNotNull(appDir.parentFile)
        val metadataFile = projectDir.resolve("gradle/libbox.properties")
        val metadata = Properties().apply {
            metadataFile.inputStream().use(::load)
        }

        assertTrue("downloaded libbox.aar must exist", aar.isFile)
        assertTrue("gradle/libbox.properties must exist", metadataFile.isFile)
        assertFalse(
            "libbox.aar must not be stored in the source tree",
            appDir.resolve("libs/libbox.aar").exists()
        )
        assertTrue(requireNotNull(metadata.getProperty("artifactUrl")).startsWith("https://"))
        assertTrue(requireNotNull(metadata.getProperty("sourceUrl")).startsWith("https://"))
        assertEquals(requireNotNull(metadata.getProperty("size")).toLong(), aar.length())
        assertEquals(requireNotNull(metadata.getProperty("sha256")), aar.sha256Hex())

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

    @Test
    fun proxyVpnServiceSupportsRuntimeRouteSwitchAction() {
        val service = appDir.resolve(
            "src/main/java/com/hightemp/proxy_switcher_vpn/vpn/ProxyVpnService.kt"
        ).readText()

        assertTrue(service.contains("ACTION_SWITCH_ROUTE"))
        assertTrue(service.contains("switchRoute(intent)"))
        assertTrue(service.contains("EXTRA_PROXY_ID_DIRECT"))
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

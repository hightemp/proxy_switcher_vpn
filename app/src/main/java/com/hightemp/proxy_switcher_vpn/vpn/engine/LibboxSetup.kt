package com.hightemp.proxy_switcher_vpn.vpn.engine

import android.content.Context
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.SetupOptions
import java.io.File
import java.util.Locale

object LibboxSetup {
    @Volatile
    private var initialized = false

    fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return

            runCatching {
                Libbox.setLocale(Locale.getDefault().toLanguageTag().replace("-", "_"))
            }

            val baseDir = context.filesDir.apply { mkdirs() }
            val workingDir = context.getExternalFilesDir(null)
                ?: File(baseDir, "libbox-working").apply { mkdirs() }
            val tempDir = context.cacheDir.apply { mkdirs() }

            Libbox.setup(
                SetupOptions().apply {
                    basePath = baseDir.path
                    workingPath = workingDir.path
                    tempPath = tempDir.path
                    logMaxLines = LOG_MAX_LINES
                    debug = false
                    crashReportSource = "Proxy Switcher VPN"
                }
            )
            initialized = true
        }
    }

    private const val LOG_MAX_LINES = 1000L
}

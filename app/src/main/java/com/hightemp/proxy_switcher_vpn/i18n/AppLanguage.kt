package com.hightemp.proxy_switcher_vpn.i18n

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/** A language the app ships translations for. Names are shown in their own language. */
data class AppLanguage(val tag: String, val nativeName: String)

object AppLanguages {
    /** Empty tag means "follow the system language". */
    const val SYSTEM = ""

    val supported = listOf(
        AppLanguage("en", "English"),
        AppLanguage("ru", "Русский"),
        AppLanguage("uk", "Українська"),
        AppLanguage("de", "Deutsch"),
        AppLanguage("fr", "Français"),
        AppLanguage("es", "Español"),
        AppLanguage("it", "Italiano"),
        AppLanguage("pt-BR", "Português (Brasil)"),
        AppLanguage("pl", "Polski"),
        AppLanguage("zh-CN", "简体中文"),
        AppLanguage("ja", "日本語"),
        AppLanguage("ko", "한국어")
    )

    private const val PREFS = "app_language"
    private const val KEY_TAG = "language_tag"

    /** The chosen language tag, or [SYSTEM]. */
    fun current(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            return locales?.takeIf { !it.isEmpty }?.get(0)?.toLanguageTag()
                ?.let(::matchSupported)
                ?: SYSTEM
        }
        return storedTag(context)
    }

    fun apply(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TAG, tag)
            .apply()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    /**
     * Applies the chosen language to a non-activity context (the VPN service,
     * receivers). Android 13+ already does this for the whole app.
     */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = storedTag(base).takeIf { it.isNotEmpty() } ?: return base
        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(tag))
        }
        return base.createConfigurationContext(configuration)
    }

    fun englishContext(base: Context): Context {
        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(Locale.ENGLISH)
        }
        return base.createConfigurationContext(configuration)
    }

    private fun storedTag(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TAG, SYSTEM)
            .orEmpty()
    }

    internal fun matchSupported(tag: String): String? {
        val locale = Locale.forLanguageTag(tag)
        return supported.firstOrNull { it.tag.equals(tag, ignoreCase = true) }?.tag
            ?: supported.firstOrNull { Locale.forLanguageTag(it.tag).language == locale.language }?.tag
    }
}

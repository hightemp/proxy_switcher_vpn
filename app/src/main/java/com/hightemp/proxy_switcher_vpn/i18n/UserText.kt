package com.hightemp.proxy_switcher_vpn.i18n

import android.content.Context
import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * Text shown to the user. Core layers produce it without a Context; the UI
 * resolves it in the current app language and logs render it in English.
 * Arguments may themselves be [UserText].
 */
sealed interface UserText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UserText

    data class Plural(
        @PluralsRes val id: Int,
        val quantity: Int,
        val args: List<Any> = listOf(quantity)
    ) : UserText

    /** Text that is not translated, such as engine errors or user data. */
    data class Raw(val text: String) : UserText

    companion object {
        fun of(@StringRes id: Int, vararg args: Any): UserText = Res(id, args.toList())

        fun raw(text: String): UserText = Raw(text)

        /** English resources for log rendering; set once at app startup. */
        @Volatile
        var englishResources: Resources? = null
    }
}

fun UserText.resolve(resources: Resources): String {
    return when (this) {
        is UserText.Raw -> text
        is UserText.Plural -> resources.getQuantityString(
            id,
            quantity,
            *args.map { arg -> if (arg is UserText) arg.resolve(resources) else arg }.toTypedArray()
        )
        is UserText.Res -> if (args.isEmpty()) {
            resources.getString(id)
        } else {
            resources.getString(
                id,
                *args.map { arg -> if (arg is UserText) arg.resolve(resources) else arg }.toTypedArray()
            )
        }
    }
}

fun UserText.resolve(context: Context): String = resolve(context.resources)

/** English rendering for logs, broadcasts and persisted diagnostics. */
fun UserText.english(): String {
    UserText.englishResources?.let { return resolve(it) }
    return when (this) {
        is UserText.Raw -> text
        is UserText.Plural -> "#$id($quantity)"
        is UserText.Res -> "#$id" + if (args.isEmpty()) "" else args.joinToString(prefix = "(", postfix = ")") { arg ->
            if (arg is UserText) arg.english() else arg.toString()
        }
    }
}

@Composable
fun UserText.asString(): String {
    LocalConfiguration.current
    return resolve(LocalContext.current.resources)
}

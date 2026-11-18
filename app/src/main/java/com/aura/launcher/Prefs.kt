package com.aura.launcher

import android.content.Context

/** Préférences persistantes : favoris (dans l'ordre) et apps masquées. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("aura", Context.MODE_PRIVATE)

    var favorites: List<String>
        get() = sp.getString(KEY_FAVORITES, "").orEmpty().split(SEP).filter { it.isNotBlank() }
        set(value) = sp.edit().putString(KEY_FAVORITES, value.joinToString(SEP)).apply()

    var hidden: Set<String>
        get() = sp.getStringSet(KEY_HIDDEN, emptySet()).orEmpty().toSet()
        set(value) = sp.edit().putStringSet(KEY_HIDDEN, value).apply()

    var showIcons: Boolean
        get() = sp.getBoolean(KEY_ICONS, true)
        set(value) = sp.edit().putBoolean(KEY_ICONS, value).apply()

    var autoLaunch: Boolean
        get() = sp.getBoolean(KEY_AUTO_LAUNCH, true)
        set(value) = sp.edit().putBoolean(KEY_AUTO_LAUNCH, value).apply()

    private companion object {
        const val KEY_AUTO_LAUNCH = "auto_launch"
        const val KEY_FAVORITES = "favorites"
        const val KEY_HIDDEN = "hidden"
        const val KEY_ICONS = "show_icons"
        const val SEP = "\n"
    }
}

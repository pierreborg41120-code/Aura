package com.aura.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap

/** État observable de l'interface (Compose se redessine quand il change). */
class LauncherState {
    var apps by mutableStateOf<List<AppEntry>>(emptyList())
    var favorites by mutableStateOf<List<String>>(emptyList())
    var hidden by mutableStateOf<Set<String>>(emptySet())
    var showIcons by mutableStateOf(true)
    var autoLaunch by mutableStateOf(true)
    var drawerOpen by mutableStateOf(false)
    var settingsOpen by mutableStateOf(false)
    var query by mutableStateOf("")

    /** Apps visibles dans la liste (hors apps masquées). */
    val visibleApps: List<AppEntry>
        get() = apps.filter { it.key !in hidden }

    /** Favoris dans l'ordre choisi, en ignorant ceux qui ont été désinstallés. */
    val favoriteApps: List<AppEntry>
        get() {
            val byKey = apps.associateBy { it.key }
            return favorites.mapNotNull { byKey[it] }
        }
}

/** Ce que l'interface peut demander à l'activité. */
interface LauncherActions {
    fun launch(app: AppEntry)
    fun openAppInfo(app: AppEntry)
    fun uninstall(app: AppEntry)
    fun toggleFavorite(app: AppEntry)
    fun moveFavorite(app: AppEntry, delta: Int)
    fun setHidden(key: String, hidden: Boolean)
    fun setShowIcons(show: Boolean)
    fun setAutoLaunch(enabled: Boolean)
    fun openAlarms()
    fun openCalendar()
    fun expandNotifications()
    fun openWallpaperPicker()
    fun openDefaultLauncherSettings()
    fun loadIcon(app: AppEntry, sizePx: Int): ImageBitmap?
}

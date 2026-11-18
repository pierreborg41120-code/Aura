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
    var showNotificationPreviews by mutableStateOf(true)
    var textScale by mutableStateOf(1f)

    var notifications by mutableStateOf<Map<String, NotificationInfo>>(emptyMap())
    var media by mutableStateOf<MediaInfo?>(null)
    var hasNotificationAccess by mutableStateOf(false)
    var hasLockAccess by mutableStateOf(false)
    var isDefaultHome by mutableStateOf(false)
    var onboardingOpen by mutableStateOf(false)
    var notificationServiceConnected by mutableStateOf(false)
    var mediaSessionsAllowed by mutableStateOf(false)

    var drawerOpen by mutableStateOf(false)
    var settingsOpen by mutableStateOf(false)
    var query by mutableStateOf("")
    /** App en cours de renommage (affiche la boîte de dialogue). */
    var renaming by mutableStateOf<AppEntry?>(null)

    /** Apps visibles dans la liste (hors apps masquées). */
    val visibleApps: List<AppEntry>
        get() = apps.filter { it.key !in hidden }

    /** Favoris dans l'ordre choisi, en ignorant ceux qui ont été désinstallés. */
    val favoriteApps: List<AppEntry>
        get() {
            val byKey = apps.associateBy { it.key }
            return favorites.mapNotNull { byKey[it] }
        }

    fun notificationsFor(app: AppEntry): NotificationInfo? = notifications[app.notificationKey]

    // --- Raccourcis d'app ---
    /** Panneau de raccourcis ouvert depuis l'accueil (null = fermé). */
    var shortcutPanel by mutableStateOf<ShortcutPanel?>(null)

    // --- Sous l'heure : batterie + agenda ---
    var showBattery by mutableStateOf(true)
    var showNextEvent by mutableStateOf(false)
    var hasCalendarAccess by mutableStateOf(false)
    var battery by mutableStateOf<BatteryInfo?>(null)
    var nextEvent by mutableStateOf<NextEvent?>(null)
}

/** Ce que l'interface peut demander à l'activité. */
interface LauncherActions {
    fun launch(app: AppEntry)
    fun openAppInfo(app: AppEntry)
    fun uninstall(app: AppEntry)
    fun toggleFavorite(app: AppEntry)
    fun moveFavorite(app: AppEntry, delta: Int)
    fun setHidden(key: String, hidden: Boolean)
    fun rename(app: AppEntry, newLabel: String)
    fun setShowIcons(show: Boolean)
    fun setAutoLaunch(enabled: Boolean)
    fun setShowNotificationPreviews(show: Boolean)
    fun setTextScale(scale: Float)
    fun openAlarms()
    fun openCalendar()
    fun expandNotifications()
    fun lockScreen()
    fun openWallpaperPicker()
    fun openDefaultLauncherSettings()
    fun openNotificationAccessSettings()
    fun openLockAccessSettings()
    fun restartNotificationService()
    fun requestDefaultHome()
    fun openAuraAppDetails()
    fun openOnboarding()
    fun finishOnboarding()
    fun mediaPlayPause()
    fun mediaNext()
    fun mediaPrevious()
    fun mediaOpen()
    fun loadIcon(app: AppEntry, sizePx: Int): ImageBitmap?

    // --- Raccourcis d'app ---
    /** Raccourcis de l'app (liste vide si indisponibles). À appeler hors du thread principal. */
    fun loadShortcuts(app: AppEntry): List<AppShortcut>
    fun launchShortcut(shortcut: AppShortcut)
    /** À appeler hors du thread principal. */
    fun loadShortcutIcon(shortcut: AppShortcut, sizePx: Int): ImageBitmap?

    // --- Sous l'heure : batterie + agenda ---
    fun setShowBattery(show: Boolean)
    fun setShowNextEvent(show: Boolean)
    fun openEvent(event: NextEvent)
}

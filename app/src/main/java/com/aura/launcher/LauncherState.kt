package com.aura.launcher

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.view.View
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

    // --- Widget par app ---
    /** Widget attaché à chaque app : clé de l'app → appWidgetId. */
    var widgets by mutableStateOf<Map<String, Int>>(emptyMap())

    // --- Sous l'heure : batterie + agenda ---
    var showBattery by mutableStateOf(true)
    var showNextEvent by mutableStateOf(false)
    var hasCalendarAccess by mutableStateOf(false)
    var battery by mutableStateOf<BatteryInfo?>(null)
    var nextEvent by mutableStateOf<NextEvent?>(null)

    // --- Design ---
    /** Hauteur choisie (dp) pour le widget de chaque app ; absente = hauteur calculée. */
    var widgetHeights by mutableStateOf<Map<String, Int>>(emptyMap())
    /** Augmente à chaque retour sur le launcher : déclenche l'apparition douce de l'accueil. */
    var homeEntrance by mutableStateOf(0)

    // --- Accueil ---
    /** Widget libre de l'accueil (null = aucun). */
    var homeWidgetId by mutableStateOf<Int?>(null)
    var homeWidgetPosition by mutableStateOf(HOME_WIDGET_ABOVE_FAVORITES)
    /** Hauteur choisie (dp) ; null = hauteur calculée d'après le widget. */
    var homeWidgetHeight by mutableStateOf<Int?>(null)
    /** Sélecteur du widget d'accueil ouvert. */
    var homeWidgetPicking by mutableStateOf(false)
    var showHeadphoneApps by mutableStateOf(true)
    var headphonesConnected by mutableStateOf(false)
    /** Paquets des apps de musique à proposer aux écouteurs, par ordre de préférence. */
    var headphonePackages by mutableStateOf<List<String>>(emptyList())
    var allowRotation by mutableStateOf(false)
    var hapticsEnabled by mutableStateOf(true)
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
    // --- Lecteur ---
    /** Se déplace dans le morceau en cours (si l'app de musique l'accepte). */
    fun mediaSeek(positionMs: Long)
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

    // --- Widget par app ---
    /** Widgets proposés par l'app. À appeler hors du thread principal. */
    fun loadWidgetProviders(app: AppEntry): List<WidgetProvider>
    /** Aperçu d'un widget. À appeler hors du thread principal. */
    fun loadWidgetPreview(provider: WidgetProvider, maxPx: Int): ImageBitmap?
    /** Attache (ou remplace) le widget de l'app : fenêtre d'autorisation puis configuration si besoin. */
    fun addWidget(app: AppEntry, provider: WidgetProvider)
    fun removeWidget(app: AppEntry)
    /** Vue Android du widget, créée par l'hôte de widgets d'Aura (null si le widget a disparu). */
    fun createWidgetView(context: Context, appWidgetId: Int): View?
    fun widgetInfo(appWidgetId: Int): AppWidgetProviderInfo?

    // --- Design ---
    /** Mémorise la hauteur (dp) du widget de l'app ; null revient à la hauteur calculée. */
    fun setWidgetHeight(app: AppEntry, heightDp: Int?)

    // --- Accueil ---
    /** Tous les widgets installés, groupés par app. À appeler hors du thread principal. */
    fun loadAllWidgetGroups(): List<WidgetGroup>
    /** Ajoute (ou remplace) le widget de l'accueil : autorisation puis configuration si besoin. */
    fun addHomeWidget(provider: WidgetProvider)
    fun removeHomeWidget()
    fun setHomeWidgetPosition(position: String)
    /** null revient à la hauteur calculée. */
    fun setHomeWidgetHeight(heightDp: Int?)
    fun setShowHeadphoneApps(show: Boolean)
    fun setAllowRotation(allow: Boolean)
    fun setHapticsEnabled(enabled: Boolean)
}

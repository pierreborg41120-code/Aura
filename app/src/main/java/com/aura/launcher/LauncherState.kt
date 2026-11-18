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

    // --- Réponses aux notifications ---
    /** Réglage « Répondre depuis l'accueil » : champ de réponse et effacement dans la carte de l'app. */
    var replyFromHome by mutableStateOf(true)

    // --- Météo ---
    var weatherEnabled by mutableStateOf(false)
    var weatherUseCity by mutableStateOf(false)
    var weatherFahrenheit by mutableStateOf(false)
    var weatherCity by mutableStateOf<WeatherPlace?>(null)
    /** Dernières prévisions affichables (null = rien à montrer, ou trop vieilles). */
    var weather by mutableStateOf<WeatherData?>(null)
    /** Message d'état pour les réglages (position introuvable, hors ligne…), null si tout va bien. */
    var weatherStatus by mutableStateOf<String?>(null)
    var weatherSheetOpen by mutableStateOf(false)

    // --- Tiroir et recherche ---
    /** Interrupteur « Chercher dans les contacts » (n'agit que si la permission est accordée). */
    var searchContacts by mutableStateOf(false)
    var hasContactsAccess by mutableStateOf(false)
    /** Dans chaque lettre, les apps les plus utilisées d'abord. */
    var sortByUsage by mutableStateOf(true)
    /** Ligne d'apps suggérées en haut du tiroir. */
    var showSuggestions by mutableStateOf(true)
    /** Scores d'utilisation (clé de l'app → score), mis à jour à chaque retour sur Aura. */
    var usageScores by mutableStateOf<Map<String, Double>>(emptyMap())
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

    // --- Réponses aux notifications ---
    fun setReplyFromHome(enabled: Boolean)

    // --- Météo ---
    fun setWeatherEnabled(enabled: Boolean)
    /** Passe sur « Ma position » (demande la localisation approximative si besoin). */
    fun useWeatherLocation()
    /** Passe sur « Ville… » (garde la ville déjà choisie, s'il y en a une). */
    fun useWeatherCity()
    /** Villes trouvées : vide si aucune, null si la recherche a échoué (hors ligne). */
    suspend fun searchWeatherCity(name: String): List<WeatherPlace>?
    fun setWeatherCity(place: WeatherPlace)
    fun setWeatherFahrenheit(fahrenheit: Boolean)

    // --- Tiroir et recherche ---
    /** Active la recherche dans les contacts (demande la permission si besoin). */
    fun setSearchContacts(enabled: Boolean)
    fun setSortByUsage(enabled: Boolean)
    fun setShowSuggestions(show: Boolean)
    /** Efface l'historique d'utilisation. */
    fun clearUsage()
    /** Contacts correspondant à la saisie. À appeler hors du thread principal. */
    fun searchContacts(query: String): List<com.aura.launcher.search.ContactResult>
    /** Photo du contact. À appeler hors du thread principal. */
    fun loadContactPhoto(contact: com.aura.launcher.search.ContactResult, sizePx: Int): ImageBitmap?
    fun openContact(contact: com.aura.launcher.search.ContactResult)
    /** Ouvre le composeur avec le numéro du contact (aucune permission d'appel nécessaire). */
    fun dialContact(contact: com.aura.launcher.search.ContactResult)
    fun openSystemSetting(shortcut: com.aura.launcher.search.SettingsShortcut)
    fun webSearch(query: String)
    fun copyToClipboard(text: String)
}

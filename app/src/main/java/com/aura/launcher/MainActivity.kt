package com.aura.launcher

import android.Manifest
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.aura.launcher.ui.AuraTheme
import com.aura.launcher.ui.LauncherScreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), LauncherActions {

    private lateinit var repo: AppRepository
    private lateinit var prefs: Prefs
    private lateinit var media: MediaWatcher
    private lateinit var widgets: WidgetRepository
    private val state = LauncherState()
    private var stopObserving: (() -> Unit)? = null

    /** Apps telles que lues sur le système, avant renommage. */
    private var rawApps: List<AppEntry> = emptyList()
    private var customLabels: Map<String, String> = emptyMap()

    /** Fenêtre système « Définir Aura comme écran d'accueil ». */
    private val homeRoleRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshPermissions()
            if (!state.isDefaultHome) openDefaultLauncherSettings()
        }

    /** Vrai quand on part vers les réglages d'Android depuis ceux d'Aura : on les rouvrira au retour. */
    private var keepSettingsOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        repo = AppRepository(this)
        prefs = Prefs(this)
        media = MediaWatcher(this) { state.media = it }

        state.favorites = prefs.favorites
        state.hidden = prefs.hidden
        state.showIcons = prefs.showIcons
        state.autoLaunch = prefs.autoLaunch
        state.showNotificationPreviews = prefs.showNotificationPreviews
        state.textScale = prefs.textScale
        customLabels = prefs.customLabels
        state.onboardingOpen = !prefs.onboardingDone

        // Widgets : on oublie ceux dont le fournisseur a disparu (app désinstallée…).
        widgets = WidgetRepository(this)
        state.widgets = widgets.cleanUp(prefs.widgetIds)
        prefs.widgetIds = state.widgets
        // --- Design ---
        state.widgetHeights = prefs.widgetHeights.filterKeys { it in state.widgets }

        refreshApps()
        stopObserving = repo.observeChanges { refreshApps() }

        lifecycleScope.launch {
            NotificationStore.byApp.collect { state.notifications = it }
        }
        lifecycleScope.launch {
            NotificationStore.connected.collect { state.notificationServiceConnected = it }
        }
        lifecycleScope.launch {
            NotificationStore.mediaTokens.collect { media.onNotificationsChanged() }
        }

        setContent {
            AuraTheme {
                LauncherScreen(state = state, actions = this)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        widgets.startListening()
        // --- Design ---
        state.homeEntrance++
        refreshPermissions()
        if (state.hasNotificationAccess) {
            // Après une mise à jour de l'APK, Android oublie parfois de relancer le service.
            if (!NotificationStore.connected.value) rebindNotificationService()
            NotificationStore.refreshNow()
            media.start()
            state.mediaSessionsAllowed = media.sessionsAllowed
        }
    }

    override fun onDestroy() {
        stopObserving?.invoke()
        super.onDestroy()
    }

    /** Bouton Accueil pressé alors que le launcher est déjà affiché : on revient à l'accueil. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) resetToHome()
    }

    /** En revenant d'une app, on retrouve l'accueil propre (liste fermée, recherche vidée). */
    override fun onStop() {
        super.onStop()
        media.stop()
        widgets.stopListening()
        if (keepSettingsOpen) {
            keepSettingsOpen = false
            state.drawerOpen = false
        } else if (pendingWidget != null) {
            // Écran de configuration d'un widget ouvert : on garde la carte de l'app pour le retour.
            state.drawerOpen = false
        } else {
            resetToHome()
        }
    }

    private fun resetToHome() {
        state.drawerOpen = false
        state.settingsOpen = false
        state.renaming = null
        state.query = ""
        state.shortcutPanel = null
    }

    private fun rebindNotificationService() {
        try {
            NotificationListenerService.requestRebind(ComponentName(this, NotificationService::class.java))
        } catch (e: Exception) {
        }
    }

    private fun refreshPermissions() {
        state.isDefaultHome = isDefaultHome()
        state.hasNotificationAccess =
            NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        val lockComponent = ComponentName(this, LockService::class.java)
        state.hasLockAccess = enabledServices.split(':').any {
            ComponentName.unflattenFromString(it) == lockComponent
        }
    }

    private fun isDefaultHome(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return roles.isRoleHeld(RoleManager.ROLE_HOME)
            }
        }
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(home, 0)?.activityInfo?.packageName == packageName
    }

    private fun refreshApps() {
        lifecycleScope.launch {
            rawApps = repo.loadApps()
            applyLabels()
            // Une app a pu être désinstallée : son widget ne doit plus apparaître.
            if (pendingWidget == null && state.widgets.isNotEmpty()) {
                val valid = widgets.cleanUp(state.widgets)
                if (valid != state.widgets) {
                    state.widgets = valid
                    prefs.widgetIds = valid
                }
            }
        }
    }

    private fun applyLabels() {
        state.apps = AppEntry.sort(
            rawApps.map { app -> customLabels[app.key]?.let { app.copy(label = it) } ?: app },
        )
    }

    private fun tryStart(intent: Intent, errorMessage: String = "Aucune application ne gère cette action") {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            toast(errorMessage)
        } catch (e: SecurityException) {
            toast(errorMessage)
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    // --- LauncherActions ---

    override fun launch(app: AppEntry) {
        try {
            getSystemService(LauncherApps::class.java)
                .startMainActivity(app.component, app.user, null, null)
        } catch (e: Exception) {
            toast("Impossible d'ouvrir ${app.label}")
            refreshApps()
        }
    }

    override fun openAppInfo(app: AppEntry) {
        try {
            getSystemService(LauncherApps::class.java)
                .startAppDetailsActivity(app.component, app.user, null, null)
        } catch (e: Exception) {
            tryStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")))
        }
    }

    override fun uninstall(app: AppEntry) {
        tryStart(
            Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
                .putExtra(Intent.EXTRA_USER, app.user),
            "Cette application ne peut pas être désinstallée",
        )
    }

    override fun toggleFavorite(app: AppEntry) {
        val current = state.favorites
        state.favorites = if (app.key in current) current - app.key else current + app.key
        prefs.favorites = state.favorites
    }

    override fun moveFavorite(app: AppEntry, delta: Int) {
        // On travaille sur la liste des favoris encore installés pour que l'ordre affiché soit cohérent.
        val list = state.favoriteApps.map { it.key }.toMutableList()
        val from = list.indexOf(app.key)
        val to = from + delta
        if (from < 0 || to !in list.indices) return
        list.add(to, list.removeAt(from))
        state.favorites = list
        prefs.favorites = list
    }

    override fun setHidden(key: String, hidden: Boolean) {
        state.hidden = if (hidden) state.hidden + key else state.hidden - key
        prefs.hidden = state.hidden
    }

    override fun rename(app: AppEntry, newLabel: String) {
        val label = newLabel.trim()
        customLabels = if (label.isEmpty() || label == app.originalLabel) {
            customLabels - app.key
        } else {
            customLabels + (app.key to label)
        }
        prefs.customLabels = customLabels
        applyLabels()
    }

    override fun setShowIcons(show: Boolean) {
        state.showIcons = show
        prefs.showIcons = show
    }

    override fun setAutoLaunch(enabled: Boolean) {
        state.autoLaunch = enabled
        prefs.autoLaunch = enabled
    }

    override fun setShowNotificationPreviews(show: Boolean) {
        state.showNotificationPreviews = show
        prefs.showNotificationPreviews = show
    }

    override fun setTextScale(scale: Float) {
        state.textScale = scale
        prefs.textScale = scale
    }

    override fun openAlarms() = tryStart(Intent(AlarmClock.ACTION_SHOW_ALARMS))

    override fun openCalendar() =
        tryStart(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR))

    @SuppressLint("WrongConstant", "PrivateApi")
    override fun expandNotifications() {
        try {
            val service = getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager")
                .getMethod("expandNotificationsPanel")
                .invoke(service)
        } catch (e: Exception) {
            // Certains constructeurs bloquent cet appel : on ignore silencieusement.
        }
    }

    override fun lockScreen() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            toast("Le verrouillage nécessite Android 9 ou plus récent")
            return
        }
        if (!LockService.lockScreen()) {
            toast("Active « Double-toucher pour verrouiller » dans les réglages d'Aura")
        }
    }

    override fun openWallpaperPicker() =
        tryStart(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Fond d'écran"))

    override fun openDefaultLauncherSettings() = tryStart(Intent(Settings.ACTION_HOME_SETTINGS))

    override fun openNotificationAccessSettings() {
        keepSettingsOpen = true
        val component = ComponentName(this, NotificationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                        .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                return
            } catch (e: Exception) {
                // Certains constructeurs n'ont pas l'écran de détail : on ouvre la liste.
            }
        }
        tryStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    override fun openLockAccessSettings() {
        keepSettingsOpen = true
        tryStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    override fun restartNotificationService() {
        rebindNotificationService()
        NotificationStore.refreshNow()
        media.stop()
        media.start()
        state.mediaSessionsAllowed = media.sessionsAllowed
        toast("Service relancé")
    }

    override fun requestDefaultHome() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME) &&
                !roles.isRoleHeld(RoleManager.ROLE_HOME)
            ) {
                try {
                    homeRoleRequest.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME))
                    return
                } catch (e: Exception) {
                    // Pas de fenêtre système : on ouvre la page des réglages.
                }
            }
        }
        openDefaultLauncherSettings()
    }

    override fun openAuraAppDetails() {
        keepSettingsOpen = true
        tryStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    override fun openOnboarding() {
        state.settingsOpen = false
        state.onboardingOpen = true
    }

    override fun finishOnboarding() {
        state.onboardingOpen = false
        prefs.onboardingDone = true
    }

    override fun mediaPlayPause() = media.playPause()

    override fun mediaNext() = media.next()

    override fun mediaPrevious() = media.previous()

    override fun mediaOpen() {
        if (media.openPlayer()) return
        val pkg = media.playerPackage ?: return
        packageManager.getLaunchIntentForPackage(pkg)?.let { tryStart(it) }
    }

    override fun loadIcon(app: AppEntry, sizePx: Int): ImageBitmap? = repo.loadIcon(app, sizePx)

    // --- Raccourcis d'app ---

    private val shortcutRepo by lazy { ShortcutRepository(this) }

    override fun loadShortcuts(app: AppEntry): List<AppShortcut> = shortcutRepo.shortcutsFor(app)

    override fun launchShortcut(shortcut: AppShortcut) {
        if (!shortcutRepo.start(shortcut)) toast("Impossible d'ouvrir « ${shortcut.label} »")
    }

    override fun loadShortcutIcon(shortcut: AppShortcut, sizePx: Int): ImageBitmap? =
        shortcutRepo.loadIcon(shortcut, sizePx)

    // --- Sous l'heure : batterie + agenda ---
    private var deviceInfo: DeviceInfoWatcher? = null
    private var stopCalendarObserver: (() -> Unit)? = null
    private var calendarJob: Job? = null

    /** Fenêtre système « Autoriser Aura à accéder à votre agenda ? ». */
    private val calendarPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            state.hasCalendarAccess = granted
            if (granted) {
                state.showNextEvent = true
                prefs.showNextEvent = true
                startCalendarObserver()
                refreshNextEvent()
            } else if (!shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR)) {
                // Refus définitif : Android n'affichera plus la fenêtre, on ouvre la page d'Aura.
                toast("Touche « Autorisations » → « Agenda » → « Autoriser »")
                openAuraAppDetails()
            }
        }

    // Branché sur le cycle de vie pour ne pas toucher à onCreate/onStart/onStop ci-dessus.
    // ON_CREATE arrive après MainActivity.onCreate : prefs est alors initialisé.
    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                state.showBattery = prefs.showBattery
                state.showNextEvent = prefs.showNextEvent
                deviceInfo = DeviceInfoWatcher(
                    context = this@MainActivity,
                    onBattery = { state.battery = it },
                    onTick = { refreshNextEvent() },
                )
            }

            override fun onStart(owner: LifecycleOwner) {
                deviceInfo?.start()
            }

            override fun onResume(owner: LifecycleOwner) {
                // L'autorisation a pu être accordée ou retirée dans les réglages d'Android.
                state.hasCalendarAccess = CalendarReader.hasPermission(this@MainActivity)
                startCalendarObserver()
                refreshNextEvent()
            }

            override fun onStop(owner: LifecycleOwner) {
                deviceInfo?.stop()
                stopCalendarObserver?.invoke()
                stopCalendarObserver = null
            }
        })
    }

    private fun startCalendarObserver() {
        if (stopCalendarObserver != null || !state.showNextEvent) return
        stopCalendarObserver = CalendarReader.observe(this) { refreshNextEvent() }
    }

    private fun refreshNextEvent() {
        if (!state.showNextEvent || !state.hasCalendarAccess) {
            state.nextEvent = null
            return
        }
        calendarJob?.cancel()
        calendarJob = lifecycleScope.launch {
            state.nextEvent = CalendarReader.nextEvent(this@MainActivity)
        }
    }

    override fun setShowBattery(show: Boolean) {
        state.showBattery = show
        prefs.showBattery = show
    }

    override fun setShowNextEvent(show: Boolean) {
        if (!show) {
            state.showNextEvent = false
            prefs.showNextEvent = false
            stopCalendarObserver?.invoke()
            stopCalendarObserver = null
            state.nextEvent = null
            return
        }
        if (CalendarReader.hasPermission(this)) {
            state.hasCalendarAccess = true
            state.showNextEvent = true
            prefs.showNextEvent = true
            startCalendarObserver()
            refreshNextEvent()
        } else {
            calendarPermissionRequest.launch(Manifest.permission.READ_CALENDAR)
        }
    }

    override fun openEvent(event: NextEvent) {
        try {
            startActivity(CalendarReader.viewIntent(event).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            openCalendar()
        }
    }

    // --- Widget par app ---

    /** Widget en cours d'ajout (entre l'allocation de l'id et la fin de la configuration). */
    private class PendingWidget(val app: AppEntry, val id: Int)

    private var pendingWidget: PendingWidget? = null

    /** Fenêtre système « Autoriser Aura à créer des widgets et à accéder à leurs données ? ». */
    private val widgetBindRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val pending = pendingWidget ?: return@registerForActivityResult
            if (result.resultCode == RESULT_OK) {
                configureOrFinish(pending)
            } else {
                cancelPendingWidget()
            }
        }

    override fun loadWidgetProviders(app: AppEntry): List<WidgetProvider> = widgets.providersFor(app)

    override fun loadWidgetPreview(provider: WidgetProvider, maxPx: Int): ImageBitmap? =
        widgets.loadPreview(provider, maxPx)

    override fun createWidgetView(context: android.content.Context, appWidgetId: Int): android.view.View? =
        widgets.createView(context, appWidgetId)

    override fun widgetInfo(appWidgetId: Int): android.appwidget.AppWidgetProviderInfo? = widgets.info(appWidgetId)

    override fun addWidget(app: AppEntry, provider: WidgetProvider) {
        // Un ajout précédent resté en suspens (rare) : on libère son id.
        pendingWidget?.let { widgets.deleteId(it.id) }
        val id = try {
            widgets.allocateId()
        } catch (e: Exception) {
            toast("Impossible d'ajouter ce widget")
            return
        }
        val pending = PendingWidget(app, id)
        pendingWidget = pending
        if (widgets.bindIfAllowed(id, provider)) {
            configureOrFinish(pending)
            return
        }
        // Première fois : Android demande à l'utilisateur d'autoriser Aura.
        val intent = Intent(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.info.provider)
            .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, provider.info.profile)
            .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, WidgetRepository.bindOptions())
        try {
            widgetBindRequest.launch(intent)
        } catch (e: Exception) {
            cancelPendingWidget()
            toast("Android refuse l'ajout de widgets")
        }
    }

    /** Lance l'écran de réglage du widget s'il en a un, sinon termine l'ajout. */
    private fun configureOrFinish(pending: PendingWidget) {
        val info = widgets.info(pending.id)
        if (info == null) {
            cancelPendingWidget()
            toast("Impossible d'ajouter ce widget")
            return
        }
        if (info.configure == null) {
            finishWidget(pending)
            return
        }
        try {
            widgets.host.startAppWidgetConfigureActivityForResult(this, pending.id, 0, REQUEST_WIDGET_CONFIGURE, null)
        } catch (e: Exception) {
            // ActivityNotFoundException, SecurityException… : le widget ne peut pas être réglé.
            cancelPendingWidget()
            toast("Ce widget ne peut pas être configuré")
        }
    }

    @Deprecated("Seul moyen de recevoir le résultat de startAppWidgetConfigureActivityForResult")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_WIDGET_CONFIGURE) return
        val pending = pendingWidget ?: return
        if (resultCode == RESULT_OK) finishWidget(pending) else cancelPendingWidget()
    }

    private fun cancelPendingWidget() {
        pendingWidget?.let { widgets.deleteId(it.id) }
        pendingWidget = null
    }

    /** Enregistre le lien app → widget (en supprimant l'ancien) et rouvre la carte de l'app. */
    private fun finishWidget(pending: PendingWidget) {
        pendingWidget = null
        val old = state.widgets[pending.app.key]
        if (old != null && old != pending.id) widgets.deleteId(old)
        state.widgets = state.widgets + (pending.app.key to pending.id)
        prefs.widgetIds = state.widgets
        // Nouveau widget : il repart de sa hauteur calculée.
        setWidgetHeight(pending.app, null)
        if (state.shortcutPanel?.app?.key != pending.app.key) {
            lifecycleScope.launch {
                val shortcuts = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    shortcutRepo.shortcutsFor(pending.app)
                }
                state.shortcutPanel = ShortcutPanel(pending.app, shortcuts)
            }
        }
    }

    override fun removeWidget(app: AppEntry) {
        val id = state.widgets[app.key] ?: return
        widgets.deleteId(id)
        state.widgets = state.widgets - app.key
        prefs.widgetIds = state.widgets
        setWidgetHeight(app, null)
    }

    // --- Design ---

    override fun setWidgetHeight(app: AppEntry, heightDp: Int?) {
        val updated = if (heightDp == null) state.widgetHeights - app.key else state.widgetHeights + (app.key to heightDp)
        if (updated == state.widgetHeights) return
        state.widgetHeights = updated
        prefs.widgetHeights = updated
    }

    private companion object {
        const val REQUEST_WIDGET_CONFIGURE = 0x4157
    }
}

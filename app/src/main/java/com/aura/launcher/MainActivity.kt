package com.aura.launcher

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
import androidx.lifecycle.lifecycleScope
import com.aura.launcher.ui.AuraTheme
import com.aura.launcher.ui.LauncherScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), LauncherActions {

    private lateinit var repo: AppRepository
    private lateinit var prefs: Prefs
    private lateinit var media: MediaWatcher
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
        if (keepSettingsOpen) {
            keepSettingsOpen = false
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
}

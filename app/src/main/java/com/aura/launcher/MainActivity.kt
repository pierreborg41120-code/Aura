package com.aura.launcher

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.lifecycleScope
import com.aura.launcher.ui.AuraTheme
import com.aura.launcher.ui.LauncherScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), LauncherActions {

    private lateinit var repo: AppRepository
    private lateinit var prefs: Prefs
    private val state = LauncherState()
    private var stopObserving: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        repo = AppRepository(this)
        prefs = Prefs(this)
        state.favorites = prefs.favorites
        state.hidden = prefs.hidden
        state.showIcons = prefs.showIcons
        state.autoLaunch = prefs.autoLaunch

        refreshApps()
        stopObserving = repo.observeChanges { refreshApps() }

        setContent {
            AuraTheme {
                LauncherScreen(state = state, actions = this)
            }
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
        resetToHome()
    }

    private fun resetToHome() {
        state.drawerOpen = false
        state.settingsOpen = false
        state.query = ""
    }

    private fun refreshApps() {
        lifecycleScope.launch { state.apps = repo.loadApps() }
    }

    private fun tryStart(intent: Intent, errorMessage: String = "Aucune application ne gère cette action") {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
        }
    }

    // --- LauncherActions ---

    override fun launch(app: AppEntry) {
        try {
            getSystemService(LauncherApps::class.java)
                .startMainActivity(app.component, app.user, null, null)
        } catch (e: Exception) {
            Toast.makeText(this, "Impossible d'ouvrir ${app.label}", Toast.LENGTH_SHORT).show()
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

    override fun setShowIcons(show: Boolean) {
        state.showIcons = show
        prefs.showIcons = show
    }

    override fun setAutoLaunch(enabled: Boolean) {
        state.autoLaunch = enabled
        prefs.autoLaunch = enabled
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

    override fun openWallpaperPicker() =
        tryStart(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Fond d'écran"))

    override fun openDefaultLauncherSettings() = tryStart(Intent(Settings.ACTION_HOME_SETTINGS))

    override fun loadIcon(app: AppEntry, sizePx: Int): ImageBitmap? = repo.loadIcon(app, sizePx)
}

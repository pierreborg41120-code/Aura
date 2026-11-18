package com.aura.launcher

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.os.UserHandle
import android.os.UserManager
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/** Un raccourci d'app (ex. « Nouveau message », « Scanner un QR code »). */
data class AppShortcut(
    val id: String,
    val packageName: String,
    val label: String,
    val user: UserHandle,
    /** Objet système d'origine : nécessaire pour lancer le raccourci et charger son icône. */
    val info: ShortcutInfo,
) {
    /** Clé unique (le même identifiant peut exister dans le profil pro). */
    val key: String get() = "$packageName/$id#${user.hashCode()}"
}

/** Contenu du panneau de raccourcis ouvert depuis l'accueil. */
data class ShortcutPanel(val app: AppEntry, val shortcuts: List<AppShortcut>)

/**
 * Lit et lance les raccourcis des apps via LauncherApps.
 * Ne fonctionne que si Aura est l'écran d'accueil par défaut : sinon, aucun raccourci (jamais de plantage).
 */
class ShortcutRepository(private val context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val iconCache = LruCache<String, ImageBitmap>(120)

    /** Raccourcis de l'app, du plus important au moins important. À appeler hors du thread principal. */
    fun shortcutsFor(app: AppEntry, max: Int = 10): List<AppShortcut> = try {
        if (!launcherApps.hasShortcutHostPermission() || !userManager.isUserUnlocked(app.user)) {
            emptyList()
        } else {
            val query = LauncherApps.ShortcutQuery()
                .setPackage(app.packageName)
                .setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
                )
            val all = launcherApps.getShortcuts(query, app.user).orEmpty().filter { it.isEnabled }
            // Une app peut avoir plusieurs activités d'entrée : on garde les raccourcis de la bonne.
            val forActivity = all.filter { it.activity == null || it.activity == app.component }
            (forActivity.ifEmpty { all })
                .sortedWith(
                    compareBy<ShortcutInfo>(
                        // D'abord ceux du manifeste, puis les dynamiques, enfin les épinglés.
                        {
                            when {
                                it.isDeclaredInManifest -> 0
                                it.isDynamic -> 1
                                else -> 2
                            }
                        },
                        { it.rank },
                    ),
                )
                .distinctBy { it.id }
                .take(max)
                .mapNotNull { info ->
                    val label = chooseLabel(info) ?: return@mapNotNull null
                    AppShortcut(info.id, info.`package`, label, info.userHandle, info)
                }
        }
    } catch (e: SecurityException) {
        emptyList()
    } catch (e: IllegalStateException) {
        // Profil verrouillé ou utilisateur pas encore déverrouillé.
        emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    /** Libellé long s'il reste court, sinon libellé court. */
    private fun chooseLabel(info: ShortcutInfo): String? {
        val long = info.longLabel?.toString()?.trim().orEmpty()
        val short = info.shortLabel?.toString()?.trim().orEmpty()
        return when {
            long.isNotEmpty() && long.length <= 26 -> long
            short.isNotEmpty() -> short
            long.isNotEmpty() -> long
            else -> null
        }
    }

    /** Lance le raccourci. Renvoie faux si le système refuse. */
    fun start(shortcut: AppShortcut): Boolean = try {
        launcherApps.startShortcut(shortcut.info, null, null)
        true
    } catch (e: Exception) {
        // ActivityNotFoundException, SecurityException, IllegalStateException…
        false
    }

    /** Icône du raccourci (avec le badge du profil pro). À appeler hors du thread principal. */
    fun loadIcon(shortcut: AppShortcut, sizePx: Int): ImageBitmap? {
        val cacheKey = "${shortcut.key}@$sizePx"
        iconCache.get(cacheKey)?.let { return it }
        return try {
            val density = context.resources.displayMetrics.densityDpi
            val drawable = launcherApps.getShortcutIconDrawable(shortcut.info, density) ?: return null
            val badged = context.packageManager.getUserBadgedIcon(drawable, shortcut.user)
            val bitmap = badged.toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).asImageBitmap()
            iconCache.put(cacheKey, bitmap)
            bitmap
        } catch (e: Exception) {
            null
        }
    }
}

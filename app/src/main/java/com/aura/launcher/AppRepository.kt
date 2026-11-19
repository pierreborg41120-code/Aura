package com.aura.launcher

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Liste les apps installées et prévient quand elle change. */
class AppRepository(private val context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val iconCache = LruCache<String, ImageBitmap>(300)
    private val myPackage = context.packageName

    /** Apps installées, non triées (le tri se fait après application des renommages). */
    suspend fun loadApps(): List<AppEntry> = withContext(Dispatchers.IO) {
        val me = Process.myUserHandle()
        userManager.userProfiles.flatMap { user ->
            val serial = userManager.getSerialNumberForUser(user)
            launcherApps.getActivityList(null, user)
                .filter { it.applicationInfo.packageName != myPackage }
                .map { info ->
                    AppEntry(
                        key = "${info.componentName.flattenToString()}#$serial",
                        label = info.label.toString().trim(),
                        component = info.componentName,
                        user = user,
                        userSerial = serial,
                        isWorkProfile = user != me,
                    )
                }
        }
            .distinctBy { it.key }
    }

    /** --- Icônes --- packs d'icônes installés. */
    val iconPacks = IconPacks(context)

    /**
     * Charge (et met en cache) l'icône d'une app, dans le style [look] (origine, thème ou pack).
     * À appeler hors du thread principal.
     */
    fun loadIcon(app: AppEntry, sizePx: Int, look: IconLook = IconLook.ORIGINAL): ImageBitmap? {
        // La clé commence par la clé de l'app (« paquet/… ») : l'effacement par paquet continue de marcher.
        val cacheKey = "${app.key}@${look.token}@$sizePx"
        iconCache.get(cacheKey)?.let { return it }
        return try {
            val info = launcherApps.resolveActivity(
                android.content.Intent(android.content.Intent.ACTION_MAIN)
                    .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                    .setComponent(app.component),
                app.user,
            ) ?: return null
            val bitmap: Bitmap = when (look.style) {
                IconStyle.ORIGINAL -> info.getBadgedIcon(0).toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                IconStyle.THEME -> badged(renderThemedIcon(info.getIcon(0), sizePx, look), app, sizePx)
                IconStyle.PACK -> {
                    // App absente du pack : on garde son icône d'origine.
                    val fromPack = iconPacks.iconFor(look.pack, app.component)
                    if (fromPack == null) info.getBadgedIcon(0).toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                    else badged(fromPack.toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888), app, sizePx)
                }
            }
            val image = bitmap.asImageBitmap()
            iconCache.put(cacheKey, image)
            image
        } catch (e: Exception) {
            null
        }
    }

    /** Ajoute la petite mallette des apps du profil professionnel. */
    private fun badged(bitmap: Bitmap, app: AppEntry, sizePx: Int): Bitmap {
        if (!app.isWorkProfile) return bitmap
        val drawable = android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
        return context.packageManager.getUserBadgedIcon(drawable, app.user).toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    }

    /** Appelle [onChange] à chaque installation, mise à jour ou suppression d'app. */
    fun observeChanges(onChange: () -> Unit): () -> Unit {
        val callback = object : LauncherApps.Callback() {
            private fun changed(packageName: String?) {
                packageName?.let { iconPacks.forget(it) }
                packageName?.let { pkg -> iconCache.snapshot().keys.filter { it.startsWith("$pkg/") }.forEach(iconCache::remove) }
                onChange()
            }
            override fun onPackageRemoved(packageName: String?, user: UserHandle?) = changed(packageName)
            override fun onPackageAdded(packageName: String?, user: UserHandle?) = changed(packageName)
            override fun onPackageChanged(packageName: String?, user: UserHandle?) = changed(packageName)
            override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) {
                packageNames?.forEach { changed(it) } ?: onChange()
            }
            override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) {
                packageNames?.forEach { changed(it) } ?: onChange()
            }
        }
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        return { launcherApps.unregisterCallback(callback) }
    }
}

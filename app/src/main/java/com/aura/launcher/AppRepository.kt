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
import java.text.Collator

/** Liste les apps installées et prévient quand elle change. */
class AppRepository(private val context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val iconCache = LruCache<String, ImageBitmap>(300)
    private val myPackage = context.packageName

    suspend fun loadApps(): List<AppEntry> = withContext(Dispatchers.IO) {
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
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
                        isWorkProfile = user != me,
                    )
                }
        }
            .distinctBy { it.key }
            .sortedWith { a, b -> collator.compare(a.searchKey, b.searchKey) }
    }

    /** Charge (et met en cache) l'icône d'une app. À appeler hors du thread principal. */
    fun loadIcon(app: AppEntry, sizePx: Int): ImageBitmap? {
        iconCache.get(app.key)?.let { return it }
        return try {
            val info = launcherApps.resolveActivity(
                android.content.Intent(android.content.Intent.ACTION_MAIN)
                    .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                    .setComponent(app.component),
                app.user,
            ) ?: return null
            val drawable = info.getBadgedIcon(0)
            val bitmap = drawable.toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).asImageBitmap()
            iconCache.put(app.key, bitmap)
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    /** Appelle [onChange] à chaque installation, mise à jour ou suppression d'app. */
    fun observeChanges(onChange: () -> Unit): () -> Unit {
        val callback = object : LauncherApps.Callback() {
            private fun changed(packageName: String?) {
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

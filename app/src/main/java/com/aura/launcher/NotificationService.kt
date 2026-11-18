package com.aura.launcher

import android.app.Notification
import android.media.session.MediaSession
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Résumé des notifications d'une app. */
data class NotificationInfo(
    val count: Int,
    val title: String?,
    val text: String?,
    val postTime: Long,
) {
    /** Texte court à afficher sous le nom de l'app. */
    val preview: String?
        get() = listOfNotNull(title?.takeIf { it.isNotBlank() }, text?.takeIf { it.isNotBlank() })
            .joinToString(" · ")
            .replace('\n', ' ')
            .ifBlank { null }
}

/** Partage l'état des notifications entre le service et l'écran d'accueil. */
object NotificationStore {
    internal val mutableByApp = MutableStateFlow<Map<String, NotificationInfo>>(emptyMap())
    internal val mutableConnected = MutableStateFlow(false)
    internal val mutableMediaTokens = MutableStateFlow<List<MediaSession.Token>>(emptyList())

    /** Notifications par app (clé : [AppEntry.notificationKey]). */
    val byApp: StateFlow<Map<String, NotificationInfo>> = mutableByApp
    val connected: StateFlow<Boolean> = mutableConnected

    /** Sessions multimédia trouvées dans les notifications (secours si Android refuse la liste des sessions). */
    val mediaTokens: StateFlow<List<MediaSession.Token>> = mutableMediaTokens

    /** Relit les notifications actives (utile au retour sur l'accueil). */
    fun refreshNow() {
        NotificationService.instance?.refresh()
    }
}

/**
 * Service que l'utilisateur active dans les réglages d'Android ("Accès aux notifications").
 * Il ne fait que lire les notifications pour afficher un point et un aperçu sur l'accueil.
 * Rien ne quitte le téléphone.
 */
class NotificationService : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
        NotificationStore.mutableConnected.value = true
        refresh()
    }

    override fun onListenerDisconnected() {
        instance = null
        NotificationStore.mutableConnected.value = false
        NotificationStore.mutableByApp.value = emptyMap()
        NotificationStore.mutableMediaTokens.value = emptyList()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    internal fun refresh() {
        val notifications = try {
            activeNotifications ?: return
        } catch (e: Exception) {
            return
        }
        NotificationStore.mutableMediaTokens.value = notifications
            .sortedByDescending { it.postTime }
            .mapNotNull { mediaToken(it.notification.extras) }
        val userManager = getSystemService(UserManager::class.java)
        NotificationStore.mutableByApp.value = notifications
            .filter { sbn ->
                sbn.isClearable &&
                    !sbn.isOngoing &&
                    (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0
            }
            .groupBy { AppEntry.notificationKey(it.packageName, userManager.getSerialNumberForUser(it.user)) }
            .mapValues { (_, list) ->
                val latest = list.maxBy { it.postTime }
                val extras = latest.notification.extras
                NotificationInfo(
                    count = list.size,
                    title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
                    text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                        ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString(),
                    postTime = latest.postTime,
                )
            }
    }

    private fun mediaToken(extras: Bundle?): MediaSession.Token? {
        if (extras == null || !extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return null
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
            } else {
                @Suppress("DEPRECATION")
                extras.getParcelable(Notification.EXTRA_MEDIA_SESSION)
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        @Volatile
        internal var instance: NotificationService? = null
    }
}

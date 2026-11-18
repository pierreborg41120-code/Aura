package com.aura.launcher

import android.app.Notification
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

    /** Notifications par app (clé : [AppEntry.notificationKey]). */
    val byApp: StateFlow<Map<String, NotificationInfo>> = mutableByApp
    val connected: StateFlow<Boolean> = mutableConnected
}

/**
 * Service que l'utilisateur active dans les réglages d'Android ("Accès aux notifications").
 * Il ne fait que lire les notifications pour afficher un point et un aperçu sur l'accueil.
 * Rien ne quitte le téléphone.
 */
class NotificationService : NotificationListenerService() {

    override fun onListenerConnected() {
        NotificationStore.mutableConnected.value = true
        refresh()
    }

    override fun onListenerDisconnected() {
        NotificationStore.mutableConnected.value = false
        NotificationStore.mutableByApp.value = emptyMap()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        val notifications = try {
            activeNotifications ?: return
        } catch (e: Exception) {
            return
        }
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
}

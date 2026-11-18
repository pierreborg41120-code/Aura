package com.aura.launcher

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Résumé des notifications d'une app. */
data class NotificationInfo(
    val count: Int,
    val title: String?,
    val text: String?,
    val postTime: Long,
    /** Toutes les notifications de l'app, de la plus récente à la plus ancienne. */
    val items: List<NotificationItem> = emptyList(),
) {
    /** Texte court à afficher sous le nom de l'app. */
    val preview: String?
        get() = listOfNotNull(title?.takeIf { it.isNotBlank() }, text?.takeIf { it.isNotBlank() })
            .joinToString(" · ")
            .replace('\n', ' ')
            .ifBlank { null }
}

/** Un message d'une conversation (MessagingStyle). [sender] null = message envoyé par l'utilisateur. */
data class ChatMessage(val sender: String?, val text: String, val time: Long)

/** Action simple d'une notification (« Marquer comme lu »…). */
data class NotificationAction(val label: String, val intent: PendingIntent)

/** Action « Répondre » : l'intention à envoyer et les champs de saisie qu'elle attend. */
data class ReplyAction(val label: String, val intent: PendingIntent, val remoteInputs: List<RemoteInput>)

/** Une notification, avec ce qu'il faut pour l'ouvrir, y répondre ou l'effacer. */
data class NotificationItem(
    /** Clé de la StatusBarNotification (pour l'effacer). */
    val key: String,
    /** Titre : nom de la conversation ou de l'expéditeur. */
    val title: String?,
    val text: String?,
    val postTime: Long,
    /** Derniers messages (3 au plus, du plus ancien au plus récent) ; vide si ce n'est pas une conversation. */
    val messages: List<ChatMessage>,
    val contentIntent: PendingIntent?,
    val reply: ReplyAction?,
    /** Autres actions (2 au plus). */
    val actions: List<NotificationAction>,
    val autoCancel: Boolean,
)

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

    // --- Réponses aux notifications ---

    /** Efface une notification (comme un glissement dans le volet). Faux si le service n'est pas connecté. */
    fun dismiss(key: String): Boolean {
        val service = NotificationService.instance ?: return false
        return try {
            service.cancelNotification(key)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Ouvre la conversation (contentIntent). Faux si la notification n'en a pas ou si l'app l'a annulé :
     * l'appelant ouvre alors l'app elle-même.
     */
    fun open(context: Context, item: NotificationItem): Boolean {
        val intent = item.contentIntent ?: return false
        val ok = sendPending(context, intent, null)
        // Une notification « auto-cancel » disparaît quand on la touche dans le volet : pareil ici.
        if (ok && item.autoCancel) dismiss(item.key)
        return ok
    }

    /** Lance une action simple de la notification. */
    fun runAction(context: Context, action: NotificationAction): Boolean = sendPending(context, action.intent, null)

    /**
     * Envoie une réponse : le texte est placé dans les champs RemoteInput de l'action « Répondre »,
     * comme le fait le volet de notifications d'Android. Faux si l'app a annulé l'action entre-temps.
     */
    fun sendReply(context: Context, item: NotificationItem, text: String): Boolean {
        val reply = item.reply ?: return false
        val results = Bundle()
        reply.remoteInputs.forEach { input ->
            if (input.allowFreeFormInput) results.putCharSequence(input.resultKey, text)
        }
        val fillIn = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        RemoteInput.addResultsToIntent(reply.remoteInputs.toTypedArray(), fillIn, results)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            RemoteInput.setResultsSource(fillIn, RemoteInput.SOURCE_FREE_FORM_INPUT)
        }
        return sendPending(context, reply.intent, fillIn)
    }

    private fun sendPending(context: Context, pending: PendingIntent, fillIn: Intent?): Boolean = try {
        // Android 14+ : Aura est au premier plan et prête ce droit pour que l'app puisse s'ouvrir.
        val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                .toBundle()
        } else {
            null
        }
        pending.send(context, 0, fillIn, null, null, null, options)
        true
    } catch (e: PendingIntent.CanceledException) {
        false
    } catch (e: Exception) {
        false
    }
}

/**
 * Service que l'utilisateur active dans les réglages d'Android ("Accès aux notifications").
 * Il lit les notifications pour afficher un point et un aperçu sur l'accueil, et permet d'y répondre
 * ou de les effacer depuis la carte de l'app. Rien ne quitte le téléphone.
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
                    items = list.sortedByDescending { it.postTime }.map(::toItem),
                )
            }
    }

    // --- Réponses aux notifications ---

    /** Extrait d'une notification ce qu'il faut pour l'afficher et agir dessus. */
    private fun toItem(sbn: StatusBarNotification): NotificationItem {
        val n = sbn.notification
        val extras = n.extras
        val style = try {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        } catch (e: Exception) {
            null
        }
        val messages = style?.messages.orEmpty()
            .mapNotNull { m ->
                val text = m.text?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                // Expéditeur absent : message envoyé par l'utilisateur.
                val sender = m.person?.name?.toString()?.takeIf { it.isNotBlank() }
                ChatMessage(sender, text, m.timestamp)
            }
            .takeLast(3)
        val title = style?.conversationTitle?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()

        val allActions = n.actions.orEmpty().filter { it.actionIntent != null && it.title != null }
        val replyAction = allActions.firstOrNull { action ->
            action.remoteInputs.orEmpty().any { it.allowFreeFormInput }
        }
        val others = allActions
            .filter { it !== replyAction && it.remoteInputs.isNullOrEmpty() }
            .take(2)
            .map { NotificationAction(it.title.toString(), it.actionIntent) }

        return NotificationItem(
            key = sbn.key,
            title = title,
            text = text,
            postTime = sbn.postTime,
            messages = messages,
            contentIntent = n.contentIntent,
            reply = replyAction?.let { ReplyAction(it.title.toString(), it.actionIntent, it.remoteInputs.toList()) },
            actions = others,
            autoCancel = (n.flags and Notification.FLAG_AUTO_CANCEL) != 0,
        )
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

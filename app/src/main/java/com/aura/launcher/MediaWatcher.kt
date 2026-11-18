package com.aura.launcher

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper

/** Ce qui est en cours de lecture (musique, podcast…). */
data class MediaInfo(
    val title: String,
    val artist: String?,
    val isPlaying: Boolean,
    val packageName: String,
)

/**
 * Suit la session multimédia active pour afficher un mini-lecteur sur l'accueil.
 * Android n'y donne accès qu'aux apps qui ont l'accès aux notifications.
 */
class MediaWatcher(
    context: Context,
    private val onChange: (MediaInfo?) -> Unit,
) {
    private val sessionManager = context.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(context, NotificationService::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null
    private var started = false

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = pick(currentSessions())
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        pick(controllers)
    }

    fun start() {
        if (started) return
        try {
            sessionManager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, handler)
            started = true
            pick(currentSessions())
        } catch (e: SecurityException) {
            // Accès aux notifications pas encore accordé.
            onChange(null)
        }
    }

    fun stop() {
        if (started) {
            try {
                sessionManager.removeOnActiveSessionsChangedListener(sessionsListener)
            } catch (e: Exception) {
            }
        }
        started = false
        controller?.unregisterCallback(controllerCallback)
        controller = null
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) {
            c.transportControls.pause()
        } else {
            c.transportControls.play()
        }
    }

    fun next() {
        controller?.transportControls?.skipToNext()
    }

    fun previous() {
        controller?.transportControls?.skipToPrevious()
    }

    /** Ouvre l'app qui joue le son. Renvoie false si elle n'a pas fourni d'écran à ouvrir. */
    fun openPlayer(): Boolean = try {
        controller?.sessionActivity?.send()
        controller?.sessionActivity != null
    } catch (e: Exception) {
        false
    }

    val playerPackage: String? get() = controller?.packageName

    private fun currentSessions(): List<MediaController> = try {
        sessionManager.getActiveSessions(listenerComponent)
    } catch (e: SecurityException) {
        emptyList()
    }

    private fun pick(controllers: List<MediaController>?) {
        val list = controllers.orEmpty()
        val chosen = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull()
        if (chosen?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(controllerCallback)
            controller = chosen
            chosen?.registerCallback(controllerCallback, handler)
        }
        publish()
    }

    private fun publish() {
        val c = controller
        val metadata = c?.metadata
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        val state = c?.playbackState?.state
        val active = state == PlaybackState.STATE_PLAYING ||
            state == PlaybackState.STATE_PAUSED ||
            state == PlaybackState.STATE_BUFFERING
        if (c == null || title.isNullOrBlank() || !active) {
            onChange(null)
            return
        }
        onChange(
            MediaInfo(
                title = title,
                artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
                isPlaying = state == PlaybackState.STATE_PLAYING,
                packageName = c.packageName,
            ),
        )
    }
}

package com.aura.launcher

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.abs

/** Ce qui est en cours de lecture (musique, podcast…). */
data class MediaInfo(
    val title: String,
    val artist: String?,
    val isPlaying: Boolean,
    val packageName: String,
    // --- Lecteur ---
    /** Identifiant du morceau (app + titre + artiste + album) : change quand le morceau change. */
    val trackId: String = title,
    /** Sens du changement qui a amené ce morceau : 1 = suivant, -1 = précédent, 0 = externe/inconnu. */
    val direction: Int = 0,
    /** Pochette réduite (null si l'app n'en fournit pas). */
    val art: ImageBitmap? = null,
    /** Couleur dominante de la pochette (ARGB), null sans pochette. */
    val artColor: Int? = null,
    /** Durée totale en ms (0 si inconnue). */
    val durationMs: Long = 0L,
    /** Position en ms à l'instant [positionUpdatedAt] (négative si inconnue). */
    val positionMs: Long = -1L,
    /** Instant (SystemClock.elapsedRealtime) de la dernière position connue. */
    val positionUpdatedAt: Long = 0L,
    /** Vitesse de lecture (1 = normale). */
    val speed: Float = 1f,
    /** Vrai si l'app accepte qu'on se déplace dans le morceau. */
    val canSeek: Boolean = false,
)

/**
 * Suit la session multimédia active pour afficher un mini-lecteur sur l'accueil.
 * Android n'y donne accès qu'aux apps qui ont l'accès aux notifications.
 */
class MediaWatcher(
    private val context: Context,
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

    /** Vrai si Android a accepté de nous donner la liste des sessions multimédia. */
    var sessionsAllowed = false
        private set

    fun start() {
        if (started) return
        started = true
        sessionsAllowed = try {
            sessionManager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, handler)
            true
        } catch (e: Exception) {
            // Accès refusé : on se rabattra sur les sessions trouvées dans les notifications.
            false
        }
        pick(currentSessions())
    }

    /** À appeler quand les notifications changent (nouvelle session multimédia possible). */
    fun onNotificationsChanged() {
        if (started) pick(currentSessions())
    }

    fun stop() {
        if (sessionsAllowed) {
            try {
                sessionManager.removeOnActiveSessionsChangedListener(sessionsListener)
            } catch (e: Exception) {
            }
        }
        started = false
        sessionsAllowed = false
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
        // --- Lecteur --- on retient le sens du saut pour animer la pochette dans la bonne direction
        rememberSkip(1)
        controller?.transportControls?.skipToNext()
    }

    fun previous() {
        rememberSkip(-1)
        controller?.transportControls?.skipToPrevious()
    }

    // --- Lecteur ---
    fun seekTo(positionMs: Long) {
        controller?.transportControls?.seekTo(positionMs.coerceAtLeast(0L))
    }

    /** Ouvre l'app qui joue le son. Renvoie false si elle n'a pas fourni d'écran à ouvrir. */
    fun openPlayer(): Boolean = try {
        controller?.sessionActivity?.send()
        controller?.sessionActivity != null
    } catch (e: Exception) {
        false
    }

    val playerPackage: String? get() = controller?.packageName

    private fun currentSessions(): List<MediaController> {
        val fromSystem = try {
            sessionManager.getActiveSessions(listenerComponent)
        } catch (e: Exception) {
            emptyList()
        }
        if (fromSystem.isNotEmpty()) return fromSystem
        return NotificationStore.mediaTokens.value.mapNotNull { token ->
            try {
                MediaController(context, token)
            } catch (e: Exception) {
                null
            }
        }
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

    // --- Lecteur : sens des sauts et pochette ---

    private var pendingDirection = 0
    private var pendingAt = 0L
    private var lastTrackId: String? = null
    private var lastDirection = 0

    // Cache : on ne réduit et n'analyse chaque pochette qu'une seule fois.
    private var artSource: Bitmap? = null
    private var artCache: ImageBitmap? = null
    private var artColorCache: Int? = null

    private fun rememberSkip(direction: Int) {
        pendingDirection = direction
        pendingAt = SystemClock.elapsedRealtime()
    }

    /** Sens associé au morceau : celui d'un saut demandé depuis Aura il y a peu, sinon 0. */
    private fun directionFor(trackId: String): Int {
        if (trackId == lastTrackId) return lastDirection
        val recent = SystemClock.elapsedRealtime() - pendingAt < SKIP_WINDOW_MS
        val direction = if (lastTrackId != null && recent) pendingDirection else 0
        pendingDirection = 0
        lastTrackId = trackId
        lastDirection = direction
        return direction
    }

    private fun updateArt(source: Bitmap?) {
        if (source === artSource) return
        artSource = source
        artCache = null
        artColorCache = null
        if (source == null) return
        try {
            // Les bitmaps « matériels » ne se lisent pas pixel par pixel : copie logicielle.
            val soft = if (source.config == Bitmap.Config.HARDWARE) {
                source.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                source
            }
            val longest = maxOf(soft.width, soft.height).coerceAtLeast(1)
            val scale = ART_SIZE_PX.toFloat() / longest
            val small = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    soft,
                    (soft.width * scale).toInt().coerceAtLeast(1),
                    (soft.height * scale).toInt().coerceAtLeast(1),
                    true,
                )
            } else {
                soft
            }
            artCache = small.asImageBitmap()
            artColorCache = dominantColor(small)
        } catch (e: Exception) {
            artCache = null
            artColorCache = null
        }
    }

    private fun publish() {
        val c = controller
        val metadata = c?.metadata
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        val playback = c?.playbackState
        val state = playback?.state
        val active = state == PlaybackState.STATE_PLAYING ||
            state == PlaybackState.STATE_PAUSED ||
            state == PlaybackState.STATE_BUFFERING
        if (c == null || metadata == null || title.isNullOrBlank() || !active) {
            onChange(null)
            return
        }
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        // --- Lecteur ---
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)
        val trackId = "${c.packageName}|$title|${artist.orEmpty()}|${album.orEmpty()}"
        updateArt(
            metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
        )
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0L)
        val actions = playback?.actions ?: 0L
        onChange(
            MediaInfo(
                title = title,
                artist = artist,
                isPlaying = state == PlaybackState.STATE_PLAYING,
                packageName = c.packageName,
                trackId = trackId,
                direction = directionFor(trackId),
                art = artCache,
                artColor = artColorCache,
                durationMs = duration,
                positionMs = playback?.position ?: -1L,
                positionUpdatedAt = playback?.lastPositionUpdateTime ?: 0L,
                speed = playback?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
                canSeek = duration > 0L && (actions and PlaybackState.ACTION_SEEK_TO) != 0L,
            ),
        )
    }

    private companion object {
        const val SKIP_WINDOW_MS = 4000L
        const val ART_SIZE_PX = 256
    }
}

// --- Lecteur ---
/**
 * Couleur dominante d'une image, sans bibliothèque : moyenne des pixels d'une version 24×24,
 * pondérée pour favoriser les couleurs vives de luminosité moyenne (les fonds presque noirs,
 * blancs ou gris donneraient sinon une teinte terne).
 */
internal fun dominantColor(bitmap: Bitmap): Int? {
    val tiny = Bitmap.createScaledBitmap(bitmap, 24, 24, true)
    val hsv = FloatArray(3)
    var r = 0.0
    var g = 0.0
    var b = 0.0
    var total = 0.0
    for (y in 0 until tiny.height) {
        for (x in 0 until tiny.width) {
            val p = tiny.getPixel(x, y)
            if (Color.alpha(p) < 128) continue
            Color.colorToHSV(p, hsv)
            val saturation = hsv[1]
            val midLight = (1f - abs(hsv[2] - 0.6f) / 0.6f).coerceAtLeast(0f)
            val weight = 0.03 + saturation * saturation * midLight
            r += Color.red(p) * weight
            g += Color.green(p) * weight
            b += Color.blue(p) * weight
            total += weight
        }
    }
    if (total <= 0.0) return null
    return Color.rgb((r / total).toInt(), (g / total).toInt(), (b / total).toInt())
}

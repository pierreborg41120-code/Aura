package com.aura.launcher.ui

import android.os.SystemClock
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.launcher.LauncherActions
import com.aura.launcher.MediaInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

// --- Lecteur ---
// Mini-lecteur d'Aura : sans carte ni fond, posé directement sur le fond d'écran (pochette,
// titre, boutons, temps), avec des ombres douces pour rester lisible sur un fond clair. Au changement de morceau, la nouvelle
// pochette se révèle par-dessus l'ancienne à travers une « éclipse » liquide : un disque au bord
// légèrement ondulé qui grandit depuis le côté du saut. Le texte s'enchaîne en fondu avec un
// petit glissement, rien ne rebondit. Tout est dessiné à la main : aucune bibliothèque d'icônes.

/** Fond de la carte : assez sombre pour que le texte clair reste lisible sur un fond d'écran clair. */
private val CardBase = Color(0xFF0E0E14)

// Courbes « emphasized » : décélération franche à l'arrivée ; « standard » pour les fondus.
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
private val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

// Durées de la transition de morceau (sortie plus courte que l'entrée, arrivée en décélération).
/** Révélation de la nouvelle pochette par-dessus l'ancienne. */
private const val ArtRevealMs = 450
/** Teinte de la carte et du halo, lancée en même temps que la révélation de la pochette. */
private const val AccentMs = 600
/** Sortie de l'ancien titre. */
private const val TextOutMs = 120
/** Entrée du nouveau titre, et son décalage par rapport au début de la sortie. */
private const val TextInMs = 240
private const val TextInDelayMs = 60
/** Disque clair qui s'efface derrière les boutons précédent/suivant. */
private const val PressGlowMs = 300
/** Retour de la ligne à sa place après un glisser. */
private const val DragReturnMs = 200

/**
 * Temps pendant lequel on garde l'ancienne pochette quand le titre a changé mais que la nouvelle
 * image n'est pas encore arrivée (les métadonnées arrivent souvent en plusieurs fois).
 */
private const val ArtHoldMs = 1200L

/** Partie de [MediaInfo] affichée dans la colonne de texte (ne change qu'avec le morceau). */
private data class TrackText(
    val trackId: String,
    val title: String,
    val artist: String?,
    /** Sens du saut (1 = suivant, -1 = précédent, 0 = externe) : sens du glissement du texte. */
    val direction: Int,
)

/** Mini-lecteur affiché sur l'accueil quand de la musique est en cours. */
@Composable
fun MediaCard(media: MediaInfo, actions: LauncherActions, textScale: Float) {
    val context = LocalContext.current
    // Réglage système « Supprimer les animations » : transitions instantanées.
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }

    // Pochette et couleur « tenues » : si le morceau change avant que sa pochette n'arrive, on
    // garde l'ancienne (au plus [ArtHoldMs]) au lieu d'afficher la note puis l'image (double
    // animation, flash). La transition est donc pilotée par l'image elle-même, pas par le titre.
    var heldArt by remember { mutableStateOf(media.art) }
    var heldColor by remember { mutableStateOf(media.artColor) }
    // Sens du saut qui a amené l'image tenue : la révélation part de ce côté.
    var heldDirection by remember { mutableStateOf(media.direction) }
    LaunchedEffect(media.art, media.artColor) {
        if (media.art != null) {
            heldDirection = media.direction
            heldArt = media.art
            heldColor = media.artColor
        } else if (heldArt != null) {
            delay(ArtHoldMs)
            heldDirection = media.direction
            heldArt = null
            heldColor = null
        }
    }

    // Le halo change de teinte quand l'image change, donc en même temps que sa révélation.
    val targetAccent = heldColor?.let { tame(Color(it)) } ?: AuraColors.Accent
    val accent by animateColorAsState(
        targetValue = targetAccent,
        animationSpec = tween(if (reduceMotion) 0 else AccentMs, easing = Standard),
        label = "accent",
    )

    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Column {
            TrackInfo(
                media = media,
                art = heldArt,
                artDirection = heldDirection,
                accent = accent,
                textScale = textScale,
                reduceMotion = reduceMotion,
                actions = actions,
            )
            Spacer(Modifier.height(4.dp))
            Progress(media, accent, textScale, reduceMotion, actions)
        }
    }
}

/** Taille de la pochette : c'est elle qui donne sa hauteur au lecteur. */
private val ArtSize = 100.dp

/** Décalage visuel maximal de la ligne pendant un glisser (forte résistance). */
private val DragMax = 24.dp

/** Distance parcourue par le doigt au-delà de laquelle le glisser change de morceau. */
private val DragThreshold = 56.dp

/**
 * Grande pochette à gauche ; à droite, titre et artiste en haut, boutons en bas, alignés sur la
 * hauteur de la pochette. Glisser la ligne change de morceau, toucher pochette ou titre ouvre l'app.
 */
@Composable
private fun TrackInfo(
    media: MediaInfo,
    art: ImageBitmap?,
    artDirection: Int,
    accent: Color,
    textScale: Float,
    reduceMotion: Boolean,
    actions: LauncherActions,
) {
    val text = TrackText(media.trackId, media.title, media.artist, media.direction)
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val maxPx = with(density) { DragMax.toPx() }
    val threshold = with(density) { DragThreshold.toPx() }
    val textShift = with(density) { 8.dp.roundToPx() }
    val currentActions by rememberUpdatedState(actions)
    // Décalage visuel (amorti) de la ligne pendant le geste ; [raw] = distance réelle du doigt.
    val drag = remember { Animatable(0f) }
    var raw by remember { mutableStateOf(0f) }
    var crossed by remember { mutableStateOf(false) }

    fun settle() {
        scope.launch { drag.animateTo(0f, tween(if (reduceMotion) 0 else DragReturnMs, easing = Standard)) }
    }

    val openApp = Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClickLabel = "Ouvrir l'app de musique",
    ) { actions.mediaOpen() }

    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            // Le geste est lu AVANT le décalage visuel : le déplacement de la ligne ne fausse pas
            // la distance mesurée sous le doigt.
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        raw = 0f
                        crossed = false
                        scope.launch { drag.stop() }
                    },
                    onDragEnd = {
                        if (abs(raw) > threshold) {
                            view.auraHaptic(HapticFeedbackConstants.VIRTUAL_KEY)
                            if (raw < 0) currentActions.mediaNext() else currentActions.mediaPrevious()
                        }
                        settle()
                    },
                    onDragCancel = { settle() },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        raw += amount
                        // Élastique : la ligne suit le doigt de moins en moins, sans dépasser [DragMax].
                        val shown = maxPx * raw / (abs(raw) + maxPx)
                        scope.launch { drag.snapTo(shown) }
                        val beyond = abs(raw) > threshold
                        if (beyond != crossed) {
                            crossed = beyond
                            // Petit « tic » quand on franchit le seuil.
                            if (beyond) view.auraHaptic(HapticFeedbackConstants.CLOCK_TICK)
                        }
                    },
                )
            }
            .graphicsLayer { translationX = drag.value },
    ) {
        Artwork(art, artDirection, accent, reduceMotion, openApp)
        Spacer(Modifier.width(16.dp))
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = ArtSize),
        ) {
            AnimatedContent(
                targetState = text,
                contentKey = { it.trackId },
                transitionSpec = { textTransform(targetState.direction, reduceMotion, textShift) },
                contentAlignment = Alignment.TopStart,
                label = "titre",
                modifier = Modifier
                    .fillMaxWidth()
                    // Le texte reste dans sa colonne, quoi qu'il arrive.
                    .clipToBounds()
                    .then(openApp),
            ) { track ->
                Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(
                        text = track.title,
                        color = AuraColors.Text,
                        fontSize = (17 * textScale).sp,
                        fontWeight = FontWeight.SemiBold,
                        style = TextStyle().onWallpaper(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!track.artist.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = track.artist,
                            color = AuraColors.TextDim,
                            fontSize = (14 * textScale).sp,
                            style = TextStyle().onWallpaper(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            // Décalage négatif : les icônes (et non leurs zones de toucher) s'alignent sur le texte.
            Box(Modifier.offset(x = (-12).dp)) {
                Controls(media.isPlaying, actions, reduceMotion)
            }
        }
    }
}

/**
 * Titre et artiste : fondu enchaîné. L'ancien texte s'efface vite en glissant de 8 dp vers
 * l'arrière, le nouveau apparaît un instant plus tard en arrivant de 8 dp depuis le sens du saut
 * (suivant → de la droite). Changement externe : simple fondu avec 4 dp de remontée.
 * Pas d'animation de taille : la colonne prend directement la hauteur du nouveau texte.
 */
private fun textTransform(direction: Int, reduceMotion: Boolean, shiftPx: Int): ContentTransform {
    if (reduceMotion) return ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)
    val fadeInSpec = fadeIn(tween(TextInMs, TextInDelayMs, Standard))
    val exitFade = fadeOut(tween(TextOutMs, easing = LinearEasing))
    if (direction == 0) {
        val enter = fadeInSpec + slideInVertically(tween(TextInMs, TextInDelayMs, EmphasizedDecelerate)) { shiftPx / 2 }
        return ContentTransform(enter, exitFade, sizeTransform = null)
    }
    val enter = fadeInSpec +
        slideInHorizontally(tween(TextInMs, TextInDelayMs, EmphasizedDecelerate)) { direction * shiftPx }
    val exit = exitFade +
        slideOutHorizontally(tween(TextOutMs, easing = EmphasizedAccelerate)) { -direction * shiftPx }
    return ContentTransform(enter, exit, sizeTransform = null)
}

/**
 * Pochette carrée aux coins doux, dans un cadre fixe. Quand l'image change : sur Android 13+,
 * transition « liquide » par shader (voir LiquidTransition.kt) ; sinon, ou si l'une des deux
 * images manque, la nouvelle se révèle par-dessus l'ancienne à travers un disque au bord ondulé qui grandit
 * depuis le côté du saut : suivant → depuis la droite, précédent → depuis la gauche, changement
 * externe → depuis le centre. Une note dessinée remplace l'image si l'app n'en fournit pas.
 */
@Composable
private fun Artwork(art: ImageBitmap?, direction: Int, accent: Color, reduceMotion: Boolean, modifier: Modifier) {
    val placeholder = lerp(CardBase, accent, 0.45f)
    val currentDirection by rememberUpdatedState(direction)
    // Image du dessous (immobile) et image qui se révèle par-dessus.
    var base by remember { mutableStateOf(art) }
    var incoming by remember { mutableStateOf<ImageBitmap?>(null) }
    var revealing by remember { mutableStateOf(false) }
    var revealFrom by remember { mutableStateOf(0) }
    val progress = remember { Animatable(1f) }
    val mask = remember { Path() }
    val rim = with(LocalDensity.current) { 1.5.dp.toPx() }

    LaunchedEffect(art) {
        // Révélation interrompue par une nouvelle image : celle qui arrivait devient le fond.
        if (revealing) {
            base = incoming
            revealing = false
            incoming = null
        }
        if (art == base) return@LaunchedEffect
        if (reduceMotion) {
            base = art
            return@LaunchedEffect
        }
        incoming = art
        revealFrom = currentDirection
        progress.snapTo(0f)
        revealing = true
        // Progression linéaire : la courbe est appliquée dans le dessin (rayon du disque ou shader).
        val liquid = liquidTransitionSupported && base != null && art != null
        progress.animateTo(1f, tween(if (liquid) LiquidTransitionMs else ArtRevealMs, easing = LinearEasing))
        base = art
        revealing = false
        incoming = null
    }

    Box(
        modifier = modifier
            .size(ArtSize)
            // Légère ombre portée : la pochette se détache de la carte.
            .shadow(10.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black, spotColor = Color.Black)
            // Découpe aux coins arrondis : le disque de révélation ne déborde jamais.
            .clip(RoundedCornerShape(16.dp))
            .background(placeholder),
    ) {
        ArtLayer(base, placeholder, Modifier)
        val from = base
        val to = incoming
        if (revealing && liquidTransitionSupported && from != null && to != null) {
            // Android 13+ : l'ancienne pochette fond comme un liquide et la nouvelle prend sa place.
            LiquidArtTransition(from, to, revealFrom, { progress.value })
        } else if (revealing) {
            ArtLayer(
                incoming,
                placeholder,
                Modifier.drawWithContent {
                    val t = progress.value
                    val eased = FastOutSlowInEasing.transform(t)
                    val w = size.width
                    val h = size.height
                    val origin = when {
                        revealFrom > 0 -> Offset(w, h * 0.5f)
                        revealFrom < 0 -> Offset(0f, h * 0.5f)
                        else -> Offset(w * 0.5f, h * 0.5f)
                    }
                    // Rayon final : coin le plus éloigné × 1,2, pour que les creux du bord couvrent aussi.
                    val far = max(
                        max(hypot(origin.x, origin.y), hypot(w - origin.x, origin.y)),
                        max(hypot(origin.x, h - origin.y), hypot(w - origin.x, h - origin.y)),
                    )
                    val radius = far * 1.2f * eased
                    buildLiquidDisc(mask, origin, radius, t)
                    clipPath(mask) { this@drawWithContent.drawContent() }
                    // Liseré lumineux, couleur du halo, qui s'efface en fin de révélation.
                    val glow = (1f - eased).coerceIn(0f, 1f)
                    if (glow > 0.01f && radius > 0f) {
                        drawPath(
                            mask,
                            lerp(accent, Color.White, 0.5f).copy(alpha = 0.6f * glow),
                            style = Stroke(width = rim),
                        )
                    }
                },
            )
        }
    }
}

/**
 * Disque « liquide » : un cercle dont le rayon est perturbé par trois sinusoïdes de fréquences
 * et de phases différentes ; les phases avancent avec [t], ce qui fait onduler le bord.
 * L'amplitude reste faible (≈ 5 % du rayon) : on lit d'abord un cercle, puis un bord vivant.
 */
private fun buildLiquidDisc(path: Path, center: Offset, radius: Float, t: Float) {
    path.reset()
    if (radius <= 0f) return
    val steps = 72
    val amp = radius * 0.05f
    val turn = (2 * PI).toFloat()
    for (i in 0..steps) {
        val a = i * turn / steps
        val wobble = 0.55f * sin(3 * a + 0.7f + t * turn * 0.9f) +
            0.30f * sin(5 * a + 2.1f - t * turn * 1.3f) +
            0.15f * sin(8 * a + 4.0f + t * turn * 1.7f)
        val r = radius + amp * wobble
        val x = center.x + r * cos(a)
        val y = center.y + r * sin(a)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
}

/** Un calque de la pochette : l'image, ou la note sur fond opaque (pour bien couvrir l'ancienne). */
@Composable
private fun ArtLayer(image: ImageBitmap?, placeholder: Color, modifier: Modifier) {
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize(),
        )
    } else {
        Box(modifier.fillMaxSize().background(placeholder), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(34.dp)) { drawNote(Color.White.copy(alpha = 0.85f)) }
        }
    }
}


/** Précédent, lecture/pause (pastille claire), suivant. */
@Composable
private fun Controls(isPlaying: Boolean, actions: LauncherActions, reduceMotion: Boolean) {
    val morph by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(if (reduceMotion) 0 else 340, easing = Standard),
        label = "lecturePause",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        RoundButton("Morceau précédent", 44.dp, null, actions::mediaPrevious, glow = !reduceMotion) {
            Canvas(Modifier.size(15.dp)) { drawPrevious(AuraColors.Text) }
        }
        RoundButton(if (isPlaying) "Pause" else "Lecture", 48.dp, null, actions::mediaPlayPause, glow = !reduceMotion, ring = true) {
            Canvas(Modifier.size(17.dp)) { drawPlayPause(morph, AuraColors.Text) }
        }
        RoundButton("Morceau suivant", 44.dp, null, actions::mediaNext, glow = !reduceMotion) {
            Canvas(Modifier.size(15.dp)) { drawNext(AuraColors.Text) }
        }
    }
}

@Composable
private fun RoundButton(
    label: String,
    size: androidx.compose.ui.unit.Dp,
    fill: Color?,
    onClick: () -> Unit,
    glow: Boolean = false,
    ring: Boolean = false,
    content: @Composable () -> Unit,
) {
    // Disque clair qui apparaît derrière l'icône au toucher puis s'efface (remplace l'ondulation).
    val pressGlow = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val click = if (glow) {
        Modifier
            .drawBehind {
                val g = pressGlow.value
                if (g > 0f) {
                    // Le disque s'élargit très légèrement en s'effaçant.
                    drawCircle(
                        Color.White.copy(alpha = 0.18f * g),
                        radius = this.size.minDimension / 2f * (0.9f + 0.1f * (1f - g)),
                    )
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
            ) {
                scope.launch {
                    pressGlow.snapTo(1f)
                    pressGlow.animateTo(0f, tween(PressGlowMs, easing = Standard))
                }
                onClick()
            }
    } else {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .then(if (fill != null) Modifier.background(fill) else Modifier)
            .then(
                if (ring) {
                    // Anneau fin, avec un contour sombre très léger pour rester visible sur un fond clair.
                    Modifier.drawBehind {
                        val stroke = 1.5.dp.toPx()
                        val r = this.size.minDimension / 2f - stroke
                        drawCircle(Color.Black.copy(alpha = 0.18f), radius = r, style = Stroke(width = stroke * 2.5f))
                        drawCircle(AuraColors.Text, radius = r, style = Stroke(width = stroke))
                    }
                } else {
                    Modifier
                },
            )
            .then(click)
            .semantics { contentDescription = label },
    ) { content() }
}

/** Barre de progression fine ; glisser ou toucher pour se déplacer si l'app le permet. */
@Composable
private fun Progress(
    media: MediaInfo,
    accent: Color,
    textScale: Float,
    reduceMotion: Boolean,
    actions: LauncherActions,
) {
    val duration = media.durationMs
    if (duration <= 0L || media.positionMs < 0L) return

    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(media.isPlaying, media.positionUpdatedAt) {
        now = SystemClock.elapsedRealtime()
        while (media.isPlaying) {
            delay(200)
            now = SystemClock.elapsedRealtime()
        }
    }
    val livePosition = if (media.isPlaying) {
        media.positionMs + ((now - media.positionUpdatedAt) * media.speed).toLong()
    } else {
        media.positionMs
    }.coerceIn(0L, duration)

    // Pendant le glisser : position sous le doigt. Après : on la garde jusqu'à la réponse de l'app.
    var scrub by remember { mutableStateOf<Float?>(null) }
    var pendingSeek by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(media.positionUpdatedAt) { pendingSeek = null }

    val fraction = scrub ?: pendingSeek ?: (livePosition.toFloat() / duration)
    val shown = (fraction * duration).toLong()
    val thickness by animateDpAsState(
        targetValue = if (scrub != null) 6.dp else 3.dp,
        animationSpec = tween(if (reduceMotion) 0 else 200, easing = Standard),
        label = "épaisseur",
    )
    val fill = lerp(accent, Color.White, 0.45f)
    val currentActions by rememberUpdatedState(actions)
    val currentDuration by rememberUpdatedState(duration)
    val view = LocalView.current

    fun commit(f: Float) {
        pendingSeek = f
        currentActions.mediaSeek((f * currentDuration).toLong())
    }

    Column(Modifier.padding(start = 2.dp, end = 6.dp, top = 6.dp)) {
        var barModifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
        if (media.canSeek) {
            barModifier = barModifier
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        view.auraHaptic(HapticFeedbackConstants.CLOCK_TICK)
                        commit((offset.x / size.width).coerceIn(0f, 1f))
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> scrub = (offset.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = {
                            scrub?.let { commit(it) }
                            scrub = null
                        },
                        onDragCancel = { scrub = null },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            scrub = (change.position.x / size.width).coerceIn(0f, 1f)
                        },
                    )
                }
        }
        Canvas(barModifier) {
            val h = thickness.toPx()
            val top = (size.height - h) / 2f
            val radius = CornerRadius(h / 2f)
            drawRoundRect(Color.White.copy(alpha = 0.28f), Offset(0f, top), Size(size.width, h), radius)
            val w = (size.width * fraction.coerceIn(0f, 1f)).coerceAtLeast(h)
            drawRoundRect(fill, Offset(0f, top), Size(w, h), radius)
            if (scrub != null) {
                drawCircle(Color.White, radius = 7.dp.toPx(), center = Offset(w, size.height / 2f))
            }
        }
        Row {
            TimeText(formatTime(shown), textScale)
            Spacer(Modifier.weight(1f))
            TimeText(formatTime(duration), textScale)
        }
    }
}

@Composable
private fun TimeText(text: String, textScale: Float) {
    Text(
        text = text,
        color = AuraColors.TextDim,
        fontSize = (11 * textScale).sp,
        // Chiffres à largeur fixe : le temps ne « danse » pas en défilant.
        style = TextStyle(fontFeatureSettings = "tnum").onWallpaper(),
        maxLines = 1,
    )
}

private fun formatTime(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/**
 * Rend la couleur de la pochette utilisable pour un halo : ni trop sombre (invisible sur fond
 * noir), ni trop saturée (criarde), ni trop claire (le texte blanc doit rester lisible).
 */
private fun tame(color: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    hsv[1] = hsv[1].coerceAtMost(0.72f)
    hsv[2] = hsv[2].coerceIn(0.55f, 0.9f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

// Petites icônes dessinées à la main (pas besoin de bibliothèque d'icônes).

private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t

/**
 * Lecture ↔ pause : le triangle est coupé en deux quadrilatères qui se transforment chacun
 * en une barre de pause ([t] = 0 → lecture, 1 → pause).
 */
private fun DrawScope.drawPlayPause(t: Float, color: Color) {
    val w = size.width
    val h = size.height
    fun quad(play: FloatArray, pause: FloatArray) {
        val path = Path()
        for (i in 0 until 4) {
            val x = lerpF(play[i * 2], pause[i * 2], t) * w
            val y = lerpF(play[i * 2 + 1], pause[i * 2 + 1], t) * h
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, color)
    }
    // Triangle de lecture de x = 0.14 à 0.94 ; coupure verticale au milieu (x = 0.54).
    quad(
        floatArrayOf(0.14f, 0f, 0.54f, 0.25f, 0.54f, 0.75f, 0.14f, 1f),
        floatArrayOf(0.14f, 0.02f, 0.42f, 0.02f, 0.42f, 0.98f, 0.14f, 0.98f),
    )
    quad(
        floatArrayOf(0.54f, 0.25f, 0.94f, 0.5f, 0.94f, 0.5f, 0.54f, 0.75f),
        floatArrayOf(0.58f, 0.02f, 0.86f, 0.02f, 0.86f, 0.98f, 0.58f, 0.98f),
    )
}

private fun DrawScope.triangle(left: Float, right: Float, pointsRight: Boolean, color: Color) {
    val h = size.height
    val path = Path().apply {
        if (pointsRight) {
            moveTo(left, 0f); lineTo(right, h / 2); lineTo(left, h)
        } else {
            moveTo(right, 0f); lineTo(left, h / 2); lineTo(right, h)
        }
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.bar(left: Float, width: Float, color: Color) {
    drawRoundRect(color, Offset(left, 0f), Size(width, size.height), CornerRadius(width / 2))
}

private fun DrawScope.drawNext(color: Color) {
    triangle(0f, size.width * 0.78f, true, color)
    bar(size.width * 0.82f, size.width * 0.16f, color)
}

private fun DrawScope.drawPrevious(color: Color) {
    bar(size.width * 0.02f, size.width * 0.16f, color)
    triangle(size.width * 0.22f, size.width, false, color)
}

/** Double croche simplifiée : deux têtes, deux hampes, une barre. */
private fun DrawScope.drawNote(color: Color) {
    val w = size.width
    val h = size.height
    val head = w * 0.17f
    val stem = w * 0.08f
    drawCircle(color, head, Offset(w * 0.24f, h * 0.80f))
    drawCircle(color, head, Offset(w * 0.76f, h * 0.70f))
    drawRect(color, Offset(w * 0.24f + head - stem, h * 0.18f), Size(stem, h * 0.62f))
    drawRect(color, Offset(w * 0.76f + head - stem, h * 0.08f), Size(stem, h * 0.62f))
    val beam = Path().apply {
        moveTo(w * 0.24f + head - stem, h * 0.18f)
        lineTo(w * 0.76f + head, h * 0.08f)
        lineTo(w * 0.76f + head, h * 0.22f)
        lineTo(w * 0.24f + head - stem, h * 0.32f)
        close()
    }
    drawPath(beam, color)
}

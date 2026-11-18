package com.aura.launcher.ui

import android.os.SystemClock
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import kotlin.math.abs

// --- Lecteur ---
// Mini-lecteur d'Aura : une carte sombre translucide, teintée par la pochette, avec un halo
// coloré qui déborde doucement sur le fond d'écran. Quand le morceau change, la pochette glisse
// dans le sens du saut (suivant → vers la gauche) avec un léger rebond, et le texte suit avec
// un petit décalage. Tout est dessiné à la main : aucune bibliothèque d'icônes.

/** Fond de la carte : assez sombre pour que le texte clair reste lisible sur un fond d'écran clair. */
private val CardBase = Color(0xFF0E0E14)

// Courbes « emphasized » : décélération franche à l'arrivée, accélération au départ.
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
private val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** Partie de [MediaInfo] qui change seulement avec le morceau (pour ne pas réanimer à chaque seconde). */
private data class TrackVisual(
    val trackId: String,
    val title: String,
    val artist: String?,
    val art: ImageBitmap?,
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

    val targetAccent = media.artColor?.let { tame(Color(it)) } ?: AuraColors.Accent
    val accent by animateColorAsState(
        targetValue = targetAccent,
        animationSpec = tween(if (reduceMotion) 0 else 700, easing = Standard),
        label = "accent",
    )
    val shape = RoundedCornerShape(28.dp)

    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            // Halo : un ovale de couleur qui s'estompe autour de la carte.
            .drawBehind {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.height * 0.95f
                scale(scaleX = size.width / size.height * 0.62f, scaleY = 1f, pivot = center) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(accent.copy(alpha = 0.34f), accent.copy(alpha = 0.10f), Color.Transparent),
                            center = center,
                            radius = radius,
                        ),
                        radius = radius,
                        center = center,
                    )
                }
            }
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        lerp(CardBase, accent, 0.30f).copy(alpha = 0.82f),
                        CardBase.copy(alpha = 0.72f),
                    ),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.07f), shape)
            .padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Column {
            TrackInfo(
                media = media,
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

/**
 * Grande pochette à gauche ; à droite, titre et artiste en haut, boutons en bas, alignés sur la
 * hauteur de la pochette. Glisser la pochette ou le titre change de morceau, toucher ouvre l'app.
 */
@Composable
private fun TrackInfo(
    media: MediaInfo,
    accent: Color,
    textScale: Float,
    reduceMotion: Boolean,
    actions: LauncherActions,
) {
    val visual = TrackVisual(media.trackId, media.title, media.artist, media.art, media.direction)
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    // Décalage horizontal visuel (déjà amorti) de la pochette et du texte pendant le geste.
    val drag = remember { Animatable(0f) }
    val threshold = with(LocalDensity.current) { 40.dp.toPx() }
    var rowWidth by remember { mutableFloatStateOf(1f) }
    val currentActions by rememberUpdatedState(actions)
    val currentTrack by rememberUpdatedState(media.trackId)
    var crossed by remember { mutableStateOf(false) }
    // Vrai quand la pochette vient d'être « lancée » hors de la carte par un glisser : l'ancien
    // contenu est déjà parti, il disparaît donc sans seconde animation de sortie.
    var flung by remember { mutableStateOf(false) }

    // Le nouveau morceau est arrivé : on remet le calque en place, la transition d'entrée prend le relais.
    LaunchedEffect(media.trackId) {
        if (flung) {
            drag.snapTo(0f)
            flung = false
        }
    }

    val dragLayer = Modifier.graphicsLayer {
        translationX = drag.value
        alpha = 1f - (abs(drag.value) / (rowWidth * 0.7f)).coerceIn(0f, 1f)
    }
    val gestures = Modifier
        .pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { crossed = false },
                onDragEnd = {
                    val d = drag.value
                    if (abs(d) > threshold) {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        val from = currentTrack
                        flung = true
                        if (d < 0) currentActions.mediaNext() else currentActions.mediaPrevious()
                        scope.launch {
                            // La pochette poursuit sa course dans le sens du geste et sort de la carte.
                            val out = if (d < 0) -rowWidth else rowWidth
                            drag.animateTo(out, tween(if (reduceMotion) 0 else 240, easing = EmphasizedAccelerate))
                            // Si l'app n'a pas changé de morceau, la pochette revient.
                            delay(1500)
                            if (currentTrack == from) {
                                flung = false
                                drag.snapTo(if (d < 0) -rowWidth * 0.25f else rowWidth * 0.25f)
                                drag.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
                            }
                        }
                    } else {
                        scope.launch {
                            drag.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
                        }
                    }
                },
                onDragCancel = { scope.launch { drag.animateTo(0f) } },
                onHorizontalDrag = { change, amount ->
                    change.consume()
                    // Un peu de résistance : la pochette suit le doigt à 60 %.
                    val next = drag.value + amount * 0.6f
                    scope.launch { drag.snapTo(next) }
                    val beyond = abs(next) > threshold
                    if (beyond != crossed) {
                        crossed = beyond
                        // Petit « tic » quand on franchit le seuil.
                        if (beyond) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                },
            )
        }
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClickLabel = "Ouvrir l'app de musique",
        ) { actions.mediaOpen() }

    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { rowWidth = it.width.toFloat().coerceAtLeast(1f) },
    ) {
        AnimatedContent(
            targetState = visual,
            contentKey = { it.trackId },
            transitionSpec = { trackTransform(targetState.direction, reduceMotion, isArt = true, flung) },
            label = "pochette",
            modifier = dragLayer.then(gestures),
        ) { track ->
            Artwork(track.art, accent, reduceMotion)
        }
        Spacer(Modifier.width(16.dp))
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = ArtSize),
        ) {
            AnimatedContent(
                targetState = visual,
                contentKey = { it.trackId },
                transitionSpec = { trackTransform(targetState.direction, reduceMotion, isArt = false, flung) },
                contentAlignment = Alignment.TopStart,
                label = "titre",
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        // Le texte accompagne la pochette, en plus discret.
                        translationX = drag.value * 0.35f
                        alpha = 1f - (abs(drag.value) / (rowWidth * 0.5f)).coerceIn(0f, 1f)
                    }
                    .then(gestures),
            ) { track ->
                Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(
                        text = track.title,
                        color = AuraColors.Text,
                        fontSize = (17 * textScale).sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!track.artist.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = track.artist,
                            color = AuraColors.TextDim,
                            fontSize = (14 * textScale).sp,
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
 * Transition entre deux morceaux.
 * - Saut demandé depuis Aura : glissement dans le sens du saut + fondu croisé ; la pochette
 *   arrive avec un léger rebond d'échelle, le texte suit 70 ms plus tard sur une distance plus courte.
 *   Le décalage est proportionnel à la largeur du contenu, donc identique quelle que soit sa taille.
 * - Après un glisser ([flung]) : l'ancien contenu est déjà sorti, il disparaît instantanément.
 * - Changement venu d'ailleurs : simple fondu avec un soupçon d'échelle.
 */
private fun trackTransform(direction: Int, reduceMotion: Boolean, isArt: Boolean, flung: Boolean): ContentTransform {
    if (reduceMotion) return EnterTransition.None togetherWith ExitTransition.None
    val delay = if (isArt) 0 else 70
    if (direction == 0) {
        val enter = fadeIn(tween(380, delay, Standard)) +
            scaleIn(tween(420, delay, EmphasizedDecelerate), initialScale = if (isArt) 0.94f else 1f)
        return ContentTransform(enter, fadeOut(tween(180, easing = EmphasizedAccelerate)), sizeTransform = SizeTransform(clip = false))
    }
    val share = if (isArt) 0.6f else 0.3f
    var enter = slideInHorizontally(tween(440, delay, EmphasizedDecelerate)) { w -> (direction * w * share).toInt() } +
        fadeIn(tween(320, delay + 40, Standard))
    if (isArt) enter += scaleIn(spring(dampingRatio = 0.6f, stiffness = 380f), initialScale = 0.86f)
    if (flung) {
        return ContentTransform(enter, fadeOut(tween(0)), sizeTransform = SizeTransform(clip = false))
    }
    var exit = slideOutHorizontally(tween(220, 0, EmphasizedAccelerate)) { w -> (-direction * w * share).toInt() } +
        fadeOut(tween(200, easing = EmphasizedAccelerate))
    if (isArt) exit += scaleOut(tween(220, easing = EmphasizedAccelerate), targetScale = 0.9f)
    return ContentTransform(enter, exit, sizeTransform = SizeTransform(clip = false))
}

/** Pochette carrée aux coins doux ; une note dessinée la remplace si l'app n'en fournit pas. */
@Composable
private fun Artwork(art: ImageBitmap?, accent: Color, reduceMotion: Boolean) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(ArtSize)
            // Légère ombre portée : la pochette se détache de la carte.
            .shadow(10.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black, spotColor = Color.Black)
            .clip(RoundedCornerShape(16.dp))
            .background(lerp(CardBase, accent, 0.45f)),
    ) {
        // La pochette arrive parfois une fraction de seconde après le titre : fondu dans ce cas.
        Crossfade(art, animationSpec = tween(if (reduceMotion) 0 else 300), label = "image") { image ->
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(34.dp)) { drawNote(Color.White.copy(alpha = 0.85f)) }
                }
            }
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
        RoundButton("Morceau précédent", 44.dp, null, actions::mediaPrevious) {
            Canvas(Modifier.size(15.dp)) { drawPrevious(AuraColors.Text) }
        }
        RoundButton(if (isPlaying) "Pause" else "Lecture", 48.dp, AuraColors.Text, actions::mediaPlayPause) {
            Canvas(Modifier.size(17.dp)) { drawPlayPause(morph, CardBase) }
        }
        RoundButton("Morceau suivant", 44.dp, null, actions::mediaNext) {
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
    content: @Composable () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .then(if (fill != null) Modifier.background(fill) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
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
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
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
            drawRoundRect(Color.White.copy(alpha = 0.16f), Offset(0f, top), Size(size.width, h), radius)
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
        color = AuraColors.TextFaint,
        fontSize = (11 * textScale).sp,
        // Chiffres à largeur fixe : le temps ne « danse » pas en défilant.
        style = TextStyle(fontFeatureSettings = "tnum"),
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

package com.aura.launcher.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.launcher.AppEntry
import com.aura.launcher.AppShortcut
import com.aura.launcher.LauncherActions
import com.aura.launcher.LauncherState
import com.aura.launcher.ShortcutPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.exp

/** Nombre maximal de raccourcis affichés en tête du menu d'appui long. */
private const val MENU_MAX = 4

/**
 * Geste « glisser vers la droite » sur un favori pour ouvrir ses raccourcis.
 *
 * La ligne suit le doigt avec une résistance élastique ; un chevron apparaît à gauche et passe à la
 * couleur d'accent (avec un petit retour haptique) quand on a glissé assez loin. Si l'app n'a aucun
 * raccourci, la ligne bouge à peine et revient (léger rebond), rien ne s'ouvre.
 *
 * Seul le geste horizontal est capté : les glissements verticaux (tiroir / notifications),
 * l'appui long et le double-toucher de l'accueil continuent de fonctionner.
 * Les lecteurs d'écran ont une action « Raccourcis » équivalente.
 */
@Composable
fun rememberShortcutSwipe(
    app: AppEntry,
    actions: LauncherActions,
    onOpen: (List<AppShortcut>) -> Unit,
): Modifier {
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val currentOnOpen by rememberUpdatedState(onOpen)

    // Distances (en pixels) : seuil d'ouverture, déplacement maximal, rebond quand il n'y a rien.
    val openAt = with(density) { 40.dp.toPx() }
    val maxShift = with(density) { 72.dp.toPx() }
    val bumpShift = with(density) { 12.dp.toPx() }
    val chevronSize = with(density) { 7.dp.toPx() }
    val chevronStroke = with(density) { 2.dp.toPx() }

    val shift = remember(app.key) { Animatable(0f) }
    var shortcuts by remember(app.key) { mutableStateOf<List<AppShortcut>>(emptyList()) }

    // Préchargement : on sait d'avance si l'app a des raccourcis (pour le rebond et l'accessibilité).
    LaunchedEffect(app.key) {
        shortcuts = withContext(Dispatchers.IO) { actions.loadShortcuts(app) }
    }
    val hasShortcuts = shortcuts.isNotEmpty()

    fun haptic(constant: Int) {
        view.performHapticFeedback(constant)
    }

    return Modifier
        .semantics {
            if (hasShortcuts) {
                customActions = listOf(
                    CustomAccessibilityAction("Raccourcis") {
                        currentOnOpen(shortcuts)
                        true
                    },
                )
            }
        }
        .pointerInput(app.key) {
            var raw = 0f
            var armed = false

            fun release(open: Boolean) {
                if (open && armed && shortcuts.isNotEmpty()) currentOnOpen(shortcuts)
                if (!open && armed && Build.VERSION.SDK_INT >= 34) {
                    haptic(HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE)
                }
                armed = false
                raw = 0f
                scope.launch {
                    shift.animateTo(
                        0f,
                        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
                    )
                }
            }

            detectHorizontalDragGestures(
                onDragStart = {
                    raw = 0f
                    armed = false
                    // Rafraîchit la liste au cas où l'app l'aurait changée depuis.
                    scope.launch {
                        shortcuts = withContext(Dispatchers.IO) { actions.loadShortcuts(app) }
                    }
                },
                onDragEnd = {
                    if (shortcuts.isEmpty() && raw > openAt && Build.VERSION.SDK_INT >= 30) {
                        haptic(HapticFeedbackConstants.REJECT)
                    }
                    release(open = true)
                },
                onDragCancel = { release(open = false) },
                onHorizontalDrag = { change, amount ->
                    change.consume()
                    // Seule la droite compte ; vers la gauche, la ligne reste en place.
                    raw = (raw + amount).coerceAtLeast(0f)
                    val limit = if (shortcuts.isEmpty()) bumpShift else maxShift
                    // Résistance élastique : plus on tire, moins la ligne avance.
                    val target = limit * (1f - exp(-raw / limit))
                    scope.launch { shift.snapTo(target) }
                    val nowArmed = shortcuts.isNotEmpty() && target >= openAt
                    if (nowArmed != armed) {
                        armed = nowArmed
                        if (armed) {
                            haptic(
                                if (Build.VERSION.SDK_INT >= 34) {
                                    HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
                                } else {
                                    HapticFeedbackConstants.CONTEXT_CLICK
                                },
                            )
                        } else if (Build.VERSION.SDK_INT >= 34) {
                            haptic(HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE)
                        }
                    }
                },
            )
        }
        .drawWithContent {
            val x = shift.value
            // Le contenu est seulement décalé au dessin : la mise en page ne bouge pas.
            translate(left = x) { this@drawWithContent.drawContent() }
            if (x > 0.5f && shortcuts.isNotEmpty()) {
                val progress = (x / openAt).coerceIn(0f, 1f)
                val color = if (progress >= 1f) AuraColors.Accent else AuraColors.TextDim
                val cx = x / 2f
                val cy = size.height / 2f
                val path = Path().apply {
                    moveTo(cx - chevronSize / 2f, cy - chevronSize)
                    lineTo(cx + chevronSize / 2f, cy)
                    lineTo(cx - chevronSize / 2f, cy + chevronSize)
                }
                drawPath(
                    path = path,
                    color = color.copy(alpha = color.alpha * progress),
                    style = Stroke(width = chevronStroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
}

/**
 * Panneau des raccourcis : une carte qui monte du bas de l'écran, au-dessus d'un voile sombre.
 * Toucher à côté, glisser la carte vers le bas ou appuyer sur Retour la ferme.
 */
@Composable
fun ShortcutsSheet(state: LauncherState, actions: LauncherActions) {
    val panel = state.shortcutPanel
    val visible = panel != null
    // On garde le dernier contenu pour que la carte ne se vide pas pendant l'animation de fermeture.
    var held by remember { mutableStateOf<ShortcutPanel?>(null) }
    if (panel != null && panel != held) held = panel

    val close = { state.shortcutPanel = null }
    BackHandler(enabled = visible) { close() }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(160)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x99000000))
                    .pointerInput(Unit) { detectTapGestures { close() } },
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(180, easing = FastOutLinearInEasing)) { it } + fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            held?.let { content ->
                ShortcutsCard(content, state, actions, onClose = close)
            }
        }
    }
}

@Composable
private fun ShortcutsCard(
    panel: ShortcutPanel,
    state: LauncherState,
    actions: LauncherActions,
    onClose: () -> Unit,
) {
    val dismissDrag = with(LocalDensity.current) { 56.dp.toPx() }
    val shape = RoundedCornerShape(24.dp)

    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .clip(shape)
            .background(AuraColors.Surface)
            .border(1.dp, AuraColors.TextFaint.copy(alpha = 0.12f), shape)
            // Absorbe les touchers sur la carte (sinon ils fermeraient le panneau) ; glisser vers le bas ferme.
            .pointerInput(Unit) { detectTapGestures { } }
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total > dismissDrag) onClose() },
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        total += amount
                    },
                )
            }
            .padding(vertical = 8.dp),
    ) {
        // En-tête : l'app concernée, en discret.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 6.dp),
        ) {
            AppIcon(panel.app, actions, 18.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                text = panel.app.label,
                color = AuraColors.TextDim,
                fontSize = 14.sp,
                letterSpacing = 0.4.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Column(
            Modifier
                .heightIn(max = 460.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            panel.shortcuts.forEach { shortcut ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Button) {
                            actions.launchShortcut(shortcut)
                            onClose()
                        }
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                ) {
                    ShortcutIcon(shortcut, actions, (26 * state.textScale).dp)
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = shortcut.label,
                        color = AuraColors.Text,
                        fontSize = (19 * state.textScale).sp,
                        fontWeight = FontWeight.Light,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Icône d'un raccourci ; un petit cercle discret si l'app n'en fournit pas. */
@Composable
private fun ShortcutIcon(shortcut: AppShortcut, actions: LauncherActions, size: Dp) {
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val icon by produceState<ImageBitmap?>(initialValue = null, shortcut.key, sizePx) {
        value = withContext(Dispatchers.IO) { actions.loadShortcutIcon(shortcut, sizePx) }
    }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        val bitmap = icon
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(size))
        } else {
            Box(
                Modifier
                    .size(size * 0.3f)
                    .border(1.5.dp, AuraColors.TextFaint, CircleShape),
            )
        }
    }
}

/**
 * Raccourcis affichés en tête du menu d'appui long (au plus [MENU_MAX]), suivis d'un séparateur.
 * N'affiche rien si l'app n'a pas de raccourci.
 */
@Composable
fun ShortcutMenuItems(app: AppEntry, actions: LauncherActions, onDismiss: () -> Unit) {
    val shortcuts by produceState<List<AppShortcut>>(initialValue = emptyList(), app.key) {
        value = withContext(Dispatchers.IO) { actions.loadShortcuts(app).take(MENU_MAX) }
    }
    if (shortcuts.isEmpty()) return
    shortcuts.forEach { shortcut ->
        DropdownMenuItem(
            text = { Text(shortcut.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { ShortcutIcon(shortcut, actions, 20.dp) },
            onClick = {
                onDismiss()
                actions.launchShortcut(shortcut)
            },
        )
    }
    HorizontalDivider(
        color = AuraColors.TextFaint.copy(alpha = 0.2f),
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

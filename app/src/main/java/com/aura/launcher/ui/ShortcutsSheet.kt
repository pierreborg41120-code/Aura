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
import com.aura.launcher.NotificationInfo
import com.aura.launcher.ShortcutPanel
import com.aura.launcher.WidgetProvider
import com.aura.launcher.WidgetRepository
import android.widget.FrameLayout
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.exp
import kotlin.math.roundToInt
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset

/** Nombre maximal de raccourcis affichés en tête du menu d'appui long. */
private const val MENU_MAX = 4

/**
 * Geste « glisser vers la droite » sur un favori pour ouvrir la carte de l'app
 * (notifications, widget, raccourcis).
 *
 * La ligne suit le doigt avec une résistance élastique ; un chevron apparaît à gauche et passe à la
 * couleur d'accent (avec un petit retour haptique) quand on a glissé assez loin. Si l'app n'a rien à
 * montrer (ni raccourci, ni notification, ni widget), la ligne bouge à peine et revient (léger rebond).
 *
 * Seul le geste horizontal est capté : les glissements verticaux (tiroir / notifications),
 * l'appui long et le double-toucher de l'accueil continuent de fonctionner.
 * Les lecteurs d'écran ont une action « Carte de l'app » équivalente.
 */
@Composable
fun rememberShortcutSwipe(
    app: AppEntry,
    state: LauncherState,
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
    var hasWidgetProviders by remember(app.key) { mutableStateOf(false) }

    // Préchargement : on sait d'avance si l'app a quelque chose à montrer (rebond, accessibilité).
    LaunchedEffect(app.key) {
        shortcuts = withContext(Dispatchers.IO) { actions.loadShortcuts(app) }
        hasWidgetProviders = withContext(Dispatchers.IO) { actions.loadWidgetProviders(app).isNotEmpty() }
    }

    /** Vrai si la carte aurait du contenu : raccourcis, notifications, widget attaché ou à ajouter. */
    fun hasContent(): Boolean =
        shortcuts.isNotEmpty() || hasWidgetProviders ||
            state.notificationsFor(app) != null || state.widgets[app.key] != null

    val hasShortcuts = hasContent()

    fun haptic(constant: Int) {
        view.performHapticFeedback(constant)
    }

    return Modifier
        .semantics {
            if (hasShortcuts) {
                customActions = listOf(
                    CustomAccessibilityAction("Carte de l'app") {
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
                if (open && armed && hasContent()) currentOnOpen(shortcuts)
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
                    if (!hasContent() && raw > openAt && Build.VERSION.SDK_INT >= 30) {
                        haptic(HapticFeedbackConstants.REJECT)
                    }
                    release(open = true)
                },
                onDragCancel = { release(open = false) },
                onHorizontalDrag = { change, amount ->
                    change.consume()
                    // Seule la droite compte ; vers la gauche, la ligne reste en place.
                    raw = (raw + amount).coerceAtLeast(0f)
                    val limit = if (hasContent()) maxShift else bumpShift
                    // Résistance élastique : plus on tire, moins la ligne avance.
                    val target = limit * (1f - exp(-raw / limit))
                    scope.launch { shift.snapTo(target) }
                    val nowArmed = hasContent() && target >= openAt
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
            if (x > 0.5f && hasContent()) {
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
    val app = panel.app
    val dismissDrag = with(LocalDensity.current) { 56.dp.toPx() }
    val shape = RoundedCornerShape(24.dp)
    // La carte ne dépasse jamais ~80 % de l'écran : au-delà, le contenu défile sous l'en-tête.
    val maxBody = (LocalConfiguration.current.screenHeightDp * 0.8f).dp - 72.dp

    // Sélecteur de widgets affiché à la place du contenu (même carte, pas de nouvelle fenêtre).
    var picking by remember(app.key) { mutableStateOf(false) }
    val providers by produceState<List<WidgetProvider>>(initialValue = emptyList(), app.key) {
        value = withContext(Dispatchers.IO) { actions.loadWidgetProviders(app) }
    }
    val widgetId = state.widgets[app.key]
    val notif = state.notificationsFor(app)

    BackHandler(enabled = picking) { picking = false }

    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .clip(shape)
            .background(AuraColors.Surface)
            .border(1.dp, AuraColors.TextFaint.copy(alpha = 0.12f), shape)
            // Absorbe les touchers sur la carte (sinon ils fermeraient le panneau).
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(bottom = 8.dp),
    ) {
        // Zone de prise (poignée + en-tête) : seul endroit où « glisser vers le bas » ferme la carte,
        // pour ne jamais voler les touchers des boutons du widget.
        Column(
            Modifier
                .fillMaxWidth()
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
                },
        ) {
            Box(
                Modifier
                    .padding(top = 10.dp)
                    .align(Alignment.CenterHorizontally)
                    .size(width = 32.dp, height = 4.dp)
                    .background(AuraColors.TextFaint.copy(alpha = 0.35f), CircleShape),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(start = 24.dp, end = 12.dp),
            ) {
                AppIcon(app, actions, 18.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (picking) "Choisir un widget" else app.label,
                    color = AuraColors.TextDim,
                    fontSize = 14.sp,
                    letterSpacing = 0.4.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (picking) {
                    TextAction("Annuler", AuraColors.TextDim) { picking = false }
                }
            }
        }

        Column(
            Modifier
                .heightIn(max = maxBody.coerceAtLeast(160.dp))
                .verticalScroll(rememberScrollState()),
        ) {
            if (picking) {
                WidgetPicker(
                    providers = providers,
                    actions = actions,
                    onPick = { provider ->
                        picking = false
                        actions.addWidget(app, provider)
                    },
                )
            } else {
                AppCardContent(panel, state, actions, notif, widgetId, providers, onClose) { picking = true }
            }
        }
    }
}

/** Contenu normal de la carte : notifications, widget, raccourcis, gestion du widget. */
@Composable
private fun AppCardContent(
    panel: ShortcutPanel,
    state: LauncherState,
    actions: LauncherActions,
    notif: NotificationInfo?,
    widgetId: Int?,
    providers: List<WidgetProvider>,
    onClose: () -> Unit,
    onPickWidget: () -> Unit,
) {
    val app = panel.app
    val sizing = widgetId?.let { rememberWidgetSizing(app, it, state, actions) }
    Column(Modifier.fillMaxWidth()) {
        // 1. Notifications de l'app : toucher l'aperçu ouvre l'app.
        if (notif != null) {
            NotificationBlock(notif, state.textScale) {
                actions.launch(app)
                onClose()
            }
        }

        // 2. Widget attaché (s'il existe encore).
        if (widgetId != null) {
            if (sizing != null) {
                key(widgetId) { AppWidget(app, widgetId, sizing, actions) }
            }
            Spacer(Modifier.height(4.dp))
        }

        // 3. Raccourcis.
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

        // 4. Gestion du widget, en discret (seulement si l'app en propose ou en a un d'attaché).
        if (providers.isNotEmpty() || widgetId != null) {
            HorizontalDivider(
                color = AuraColors.TextFaint.copy(alpha = 0.12f),
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            ) {
                if (widgetId == null) {
                    TextAction("Ajouter un widget", AuraColors.TextDim, onPickWidget)
                } else {
                    if (providers.isNotEmpty()) {
                        TextAction("Changer", AuraColors.TextDim, onPickWidget)
                    }
                    Spacer(Modifier.weight(1f))
                    if (sizing != null) {
                        SizeButton(plus = false, enabled = sizing.height > sizing.range.first) {
                            actions.setWidgetHeight(app, sizing.stepped(-WIDGET_STEP_DP))
                        }
                        SizeButton(plus = true, enabled = sizing.height < sizing.range.last) {
                            actions.setWidgetHeight(app, sizing.stepped(+WIDGET_STEP_DP))
                        }
                    }
                    TextAction("Retirer", AuraColors.TextDim) { actions.removeWidget(app) }
                }
            }
        }
    }
}

/** Petit bouton texte discret (zone de toucher d'au moins 48 dp). */
@Composable
private fun TextAction(label: String, color: Color, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
    ) {
        Text(text = label, color = color, fontSize = 14.sp, letterSpacing = 0.3.sp)
    }
}

/** Aperçu des notifications de l'app : un point, le nombre, puis le titre et le texte. */
@Composable
private fun NotificationBlock(notif: NotificationInfo, textScale: Float, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(6.dp)
                    .background(AuraColors.Accent, CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (notif.count > 1) "${notif.count} notifications" else "1 notification",
                color = AuraColors.TextDim,
                fontSize = 12.sp,
                letterSpacing = 0.4.sp,
            )
        }
        Spacer(Modifier.height(4.dp))
        val title = notif.title?.takeIf { it.isNotBlank() }
        val text = notif.text?.takeIf { it.isNotBlank() }?.replace('\n', ' ')
        if (title != null) {
            Text(
                text = title,
                color = AuraColors.Text,
                fontSize = (17 * textScale).sp,
                fontWeight = FontWeight.Light,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (text != null) {
            Text(
                text = text,
                color = AuraColors.TextDim,
                fontSize = (14 * textScale).sp,
                lineHeight = (20 * textScale).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}

/** Pas des boutons « − / + » de la taille du widget (dp). */
private const val WIDGET_STEP_DP = 20

/** Hauteur du widget de l'app : bornes permises et hauteur actuelle (choisie ou calculée), en dp. */
private class WidgetSizing(val range: IntRange, val height: Int) {
    fun stepped(delta: Int): Int = (height + delta).coerceIn(range.first, range.last)
}

@Composable
private fun rememberWidgetSizing(
    app: AppEntry,
    appWidgetId: Int,
    state: LauncherState,
    actions: LauncherActions,
): WidgetSizing? {
    val density = LocalDensity.current.density
    val info = remember(appWidgetId) { actions.widgetInfo(appWidgetId) } ?: return null
    val range = WidgetRepository.heightRangeDp(info, density)
    val height = (state.widgetHeights[app.key] ?: WidgetRepository.preferredHeightDp(info, density))
        .coerceIn(range.first, range.last)
    return WidgetSizing(range, height)
}

/**
 * Widget de l'app, sur toute la largeur de la carte. La vue Android est créée par l'hôte de widgets
 * d'Aura ; ses boutons fonctionnent normalement (rien ne capte leurs touchers au-dessus).
 *
 * Sous le widget, une poignée se glisse verticalement pour changer sa hauteur : la carte suit le
 * doigt, puis la hauteur est mémorisée et transmise au widget au relâchement (pour qu'il se remette
 * en page une seule fois, pas à chaque image).
 */
@Composable
private fun AppWidget(
    app: AppEntry,
    appWidgetId: Int,
    sizing: WidgetSizing,
    actions: LauncherActions,
) {
    val density = LocalDensity.current.density
    val view = LocalView.current
    val widgetShape = RoundedCornerShape(16.dp)
    val current by rememberUpdatedState(sizing)
    // Hauteur pendant le glissement de la poignée (null = pas de glissement en cours).
    var dragHeight by remember(appWidgetId) { mutableStateOf<Float?>(null) }
    val shownHeight = dragHeight?.roundToInt() ?: sizing.height

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(widgetShape)
            .background(AuraColors.Text.copy(alpha = 0.04f)),
    ) {
        // maxWidth est déjà la largeur intérieure de la carte (marges déduites), en dp.
        val widthDp = maxWidth.value.toInt()
        val committedHeight = sizing.height
        AndroidView(
            factory = { context ->
                actions.createWidgetView(context, appWidgetId) ?: FrameLayout(context)
            },
            update = { widgetView ->
                // On ne prévient le widget que si la taille mémorisée a changé (évite des appels inutiles).
                val size = widthDp to committedHeight
                if (widgetView.tag != size) {
                    widgetView.tag = size
                    WidgetRepository.applySize(widgetView, widthDp, committedHeight)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(shownHeight.dp),
        )
    }

    // Poignée de redimensionnement : un trait discret, zone de toucher de 28 dp de haut.
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("Agrandir le widget") {
                        actions.setWidgetHeight(app, current.stepped(+WIDGET_STEP_DP))
                        true
                    },
                    CustomAccessibilityAction("Réduire le widget") {
                        actions.setWidgetHeight(app, current.stepped(-WIDGET_STEP_DP))
                        true
                    },
                )
            }
            .pointerInput(appWidgetId) {
                detectVerticalDragGestures(
                    onDragStart = { dragHeight = current.height.toFloat() },
                    onDragEnd = {
                        dragHeight?.let { actions.setWidgetHeight(app, it.roundToInt()) }
                        dragHeight = null
                    },
                    onDragCancel = { dragHeight = null },
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        val range = current.range
                        val before = dragHeight ?: current.height.toFloat()
                        val next = (before + amount / density).coerceIn(range.first.toFloat(), range.last.toFloat())
                        // Petit retour haptique en butée.
                        if (next != before && (next == range.first.toFloat() || next == range.last.toFloat())) {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                        dragHeight = next
                    },
                )
            },
    ) {
        Box(
            Modifier
                .size(width = if (dragHeight != null) 48.dp else 28.dp, height = 3.dp)
                .background(
                    if (dragHeight != null) AuraColors.Accent else AuraColors.TextFaint.copy(alpha = 0.5f),
                    CircleShape,
                ),
        )
    }
}

/** Bouton « − » ou « + » de la taille du widget, dessiné en Canvas (zone de toucher de 48 dp). */
@Composable
private fun SizeButton(plus: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val color = if (enabled) AuraColors.TextDim else AuraColors.TextFaint.copy(alpha = 0.4f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = if (plus) "Agrandir le widget" else "Réduire le widget",
                onClick = onClick,
            ),
    ) {
        Canvas(Modifier.size(14.dp)) {
            val stroke = 1.6.dp.toPx()
            val mid = size.height / 2f
            drawLine(color, Offset(0f, mid), Offset(size.width, mid), stroke, StrokeCap.Round)
            if (plus) {
                drawLine(color, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), stroke, StrokeCap.Round)
            }
        }
    }
}

/** Liste des widgets proposés par l'app, avec leur aperçu. */
@Composable
private fun WidgetPicker(
    providers: List<WidgetProvider>,
    actions: LauncherActions,
    onPick: (WidgetProvider) -> Unit,
) {
    if (providers.isEmpty()) {
        Text(
            text = "Cette app ne propose aucun widget.",
            color = AuraColors.TextDim,
            fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        )
        return
    }
    val previewMaxPx = with(LocalDensity.current) { 280.dp.roundToPx() }
    providers.forEach { provider ->
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { onPick(provider) }
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            val preview by produceState<ImageBitmap?>(initialValue = null, provider.key) {
                value = withContext(Dispatchers.IO) { actions.loadWidgetPreview(provider, previewMaxPx) }
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(AuraColors.Text.copy(alpha = 0.04f))
                    .padding(12.dp),
            ) {
                preview?.let {
                    Image(
                        bitmap = it,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.heightIn(max = 120.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = provider.label,
                    color = AuraColors.Text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Light,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                provider.cells?.let {
                    Spacer(Modifier.width(10.dp))
                    Text(text = it, color = AuraColors.TextDim, fontSize = 13.sp)
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

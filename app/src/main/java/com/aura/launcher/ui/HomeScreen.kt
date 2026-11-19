package com.aura.launcher.ui

import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.runtime.mutableStateOf
import com.aura.launcher.HOME_WIDGET_UNDER_CLOCK
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aura.launcher.LauncherActions
import com.aura.launcher.LauncherState
import com.aura.launcher.ShortcutPanel
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    state: LauncherState,
    actions: LauncherActions,
    // --- Apparence --- aperçu dans l'éditeur : l'accueil est affiché mais ne réagit à aucun geste.
    preview: Boolean = false,
) {
    val density = LocalDensity.current
    val swipeThreshold = with(density) { 72.dp.toPx() }
    val haptics = LocalHapticFeedback.current
    val reducedMotion = rememberReducedMotion()
    val scope = rememberCoroutineScope()

    // Apparition douce : l'accueil s'efface sous le tiroir / les réglages et revient en fondu,
    // avec un léger glissement vers le haut. Même chose au retour d'une app (homeEntrance).
    val covered = state.drawerOpen || state.settingsOpen || state.onboardingOpen
    val reveal = remember { Animatable(1f) }
    LaunchedEffect(covered) {
        val target = if (covered) 0f else 1f
        if (reducedMotion) reveal.snapTo(target)
        else reveal.animateTo(
            target,
            tween(
                if (covered) AuraMotion.quick else AuraMotion.gentle,
                easing = if (covered) AuraMotion.exit else AuraMotion.enter,
            ),
        )
    }
    LaunchedEffect(state.homeEntrance) {
        if (state.homeEntrance > 0 && !covered && !reducedMotion) {
            reveal.snapTo(0.35f)
            reveal.animateTo(1f, tween(AuraMotion.gentle, easing = AuraMotion.enter))
        }
    }
    val slide = with(density) { AuraSpace.l.toPx() }
    // --- Accueil --- zone du widget d'accueil, que l'appui long « réglages » doit ignorer.
    val widgetArea = remember { HomeWidgetArea() }
    var homeOrigin by remember { mutableStateOf(Offset.Zero) }

    Column(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { homeOrigin = it.positionInRoot() }
            .then(if (preview) Modifier.blockGestures() else Modifier)
            // Glisser vers le haut : toutes les apps. Vers le bas : notifications.
            .pointerInputVerticalSwipe(
                threshold = swipeThreshold,
                onSwipeUp = { state.drawerOpen = true },
                onSwipeDown = { actions.performGesture(com.aura.launcher.Gesture.SWIPE_DOWN) },
            )
            // --- Gestes --- glisser à gauche / à droite dans le vide (les favoris gardent leur propre glisser).
            .pointerInputHorizontalSwipe(
                threshold = swipeThreshold,
                onSwipeLeft = { actions.performGesture(com.aura.launcher.Gesture.SWIPE_LEFT) },
                onSwipeRight = { actions.performGesture(com.aura.launcher.Gesture.SWIPE_RIGHT) },
            )
            // Appui long dans le vide : réglages. Double-toucher : verrouiller l'écran.
            .pointerInputTaps(
                onLongPress = { offset ->
                    // Sur le widget d'accueil, c'est lui qui ouvre son menu.
                    if (state.homeWidgetId == null || !widgetArea.rect.contains(offset + homeOrigin)) {
                        haptics.auraHaptic(HapticFeedbackType.LongPress)
                        state.settingsOpen = true
                    }
                },
                onDoubleTap = { actions.performGesture(com.aura.launcher.Gesture.DOUBLE_TAP) },
            )
            .graphicsLayer {
                alpha = reveal.value
                translationY = (1f - reveal.value) * slide
            }
            .systemBarsPadding()
            .padding(horizontal = AuraSpace.gutter),
    ) {
        Spacer(Modifier.height(AuraSpace.xxxl + AuraSpace.l))
        // --- Apparence --- style d'horloge choisi (ui/ClockStyles.kt).
        AuraClock(LocalAppearance.current.clock, LocalAppearance.current.alignment, actions)
        StatusLine(state, actions)
        RecapLine(state) // --- Récap des notifications ---
        // --- Accueil --- widget libre « sous l'heure ».
        if (state.homeWidgetPosition == HOME_WIDGET_UNDER_CLOCK && state.homeWidgetId != null) {
            Spacer(Modifier.height(AuraSpace.l))
            HomeWidget(state, actions, widgetArea)
        }

        Spacer(Modifier.weight(1f))

        state.media?.let { media ->
            MediaCard(media, actions, state.textScale)
            Spacer(Modifier.height(AuraSpace.xl))
        }
        // --- Accueil --- apps de musique quand des écouteurs sont branchés et que rien ne joue.
        HeadphonesRow(state, actions)
        // --- Accueil --- widget libre « au-dessus des favoris ».
        if (state.homeWidgetPosition != HOME_WIDGET_UNDER_CLOCK && state.homeWidgetId != null) {
            HomeWidget(state, actions, widgetArea)
            Spacer(Modifier.height(AuraSpace.l))
        }

        // --- Dossiers --- l'accueil mêle apps épinglées et dossiers.
        val favorites = state.homeItems
        // --- Apparence --- alignement et police choisis pour l'accueil.
        val alignment = LocalAppearance.current.alignment
        if (favorites.isEmpty()) {
            Text(
                text = "Glisse vers le haut pour voir tes apps,\npuis appui long sur une app pour l'épingler ici.",
                color = AuraColors.TextDim,
                style = AuraType.body.homeFont().onWallpaper(),
                textAlign = alignment.textAlign(),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            favorites.forEachIndexed { index, item ->
                if (item is com.aura.launcher.HomeItem.Group) {
                    FolderRow(
                        group = item,
                        state = state,
                        actions = actions,
                        showIcon = state.showIcons,
                        iconSize = (30 * state.textScale).dp,
                        textStyle = AuraType.favorite.scaled(state.textScale).homeFont().onWallpaper(),
                        alignment = alignment,
                        menu = buildList {
                            if (index > 0) add(MenuAction("Monter") { actions.moveHomeItem(item.key, -1) })
                            if (index < favorites.lastIndex) add(MenuAction("Descendre") { actions.moveHomeItem(item.key, +1) })
                            add(MenuAction("Renommer le dossier") { state.renamingFolder = item.folder })
                            add(MenuAction("Supprimer le dossier") { actions.deleteFolder(item.folder.id) })
                        },
                    )
                    return@forEachIndexed
                }
                val app = (item as com.aura.launcher.HomeItem.App).app
                val notif = state.notificationsFor(app)
                AppRow(
                    app = app,
                    actions = actions,
                    showIcon = state.showIcons,
                    iconSize = (30 * state.textScale).dp,
                    textStyle = AuraType.favorite.scaled(state.textScale).homeFont().onWallpaper(),
                    alignment = alignment,
                    verticalPadding = AuraSpace.s + AuraSpace.xxs,
                    badge = notif != null,
                    subtitle = if (state.showNotificationPreviews) notif?.preview else null,
                    // --- Réponses aux notifications --- toucher l'aperçu ouvre la carte de l'app
                    // sur ses notifications ; toucher le nom lance toujours l'app.
                    onSubtitleClick = {
                        scope.launch {
                            val shortcuts = withContext(Dispatchers.IO) { actions.loadShortcuts(app) }
                            state.shortcutPanel = ShortcutPanel(app, shortcuts)
                        }
                    },
                    // --- Raccourcis d'app --- glisser vers la droite ouvre le panneau des raccourcis.
                    modifier = rememberShortcutSwipe(app, state, actions) { state.shortcutPanel = ShortcutPanel(app, it) },
                    menu = buildList {
                        if (index > 0) add(MenuAction("Monter") { actions.moveFavorite(app, -1) })
                        if (index < favorites.lastIndex) add(MenuAction("Descendre") { actions.moveFavorite(app, +1) })
                        add(MenuAction("Retirer de l'accueil") { actions.toggleFavorite(app) })
                        add(MenuAction("Ajouter à un dossier…") { state.folderPicking = app })
                        add(limiterMenu(state, actions, app)) // --- Limiteur ---
                        add(MenuAction("Renommer") { state.renaming = app })
                        add(MenuAction("Infos de l'app") { actions.openAppInfo(app) })
                        add(MenuAction("Désinstaller") { actions.uninstall(app) })
                    },
                )
            }
        }

        Spacer(Modifier.height(AuraSpace.xl))
        DrawerHint(reducedMotion) { state.drawerOpen = true }
    }
}

/**
 * Invitation à glisser vers le haut : un chevron fin dessiné en Canvas, qui « respire » (monte de
 * quelques dp et s'éclaire, puis redescend). Immobile si les animations sont coupées.
 */
@Composable
private fun DrawerHint(reducedMotion: Boolean, onClick: () -> Unit) {
    val breath = if (reducedMotion) {
        0.5f
    } else {
        val transition = rememberInfiniteTransition(label = "respiration")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(AuraMotion.breath / 2, easing = AuraMotion.enter),
                RepeatMode.Reverse,
            ),
            label = "chevron",
        )
        value
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = "Toutes les apps" }
            .padding(vertical = AuraSpace.m),
    ) {
        Canvas(Modifier.size(width = 28.dp, height = 18.dp)) {
            val w = size.width
            val stroke = 1.6.dp.toPx()
            val lift = -breath * 3.dp.toPx()
            val color = AuraColors.Text.copy(alpha = 0.35f + 0.35f * breath)
            val path = Path().apply {
                moveTo(w * 0.1f, size.height * 0.62f)
                lineTo(w * 0.5f, size.height * 0.32f)
                lineTo(w * 0.9f, size.height * 0.62f)
            }
            translate(top = lift) {
                // Ombre douce sous le trait pour rester visible sur un fond clair.
                translate(top = 1.dp.toPx()) {
                    drawPath(path, AuraColors.TextShadow.copy(alpha = 0.35f), style = Stroke(stroke * 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/** Avale tous les touchers (aperçu de l'éditeur « Apparence »). */
private fun Modifier.blockGestures(): Modifier = this.then(
    Modifier.pointerInput("block") {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    .changes.forEach { it.consume() }
            }
        }
    },
)

private fun Modifier.pointerInputHorizontalSwipe(
    threshold: Float,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
): Modifier = this.then(
    Modifier.pointerInput("hswipe") {
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = {
                when {
                    total < -threshold -> onSwipeLeft()
                    total > threshold -> onSwipeRight()
                }
                total = 0f
            },
            onDragCancel = { total = 0f },
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                total += dragAmount
            },
        )
    },
)

/** --- Limiteur --- entrée de menu « Limiter » / « Ne plus limiter ». */
fun limiterMenu(state: LauncherState, actions: LauncherActions, app: com.aura.launcher.AppEntry): MenuAction {
    val limited = app.key in state.limitedApps
    return MenuAction(if (limited) "Ne plus limiter" else "Limiter") { actions.setLimited(app, !limited) }
}

private fun Modifier.pointerInputVerticalSwipe(
    threshold: Float,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
): Modifier = this.then(
    Modifier.pointerInput("swipe") {
        var total = 0f
        detectVerticalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = {
                when {
                    total < -threshold -> onSwipeUp()
                    total > threshold -> onSwipeDown()
                }
                total = 0f
            },
            onDragCancel = { total = 0f },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                total += dragAmount
            },
        )
    },
)

private fun Modifier.pointerInputTaps(onLongPress: (Offset) -> Unit, onDoubleTap: () -> Unit): Modifier = this.then(
    Modifier.pointerInput("taps") {
        detectTapGestures(
            onLongPress = { onLongPress(it) },
            onDoubleTap = { onDoubleTap() },
        )
    },
)

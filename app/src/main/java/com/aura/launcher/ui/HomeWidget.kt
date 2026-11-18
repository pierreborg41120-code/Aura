package com.aura.launcher.ui

import android.view.HapticFeedbackConstants
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.aura.launcher.LauncherActions
import com.aura.launcher.LauncherState
import com.aura.launcher.WidgetRepository
import kotlin.math.roundToInt

/** Pas des boutons « − / + » de la taille du widget d'accueil (dp). */
private const val HOME_WIDGET_STEP_DP = 20

/**
 * Zone occupée par le widget d'accueil (coordonnées de la racine) : l'appui long de l'accueil
 * l'ignore, c'est le widget qui ouvre alors son propre menu.
 */
class HomeWidgetArea {
    var rect: Rect = Rect.Zero
}

/**
 * Widget libre de l'accueil. La vue Android reçoit ses touchers normalement ; un appui long
 * (détecté sans rien voler au widget tant qu'il n'a pas abouti) ouvre « Changer · Taille · Retirer ».
 */
@Composable
fun HomeWidget(state: LauncherState, actions: LauncherActions, area: HomeWidgetArea) {
    val id = state.homeWidgetId ?: return
    val density = LocalDensity.current.density
    val view = LocalView.current
    val info = remember(id) { actions.widgetInfo(id) } ?: return
    val range = WidgetRepository.heightRangeDp(info, density)
    val height = (state.homeWidgetHeight ?: WidgetRepository.preferredHeightDp(info, density))
        .coerceIn(range.first, range.last)
    val currentHeight by rememberUpdatedState(height)

    var menuOpen by remember(id) { mutableStateOf(false) }
    var resizing by remember(id) { mutableStateOf(false) }
    var dragHeight by remember(id) { mutableStateOf<Float?>(null) }
    val shownHeight = dragHeight?.roundToInt() ?: height
    val shape = RoundedCornerShape(AuraRadius.m)

    BackHandler(enabled = resizing) { resizing = false }

    fun stepped(delta: Int) = (currentHeight + delta).coerceIn(range.first, range.last)

    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .onGloballyPositioned { area.rect = it.boundsInRoot() }
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction("Changer le widget") { state.homeWidgetPicking = true; true },
                        CustomAccessibilityAction("Régler la taille du widget") { resizing = true; true },
                        CustomAccessibilityAction("Retirer le widget") { actions.removeHomeWidget(); true },
                    )
                }
                .pointerInput(id) {
                    // Passe « Initial » : on voit le toucher avant le widget, sans le consommer.
                    // Si le doigt reste immobile assez longtemps, on ouvre le menu et on consomme la
                    // suite du geste : Android envoie alors une annulation au widget.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            var ended = false
                            while (!ended) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                ended = change == null || !change.pressed ||
                                    (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                            }
                            true
                        }
                        if (released == null) {
                            view.auraHaptic(HapticFeedbackConstants.LONG_PRESS)
                            menuOpen = true
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                },
        ) {
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .then(if (resizing) Modifier.border(1.dp, AuraColors.Accent.copy(alpha = 0.6f), shape) else Modifier),
            ) {
                val widthDp = maxWidth.value.toInt()
                key(id) {
                    AndroidView(
                        factory = { context -> actions.createWidgetView(context, id) ?: FrameLayout(context) },
                        update = { widgetView ->
                            // On ne prévient le widget que si la taille mémorisée a changé.
                            val size = widthDp to height
                            if (widgetView.tag != size) {
                                widgetView.tag = size
                                WidgetRepository.applySize(widgetView, widthDp, height)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(shownHeight.dp),
                    )
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                listOf(
                    "Changer" to { state.homeWidgetPicking = true },
                    "Taille" to { resizing = true },
                    "Retirer" to { actions.removeHomeWidget() },
                ).forEach { (label, onClick) ->
                    DropdownMenuItem(
                        text = { Text(label, style = AuraType.body) },
                        onClick = {
                            menuOpen = false
                            onClick()
                        },
                    )
                }
            }
        }

        if (resizing) {
            // Réglage de la taille : poignée à glisser, boutons − / + et « OK ».
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = AuraSpace.xs),
            ) {
                HomeSizeButton(plus = false, enabled = height > range.first) {
                    actions.setHomeWidgetHeight(stepped(-HOME_WIDGET_STEP_DP))
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(AuraSpace.touch)
                        .pointerInput(id) {
                            detectVerticalDragGestures(
                                onDragStart = { dragHeight = currentHeight.toFloat() },
                                onDragEnd = {
                                    dragHeight?.let { actions.setHomeWidgetHeight(it.roundToInt()) }
                                    dragHeight = null
                                },
                                onDragCancel = { dragHeight = null },
                                onVerticalDrag = { change, amount ->
                                    change.consume()
                                    val before = dragHeight ?: currentHeight.toFloat()
                                    val next = (before + amount / density)
                                        .coerceIn(range.first.toFloat(), range.last.toFloat())
                                    if (next != before && (next == range.first.toFloat() || next == range.last.toFloat())) {
                                        view.auraHaptic(HapticFeedbackConstants.CLOCK_TICK)
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
                                if (dragHeight != null) AuraColors.Accent else AuraColors.TextDim,
                                CircleShape,
                            ),
                    )
                }
                HomeSizeButton(plus = true, enabled = height < range.last) {
                    actions.setHomeWidgetHeight(stepped(+HOME_WIDGET_STEP_DP))
                }
                Text(
                    "OK",
                    color = AuraColors.Accent,
                    style = AuraType.label,
                    modifier = Modifier
                        .clip(RoundedCornerShape(AuraRadius.s))
                        .clickable(role = Role.Button) { resizing = false }
                        .padding(horizontal = AuraSpace.l, vertical = AuraSpace.m),
                )
            }
        }
    }
}

/** Bouton « − » ou « + » dessiné en Canvas (zone de toucher de 48 dp). */
@Composable
private fun HomeSizeButton(plus: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val color = if (enabled) AuraColors.Text else AuraColors.TextFaint
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(AuraSpace.touch)
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

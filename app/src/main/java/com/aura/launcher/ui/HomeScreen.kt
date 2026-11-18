package com.aura.launcher.ui

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.launcher.LauncherActions
import com.aura.launcher.LauncherState
import com.aura.launcher.ShortcutPanel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(state: LauncherState, actions: LauncherActions) {
    val swipeThreshold = with(LocalDensity.current) { 72.dp.toPx() }
    val haptics = LocalHapticFeedback.current

    Column(
        Modifier
            .fillMaxSize()
            // Glisser vers le haut : toutes les apps. Vers le bas : notifications.
            .pointerInputVerticalSwipe(
                threshold = swipeThreshold,
                onSwipeUp = { state.drawerOpen = true },
                onSwipeDown = { actions.expandNotifications() },
            )
            // Appui long dans le vide : réglages. Double-toucher : verrouiller l'écran.
            .pointerInputTaps(
                onLongPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    state.settingsOpen = true
                },
                onDoubleTap = { actions.lockScreen() },
            )
            .systemBarsPadding()
            .padding(horizontal = 28.dp),
    ) {
        Spacer(Modifier.height(56.dp))
        Clock(actions)
        StatusLine(state, actions)

        Spacer(Modifier.weight(1f))

        state.media?.let { media ->
            MediaCard(media, actions, state.textScale)
            Spacer(Modifier.height(20.dp))
        }

        val favorites = state.favoriteApps
        if (favorites.isEmpty()) {
            Text(
                text = "Glisse vers le haut pour voir tes apps,\npuis appui long sur une app pour l'épingler ici.",
                color = AuraColors.TextDim,
                fontSize = 15.sp,
                lineHeight = 22.sp,
            )
        } else {
            favorites.forEachIndexed { index, app ->
                val notif = state.notificationsFor(app)
                AppRow(
                    app = app,
                    actions = actions,
                    showIcon = state.showIcons,
                    iconSize = (30 * state.textScale).dp,
                    fontSize = (24 * state.textScale).sp,
                    verticalPadding = 9.dp,
                    badge = notif != null,
                    subtitle = if (state.showNotificationPreviews) notif?.preview else null,
                    // --- Raccourcis d'app --- glisser vers la droite ouvre le panneau des raccourcis.
                    modifier = rememberShortcutSwipe(app, state, actions) { state.shortcutPanel = ShortcutPanel(app, it) },
                    menu = buildList {
                        if (index > 0) add(MenuAction("Monter") { actions.moveFavorite(app, -1) })
                        if (index < favorites.lastIndex) add(MenuAction("Descendre") { actions.moveFavorite(app, +1) })
                        add(MenuAction("Retirer de l'accueil") { actions.toggleFavorite(app) })
                        add(MenuAction("Renommer") { state.renaming = app })
                        add(MenuAction("Infos de l'app") { actions.openAppInfo(app) })
                        add(MenuAction("Désinstaller") { actions.uninstall(app) })
                    },
                )
            }
        }

        Spacer(Modifier.height(32.dp))
        Text(
            text = "︿",
            color = AuraColors.TextFaint,
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { state.drawerOpen = true }
                .padding(vertical = 12.dp),
        )
    }
}

@Composable
private fun Clock(actions: LauncherActions) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Se met à jour pile au changement de minute.
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000 - now % 60_000)
        }
    }

    val time = DateFormat.getTimeFormat(context).format(Date(now))
    val date = SimpleDateFormat("EEEE d MMMM", Locale.getDefault())
        .format(Date(now))
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }

    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = time,
            color = AuraColors.Text,
            fontSize = 68.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { actions.openAlarms() },
        )
        Text(
            text = date,
            color = AuraColors.TextDim,
            fontSize = 17.sp,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { actions.openCalendar() },
        )
    }
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

private fun Modifier.pointerInputTaps(onLongPress: () -> Unit, onDoubleTap: () -> Unit): Modifier = this.then(
    Modifier.pointerInput("taps") {
        detectTapGestures(
            onLongPress = { onLongPress() },
            onDoubleTap = { onDoubleTap() },
        )
    },
)

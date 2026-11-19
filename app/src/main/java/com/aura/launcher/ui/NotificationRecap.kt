package com.aura.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aura.launcher.AppEntry
import com.aura.launcher.LauncherActions
import com.aura.launcher.LauncherState
import com.aura.launcher.NotificationInfo

/*
 * --- Récap des notifications --- une ligne sous l'heure (« 5 notifications · WhatsApp, Gmail »),
 * et une feuille qui regroupe toutes les notifications par app : lire, répondre, effacer.
 */

/** Apps qui ont des notifications, de la plus récente à la plus ancienne. */
private fun appsWithNotifications(state: LauncherState): List<Pair<AppEntry, NotificationInfo>> =
    state.visibleApps
        .mapNotNull { app -> state.notificationsFor(app)?.let { app to it } }
        .distinctBy { it.first.notificationKey }
        .sortedByDescending { it.second.postTime }

/** Ligne du récap sous l'heure ; rien si le réglage est coupé ou s'il n'y a aucune notification. */
@Composable
fun RecapLine(state: LauncherState) {
    if (!state.notifRecap || !state.hasNotificationAccess) return
    val list = appsWithNotifications(state)
    if (list.isEmpty()) return
    val total = list.sumOf { (_, info) -> info.items.size.coerceAtLeast(1) }
    val names = list.take(3).joinToString(", ") { it.first.label } + if (list.size > 3) "…" else ""
    val alignment = LocalAppearance.current.alignment
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = AuraSpace.s)
            .clip(auraShape(AuraRadius.s))
            .clickable(role = Role.Button, onClickLabel = "Voir les notifications") { state.recapOpen = true }
            .padding(vertical = AuraSpace.xxs),
    ) {
        HaloDot(size = 10.dp)
        Spacer(Modifier.width(AuraSpace.s))
        Text(
            text = (if (total > 1) "$total notifications" else "1 notification") + " · " + names,
            color = AuraColors.TextDim,
            style = AuraType.status.onWallpaper().homeFont(),
            textAlign = alignment.textAlign(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Feuille du récap : monte du bas, toutes les notifications groupées par app. */
@Composable
fun RecapSheet(state: LauncherState, actions: LauncherActions) {
    AnimatedVisibility(
        visible = state.recapOpen,
        enter = fadeIn(tween(AuraMotion.standard)),
        exit = fadeOut(tween(AuraMotion.quick)),
    ) {
        BackHandler(enabled = state.recapOpen) { state.recapOpen = false }
        Box(
            Modifier
                .fillMaxSize()
                .background(AuraColors.SheetScrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { state.recapOpen = false }
                .statusBarsPadding(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visible = state.recapOpen,
                enter = slideInVertically(tween(AuraMotion.gentle, easing = AuraMotion.enter)) { it / 3 },
                exit = slideOutVertically(tween(AuraMotion.standard, easing = AuraMotion.exit)) { it / 3 },
            ) {
                RecapCard(state, actions)
            }
        }
    }
}

@Composable
private fun RecapCard(state: LauncherState, actions: LauncherActions) {
    val list = appsWithNotifications(state)
    val shape = auraShape(topStart = AuraRadius.xl, topEnd = AuraRadius.xl)
    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            .clip(shape)
            .background(AuraColors.Surface)
            .border(1.dp, AuraColors.Hairline, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {}
            .navigationBarsPadding()
            .padding(top = AuraSpace.l),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = AuraSpace.xl, end = AuraSpace.m),
        ) {
            Text(
                "Notifications",
                color = AuraColors.Text,
                style = AuraType.screenTitle.copy(fontSize = AuraType.favorite.fontSize),
                modifier = Modifier.weight(1f),
            )
            if (list.isNotEmpty()) {
                TextButton(onClick = { actions.dismissAllNotifications() }) {
                    Text("Tout effacer", color = AuraColors.Accent, style = AuraType.button)
                }
            }
        }
        if (list.isEmpty()) {
            Text(
                "Plus aucune notification.",
                color = AuraColors.TextDim,
                style = AuraType.body,
                modifier = Modifier.padding(AuraSpace.xl),
            )
            return@Column
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = AuraSpace.l)) {
            list.forEachIndexed { index, (app, info) ->
                if (index > 0) {
                    HorizontalDivider(
                        color = AuraColors.Hairline,
                        modifier = Modifier.padding(horizontal = AuraSpace.xl, vertical = AuraSpace.xs),
                    )
                }
                // En-tête de l'app : toucher l'ouvre.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClickLabel = "Ouvrir ${app.label}") {
                            state.recapOpen = false
                            actions.launch(app)
                        }
                        .padding(horizontal = AuraSpace.xl, vertical = AuraSpace.s),
                ) {
                    AppIcon(app, actions, 24.dp)
                    Spacer(Modifier.width(AuraSpace.m))
                    Text(app.label, color = AuraColors.Text, style = AuraType.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                NotificationList(
                    app = app,
                    info = info,
                    actions = actions,
                    canReply = state.replyFromHome,
                    textScale = state.textScale,
                    onClose = { state.recapOpen = false },
                )
            }
        }
    }
}

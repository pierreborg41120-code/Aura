package com.aura.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aura.launcher.LauncherActions
import com.aura.launcher.LauncherState

/** Durée de la pause avant de pouvoir ouvrir une app limitée. */
private const val PAUSE_MS = 5_000

/**
 * --- Limiteur --- écran de pause avant d'ouvrir une app limitée : un anneau se remplit pendant
 * 5 secondes, puis « Ouvrir quand même » devient possible. « Laisser tomber » ou Retour annule.
 */
@Composable
fun LimiterPause(state: LauncherState, actions: LauncherActions) {
    AnimatedVisibility(
        visible = state.pendingLimited != null,
        enter = fadeIn(tween(AuraMotion.gentle)),
        exit = fadeOut(tween(AuraMotion.quick)),
    ) {
        val app = remember { state.pendingLimited } ?: return@AnimatedVisibility
        BackHandler(enabled = state.pendingLimited != null) { state.pendingLimited = null }

        val progress = remember(app.key) { Animatable(0f) }
        LaunchedEffect(app.key) {
            progress.animateTo(1f, tween(PAUSE_MS, easing = LinearEasing))
        }
        val ready = progress.value >= 1f
        val count = state.pendingLimitedCount
        val timeUp = state.pendingTimeUp
        val focus = state.pendingFocus && !timeUp
        val limit = state.appLimits[app.component.packageName]

        Box(
            Modifier
                .fillMaxSize()
                .themeBackdrop()
                .systemBarsPadding()
                .padding(AuraSpace.gutter),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(contentAlignment = Alignment.Center) {
                    val accent = AuraColors.Accent
                    val track = AuraColors.Hairline
                    Canvas(Modifier.size(120.dp)) {
                        val w = 3.dp.toPx()
                        drawCircle(track, style = Stroke(w))
                        drawArc(
                            accent,
                            startAngle = -90f,
                            sweepAngle = 360f * progress.value,
                            useCenter = false,
                            style = Stroke(w, cap = StrokeCap.Round),
                        )
                    }
                    AppIcon(app, actions, 48.dp)
                }
                Spacer(Modifier.height(AuraSpace.xl))
                Text(
                    when {
                        timeUp -> "Temps écoulé"
                        focus -> "Concentration"
                        else -> "Une petite pause ?"
                    },
                    color = AuraColors.Text,
                    style = AuraType.screenTitle,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(AuraSpace.s))
                Text(
                    when {
                        timeUp -> "Tu as utilisé tes ${limit?.let(::formatMinutes) ?: "minutes"} de ${app.label} pour aujourd'hui."
                        focus -> "${app.label} est mise de côté" +
                            (state.focusEndsAt?.let { " jusqu'à ${com.aura.launcher.formatClockAt(it)}" } ?: "") + "."
                        count == 0 -> "Pas encore ouverte aujourd'hui."
                        count == 1 -> "${app.label} : déjà ouverte 1 fois aujourd'hui."
                        else -> "${app.label} : déjà ouverte $count fois aujourd'hui."
                    },
                    color = AuraColors.TextDim,
                    style = AuraType.body,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(AuraSpace.xxl))
                TextButton(
                    onClick = { if (timeUp) actions.grantMoreTime(app) else actions.launchAnyway(app) },
                    enabled = ready,
                ) {
                    Text(
                        when {
                            !ready -> "Respire…"
                            timeUp -> "Encore 5 minutes"
                            else -> "Ouvrir quand même"
                        },
                        color = if (ready) AuraColors.Accent else AuraColors.TextFaint,
                        style = AuraType.button,
                    )
                }
                TextButton(onClick = { state.pendingLimited = null }) {
                    Text(if (timeUp) "D'accord" else "Laisser tomber", color = AuraColors.TextDim, style = AuraType.button)
                }
            }
        }
    }
}

/** « 30 min », « 1 h », « 2 h ». */
fun formatMinutes(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60}"
}

/** Durées proposées, en minutes par jour. */
val LimitChoices = listOf(5, 15, 30, 60, 120)

/**
 * « Limiter » : aucune limite, une pause avant chaque ouverture, ou une durée par jour (l'app se
 * ferme quand elle est écoulée ; il faut pour cela le service « Aura – verrouillage »).
 */
@Composable
fun LimitDialog(state: LauncherState, actions: LauncherActions) {
    val app = state.limitChoosing ?: return
    val pkg = app.component.packageName
    val currentMinutes = state.appLimits[pkg]
    val pauseOnly = app.key in state.limitedApps
    val inFocus = app.key in state.focus.apps
    AlertDialog(
        onDismissRequest = { state.limitChoosing = null },
        containerColor = AuraColors.Surface,
        titleContentColor = AuraColors.Text,
        textContentColor = AuraColors.TextDim,
        shape = auraShape(AuraRadius.xl),
        title = { Text("Limiter ${app.label}", style = AuraType.screenTitle.copy(fontSize = AuraType.favorite.fontSize)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                LimitLine("Pas de limite", currentMinutes == null && !pauseOnly) {
                    actions.setLimited(app, false)
                    actions.setAppLimit(app, null)
                }
                LimitLine("Pause de 5 s à chaque ouverture", pauseOnly) {
                    actions.setLimited(app, true)
                    state.limitChoosing = null
                }
                Text(
                    "Durée par jour",
                    color = AuraColors.Accent,
                    style = AuraType.label,
                    modifier = Modifier.padding(top = AuraSpace.m, bottom = AuraSpace.xs),
                )
                LimitChoices.forEach { m ->
                    LimitLine(formatMinutes(m), currentMinutes == m) { actions.setAppLimit(app, m) }
                }
                // --- Concentration --- indépendant des limites ci-dessus.
                Text(
                    "Concentration",
                    color = AuraColors.Accent,
                    style = AuraType.label,
                    modifier = Modifier.padding(top = AuraSpace.m, bottom = AuraSpace.xs),
                )
                LimitLine(
                    if (inFocus) "Mise de côté pendant la concentration ✓" else "Mettre de côté pendant la concentration",
                    inFocus,
                ) { actions.setFocusApp(app, !inFocus) }
                if (!state.hasLockAccess) {
                    Text(
                        "Pour fermer l'app quand le temps est écoulé, Aura a besoin du service « Aura – verrouillage » " +
                            "(le même que pour le double-toucher). Il voit seulement quelle app est ouverte, jamais l'écran.",
                        color = AuraColors.TextFaint,
                        style = AuraType.caption,
                        modifier = Modifier.padding(top = AuraSpace.m),
                    )
                    TextButton(onClick = {
                        state.limitChoosing = null
                        actions.openLockAccessSettings()
                    }) { Text("Activer le service", color = AuraColors.Accent, style = AuraType.button) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { state.limitChoosing = null }) {
                Text("Fermer", color = AuraColors.TextDim, style = AuraType.button)
            }
        },
    )
}

@Composable
private fun LimitLine(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) AuraColors.AccentLight else AuraColors.Text,
        style = AuraType.row,
        modifier = Modifier
            .fillMaxWidth()
            .clip(auraShape(AuraRadius.s))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = AuraSpace.s, vertical = AuraSpace.m),
    )
}

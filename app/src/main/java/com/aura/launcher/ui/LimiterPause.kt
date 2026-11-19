package com.aura.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
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
                    "Une petite pause ?",
                    color = AuraColors.Text,
                    style = AuraType.screenTitle,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(AuraSpace.s))
                Text(
                    when (count) {
                        0 -> "Pas encore ouverte aujourd'hui."
                        1 -> "${app.label} : déjà ouverte 1 fois aujourd'hui."
                        else -> "${app.label} : déjà ouverte $count fois aujourd'hui."
                    },
                    color = AuraColors.TextDim,
                    style = AuraType.body,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(AuraSpace.xxl))
                TextButton(onClick = { actions.launchAnyway(app) }, enabled = ready) {
                    Text(
                        if (ready) "Ouvrir quand même" else "Respire…",
                        color = if (ready) AuraColors.Accent else AuraColors.TextFaint,
                        style = AuraType.button,
                    )
                }
                TextButton(onClick = { state.pendingLimited = null }) {
                    Text("Laisser tomber", color = AuraColors.TextDim, style = AuraType.button)
                }
            }
        }
    }
}

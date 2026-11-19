package com.aura.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aura.launcher.AppEntry
import com.aura.launcher.LauncherActions
import com.aura.launcher.LauncherState

/** Nombre maximal d'apps de musique proposées. */
private const val MAX_APPS = 4

/**
 * Ligne discrète « Écouteurs connectés » : petit casque dessiné, libellé, puis les icônes de quelques
 * apps de musique. Affichée à la place du mini-lecteur quand rien ne joue ; disparaît dès que la
 * musique démarre ou que les écouteurs sont débranchés.
 */
@Composable
fun HeadphonesRow(state: LauncherState, actions: LauncherActions) {
    val apps = headphoneApps(state)
    val visible = state.showHeadphoneApps && state.headphonesConnected && state.media == null && apps.isNotEmpty()
    val reducedMotion = rememberReducedMotion()
    AnimatedVisibility(
        visible = visible,
        enter = if (reducedMotion) fadeIn(tween(0)) else fadeIn(tween(AuraMotion.standard)) +
            expandVertically(tween(AuraMotion.standard, easing = AuraMotion.enter)),
        exit = if (reducedMotion) fadeOut(tween(0)) else fadeOut(tween(AuraMotion.quick)) +
            shrinkVertically(tween(AuraMotion.standard, easing = AuraMotion.exit)),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                HeadphonesGlyph()
                Spacer(Modifier.width(AuraSpace.s))
                Text(
                    "Écouteurs connectés",
                    color = AuraColors.TextDim,
                    style = AuraType.status.scaled(state.textScale).homeFont().onWallpaper(),
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                // Icônes collées à droite ; la zone de toucher (48 dp) déborde un peu de l'icône.
                apps.forEach { app ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(AuraSpace.touch)
                            .clip(CircleShape)
                            .clickable(role = Role.Button) { actions.launch(app) }
                            .semantics { contentDescription = "Ouvrir ${app.label}" },
                    ) {
                        AppIcon(app, actions, (30 * state.textScale).dp)
                    }
                }
            }
            Spacer(Modifier.height(AuraSpace.l))
        }
    }
}

/** Relie les paquets détectés aux apps visibles (une par paquet, profil principal d'abord). */
private fun headphoneApps(state: LauncherState): List<AppEntry> {
    if (state.headphonePackages.isEmpty()) return emptyList()
    val byPackage = state.visibleApps.groupBy { it.packageName }
    return state.headphonePackages
        .mapNotNull { pkg -> byPackage[pkg]?.minByOrNull { it.userSerial } }
        .take(MAX_APPS)
}

/** Petit casque au trait fin : un arceau et deux oreillettes arrondies. */
@Composable
private fun HeadphonesGlyph() {
    Canvas(Modifier.size(16.dp)) {
        val stroke = 1.5.dp.toPx()
        val w = size.width
        val h = size.height
        val color = AuraColors.TextDim
        // Arceau : demi-cercle supérieur.
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(stroke, stroke),
            size = Size(w - 2 * stroke, h - 2 * stroke),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
        // Branches qui descendent vers les oreillettes.
        drawLine(color, Offset(stroke, h / 2f), Offset(stroke, h * 0.72f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w - stroke, h / 2f), Offset(w - stroke, h * 0.72f), stroke, StrokeCap.Round)
        // Oreillettes pleines.
        val earW = w * 0.22f
        val earH = h * 0.36f
        drawRoundRect(
            color = color,
            topLeft = Offset(0f, h - earH),
            size = Size(earW, earH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(earW / 2f),
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(w - earW, h - earH),
            size = Size(earW, earH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(earW / 2f),
        )
    }
}

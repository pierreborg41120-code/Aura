package com.aura.launcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.aura.launcher.HomeProfile
import com.aura.launcher.LauncherActions
import com.aura.launcher.LauncherState
import com.aura.launcher.formatClockAt

/*
 * --- Concentration / Profils d'accueil --- petite ligne sous l'heure : le profil en cours
 * (toucher pour changer) et la concentration en cours (toucher pour l'arrêter).
 */

@Composable
fun ModeLine(state: LauncherState, actions: LauncherActions) {
    val profiles = state.profiles.enabled
    val focus = state.focusActive && state.focus.apps.isNotEmpty()
    if (!profiles && !focus) return
    val style = AuraType.status.onWallpaper().homeFont()
    Column(
        horizontalAlignment = LocalAppearance.current.alignment.horizontal(),
        modifier = Modifier.fillMaxWidth().padding(top = AuraSpace.s),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (profiles) {
                val other = if (state.homeProfile == HomeProfile.TRAVAIL) HomeProfile.PERSO else HomeProfile.TRAVAIL
                Text(
                    HomeProfile.label(state.homeProfile),
                    color = AuraColors.Accent,
                    style = style,
                    modifier = Modifier
                        .clip(auraShape(AuraRadius.s))
                        .clickable(role = Role.Button, onClickLabel = "Passer sur ${HomeProfile.label(other)}") {
                            actions.switchProfile(other)
                        }
                        .padding(vertical = AuraSpace.xxs),
                )
            }
            if (profiles && focus) Text("  ·  ", color = AuraColors.TextDim, style = style)
            if (focus) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(auraShape(AuraRadius.s))
                        .clickable(role = Role.Button, onClickLabel = "Concentration") { state.focusDialogOpen = true }
                        .padding(vertical = AuraSpace.xxs),
                ) {
                    HaloDot(size = 8.dp)
                    Spacer(Modifier.width(AuraSpace.s))
                    Text(
                        "Concentration" + (state.focusEndsAt?.let { " jusqu'à ${formatClockAt(it)}" } ?: ""),
                        color = AuraColors.TextDim,
                        style = style,
                    )
                }
            }
        }
    }
}

/** Durées proposées pour lancer la concentration à la main, en minutes. */
val FocusDurations = listOf(30, 60, 120, 240)

/** Boîte « Concentration » : arrêter celle en cours, ou en lancer une maintenant. */
@Composable
fun FocusDialog(state: LauncherState, actions: LauncherActions) {
    if (!state.focusDialogOpen) return
    val active = state.focusActive
    AlertDialog(
        onDismissRequest = { state.focusDialogOpen = false },
        containerColor = AuraColors.Surface,
        titleContentColor = AuraColors.Text,
        textContentColor = AuraColors.TextDim,
        shape = auraShape(AuraRadius.xl),
        title = { Text("Concentration", style = AuraType.screenTitle.copy(fontSize = AuraType.favorite.fontSize)) },
        text = {
            Column {
                val count = state.focus.apps.size
                Text(
                    when {
                        count == 0 -> "Aucune app mise de côté. Appui long sur une app → « Limiter… » → « Mettre de côté pendant la concentration »."
                        active -> "$count app${if (count > 1) "s" else ""} mise${if (count > 1) "s" else ""} de côté" +
                            (state.focusEndsAt?.let { " jusqu'à ${formatClockAt(it)}" } ?: "") + "."
                        else -> "Mettre de côté $count app${if (count > 1) "s" else ""} pendant :"
                    },
                    style = AuraType.body,
                )
                if (!active && count > 0) {
                    FocusDurations.forEach { minutes ->
                        Text(
                            formatMinutes(minutes),
                            color = AuraColors.Text,
                            style = AuraType.row,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(auraShape(AuraRadius.s))
                                .clickable(role = Role.Button) { actions.startFocus(minutes) }
                                .padding(horizontal = AuraSpace.s, vertical = AuraSpace.m),
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (active) {
                TextButton(onClick = { actions.stopFocus() }) {
                    Text("Arrêter", color = AuraColors.Accent, style = AuraType.button)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { state.focusDialogOpen = false }) {
                Text("Fermer", color = AuraColors.TextDim, style = AuraType.button)
            }
        },
    )
}

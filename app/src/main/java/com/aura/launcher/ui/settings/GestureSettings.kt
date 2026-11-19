package com.aura.launcher.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aura.launcher.Gesture
import com.aura.launcher.GestureAction
import com.aura.launcher.LauncherActions
import com.aura.launcher.LauncherState
import com.aura.launcher.ui.AuraColors
import com.aura.launcher.ui.AuraRadius
import com.aura.launcher.ui.AuraSpace
import com.aura.launcher.ui.AuraType
import com.aura.launcher.ui.SectionTitle
import com.aura.launcher.ui.auraShape

/**
 * --- Gestes --- action de chaque geste de l'accueil : rien, notifications, verrouiller, recherche,
 * toutes les apps, ou ouvrir une app choisie.
 */
@Composable
fun GestureSettings(state: LauncherState, actions: LauncherActions) {
    var choosing by remember { mutableStateOf<Gesture?>(null) }
    var pickingApp by remember { mutableStateOf<Gesture?>(null) }

    SectionTitle("Gestes")
    Gesture.entries.forEach { gesture ->
        val action = state.gestureActions[gesture] ?: GestureAction.default(gesture)
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button) { choosing = gesture }
                .padding(vertical = AuraSpace.s),
        ) {
            Text(gesture.label, color = AuraColors.Text, style = AuraType.body.copy(fontSize = AuraType.date.fontSize))
            Text(actionLabel(state, action), color = AuraColors.Accent, style = AuraType.bodySmall)
        }
    }
    Text(
        "Glisser vers le haut ouvre toujours toutes les apps, et l'appui long les réglages.",
        color = AuraColors.TextFaint,
        style = AuraType.caption,
        modifier = Modifier.padding(top = AuraSpace.xs, bottom = AuraSpace.s),
    )

    choosing?.let { gesture ->
        val current = state.gestureActions[gesture] ?: GestureAction.default(gesture)
        AlertDialog(
            onDismissRequest = { choosing = null },
            containerColor = AuraColors.Surface,
            titleContentColor = AuraColors.Text,
            shape = auraShape(AuraRadius.xl),
            title = { Text(gesture.label, style = AuraType.screenTitle.copy(fontSize = AuraType.favorite.fontSize)) },
            text = {
                Column {
                    GestureAction.fixed.forEach { (value, label) ->
                        ChoiceLine(label, selected = current == value) {
                            actions.setGestureAction(gesture, value)
                            choosing = null
                        }
                    }
                    ChoiceLine(
                        if (current.startsWith(GestureAction.APP_PREFIX)) "Ouvrir une app : ${actionLabel(state, current)}"
                        else "Ouvrir une app…",
                        selected = current.startsWith(GestureAction.APP_PREFIX),
                    ) {
                        choosing = null
                        pickingApp = gesture
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { choosing = null }) {
                    Text("Fermer", color = AuraColors.TextDim, style = AuraType.button)
                }
            },
        )
    }

    pickingApp?.let { gesture ->
        AlertDialog(
            onDismissRequest = { pickingApp = null },
            containerColor = AuraColors.Surface,
            titleContentColor = AuraColors.Text,
            shape = auraShape(AuraRadius.xl),
            title = { Text("Quelle app ?", style = AuraType.screenTitle.copy(fontSize = AuraType.favorite.fontSize)) },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(state.visibleApps, key = { it.key }) { app ->
                        ChoiceLine(app.label, selected = false) {
                            actions.setGestureAction(gesture, GestureAction.app(app.key))
                            pickingApp = null
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickingApp = null }) {
                    Text("Annuler", color = AuraColors.TextDim, style = AuraType.button)
                }
            },
        )
    }
}

/** Nom lisible d'une action enregistrée. */
private fun actionLabel(state: LauncherState, action: String): String {
    if (action.startsWith(GestureAction.APP_PREFIX)) {
        val key = action.removePrefix(GestureAction.APP_PREFIX)
        return state.apps.firstOrNull { it.key == key }?.label ?: "App désinstallée"
    }
    return GestureAction.fixed.firstOrNull { it.first == action }?.second ?: "Rien"
}

@Composable
private fun ChoiceLine(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) AuraColors.AccentLight else AuraColors.Text,
        style = AuraType.row,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clip(auraShape(AuraRadius.s))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = AuraSpace.s, vertical = AuraSpace.m),
    )
}

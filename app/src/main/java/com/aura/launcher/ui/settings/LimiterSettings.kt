package com.aura.launcher.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aura.launcher.LauncherActions
import com.aura.launcher.LauncherState
import com.aura.launcher.ui.AuraColors
import com.aura.launcher.ui.AuraSpace
import com.aura.launcher.ui.AuraType
import com.aura.launcher.ui.SectionTitle

/** --- Limiteur --- apps qui affichent une pause avant de s'ouvrir. */
@Composable
fun LimiterSettings(state: LauncherState, actions: LauncherActions) {
    SectionTitle("Apps limitées")
    val limited = state.apps.filter { it.key in state.limitedApps }.sortedBy { it.label.lowercase() }
    if (limited.isEmpty()) {
        Text(
            "Aucune. Appui long sur une app → « Limiter » : une pause de 5 secondes s'affichera avant chaque " +
                "ouverture, avec le nombre d'ouvertures du jour.",
            color = AuraColors.TextFaint,
            style = AuraType.caption,
            modifier = Modifier.padding(vertical = AuraSpace.s),
        )
        return
    }
    limited.forEach { app ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(app.label, color = AuraColors.Text, style = AuraType.row, modifier = Modifier.weight(1f))
            TextButton(onClick = { actions.setLimited(app, false) }) {
                Text("Retirer", color = AuraColors.Accent, style = AuraType.label)
            }
        }
    }
}

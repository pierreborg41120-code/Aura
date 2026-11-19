package com.aura.launcher.ui.settings

import androidx.compose.foundation.layout.Column
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
import com.aura.launcher.ScreenTime
import com.aura.launcher.ui.AuraColors
import com.aura.launcher.ui.AuraSpace
import com.aura.launcher.ui.AuraType
import com.aura.launcher.ui.SectionTitle
import com.aura.launcher.ui.formatMinutes

/** --- Limiteur --- apps limitées (pause à l'ouverture ou durée par jour) ; toucher « Modifier » change la limite. */
@Composable
fun LimiterSettings(state: LauncherState, actions: LauncherActions) {
    SectionTitle("Apps limitées")
    val limited = state.apps
        .filter { it.key in state.limitedApps || it.component.packageName in state.appLimits }
        .distinctBy { it.component.packageName }
        .sortedBy { it.label.lowercase() }
    if (limited.isEmpty()) {
        Text(
            "Aucune. Appui long sur une app → « Limiter… » : une pause avant chaque ouverture, ou une durée " +
                "par jour au-delà de laquelle l'app se ferme.",
            color = AuraColors.TextFaint,
            style = AuraType.caption,
            modifier = Modifier.padding(vertical = AuraSpace.s),
        )
        return
    }
    limited.forEach { app ->
        val minutes = state.appLimits[app.component.packageName]
        val detail = if (minutes != null) {
            val used = (ScreenTime.usedToday(app.component.packageName) / 60_000).toInt()
            "${formatMinutes(minutes)} par jour · $used min aujourd'hui"
        } else {
            "Pause à l'ouverture"
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(app.label, color = AuraColors.Text, style = AuraType.row)
                Text(detail, color = AuraColors.TextDim, style = AuraType.bodySmall)
            }
            TextButton(onClick = { state.limitChoosing = app }) {
                Text("Modifier", color = AuraColors.Accent, style = AuraType.label)
            }
        }
    }
    if (state.appLimits.isNotEmpty() && !state.hasLockAccess) {
        Text(
            "Les durées ne sont pas appliquées : active « Aura – verrouillage » (section Gestes).",
            color = AuraColors.TextFaint,
            style = AuraType.caption,
            modifier = Modifier.padding(vertical = AuraSpace.s),
        )
    }
}

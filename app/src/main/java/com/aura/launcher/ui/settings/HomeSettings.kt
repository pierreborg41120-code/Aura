package com.aura.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.aura.launcher.HOME_WIDGET_ABOVE_FAVORITES
import com.aura.launcher.HOME_WIDGET_UNDER_CLOCK
import com.aura.launcher.LauncherActions
import com.aura.launcher.LauncherState
import com.aura.launcher.ui.AuraColors
import com.aura.launcher.ui.AuraRadius
import com.aura.launcher.ui.AuraSpace
import com.aura.launcher.ui.AuraType
import com.aura.launcher.ui.ChevronGlyph
import com.aura.launcher.ui.auraSwitchColors

/**
 * Section « Accueil » des réglages : widget libre, apps de musique aux écouteurs, rotation et
 * retour haptique. Appelée en une ligne depuis SettingsScreen.
 */
@Composable
fun HomeSettings(state: LauncherState, actions: LauncherActions) {
    Text(
        "Accueil",
        color = AuraColors.Accent,
        style = AuraType.label,
        modifier = Modifier.padding(bottom = AuraSpace.xs),
    )

    val hasWidget = state.homeWidgetId != null
    Link(if (hasWidget) "Changer le widget de l'accueil" else "Ajouter un widget à l'accueil") {
        state.homeWidgetPicking = true
    }
    if (hasWidget) {
        Choice(
            label = "Position du widget",
            options = listOf("Sous l'heure" to HOME_WIDGET_UNDER_CLOCK, "Au-dessus des favoris" to HOME_WIDGET_ABOVE_FAVORITES),
            selected = state.homeWidgetPosition,
            onSelect = actions::setHomeWidgetPosition,
        )
        Link("Retirer le widget") { actions.removeHomeWidget() }
        Caption("Appui long sur le widget pour le changer, régler sa taille ou le retirer.")
    }

    Toggle("Apps de musique quand des écouteurs se connectent", state.showHeadphoneApps, actions::setShowHeadphoneApps)
    Toggle("Rotation de l'écran d'accueil", state.allowRotation, actions::setAllowRotation)
    Toggle("Retour haptique", state.hapticsEnabled, actions::setHapticsEnabled)
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(vertical = AuraSpace.s),
    ) {
        Text(
            label,
            color = AuraColors.Text,
            style = AuraType.body.copy(fontSize = AuraType.date.fontSize),
            modifier = Modifier.weight(1f).padding(end = AuraSpace.l),
        )
        Switch(checked = checked, onCheckedChange = onChange, colors = auraSwitchColors())
    }
}

@Composable
private fun Link(label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = AuraSpace.s),
    ) {
        Text(
            label,
            color = AuraColors.Text,
            style = AuraType.body.copy(fontSize = AuraType.date.fontSize),
            modifier = Modifier.weight(1f).padding(end = AuraSpace.l),
        )
        ChevronGlyph(AuraColors.TextFaint)
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text,
        color = AuraColors.TextFaint,
        style = AuraType.caption,
        modifier = Modifier.padding(bottom = AuraSpace.s),
    )
}

/** Choix exclusif en pastilles, la sélection cerclée de lavande (même dessin que « Taille du texte »). */
@Composable
private fun Choice(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = AuraSpace.m)) {
        Text(label, color = AuraColors.Text, style = AuraType.body.copy(fontSize = AuraType.date.fontSize))
        Row(
            horizontalArrangement = Arrangement.spacedBy(AuraSpace.s),
            modifier = Modifier.padding(top = AuraSpace.m),
        ) {
            options.forEach { (name, value) ->
                val isSelected = value == selected
                val shape = RoundedCornerShape(AuraRadius.s)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .clip(shape)
                        .background(if (isSelected) AuraColors.Accent.copy(alpha = 0.14f) else AuraColors.Surface)
                        .border(1.dp, if (isSelected) AuraColors.Accent else AuraColors.Hairline, shape)
                        .clickable(role = Role.RadioButton) { onSelect(value) }
                        .padding(horizontal = AuraSpace.l),
                ) {
                    Text(
                        name,
                        color = if (isSelected) AuraColors.AccentLight else AuraColors.TextDim,
                        style = AuraType.label,
                    )
                }
            }
        }
    }
}

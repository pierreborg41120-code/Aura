package com.aura.launcher.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.aura.launcher.LauncherActions
import com.aura.launcher.LauncherState
import com.aura.launcher.ui.AuraColors
import com.aura.launcher.ui.AuraSpace
import com.aura.launcher.ui.AuraType
import com.aura.launcher.ui.ChevronGlyph
import com.aura.launcher.ui.auraSwitchColors

/**
 * Section « Tiroir et recherche » des réglages : contacts dans la recherche, tri par utilisation,
 * apps suggérées. Reprend l'apparence des lignes de SettingsScreen (titre lavande, interrupteurs).
 */
@Composable
fun DrawerSettings(state: LauncherState, actions: LauncherActions) {
    Text(
        "Tiroir et recherche",
        color = AuraColors.Accent,
        style = AuraType.label,
        modifier = Modifier.padding(top = AuraSpace.xl + AuraSpace.xs, bottom = AuraSpace.xs),
    )
    Toggle("Chercher dans les contacts", state.searchContacts && state.hasContactsAccess, actions::setSearchContacts)
    Note(
        "Affiche tes contacts sous les apps quand tu tapes un nom. Ils sont lus sur le téléphone, " +
            "rien n'est envoyé ailleurs.",
    )
    Toggle("Trier par utilisation dans chaque lettre", state.sortByUsage, actions::setSortByUsage)
    Toggle("Apps suggérées en haut du tiroir", state.showSuggestions, actions::setShowSuggestions)
    Note("Aura compte les apps que tu ouvres depuis lui, sur ce téléphone seulement. Les vieux lancements comptent de moins en moins.")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button) { actions.clearUsage() }
            .padding(vertical = AuraSpace.s),
    ) {
        Text(
            "Effacer l'historique d'utilisation",
            color = AuraColors.Text,
            style = AuraType.body.copy(fontSize = AuraType.date.fontSize),
            modifier = Modifier.weight(1f).padding(end = AuraSpace.l),
        )
        ChevronGlyph(AuraColors.TextFaint)
    }
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
private fun Note(text: String) {
    Text(
        text,
        color = AuraColors.TextFaint,
        style = AuraType.caption,
        modifier = Modifier.padding(bottom = AuraSpace.s),
    )
}

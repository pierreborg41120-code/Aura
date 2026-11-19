package com.aura.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aura.launcher.LauncherActions
import com.aura.launcher.LauncherState
import com.aura.launcher.WeatherPlace
import com.aura.launcher.ui.AuraColors
import com.aura.launcher.ui.AuraRadius
import com.aura.launcher.ui.AuraSpace
import com.aura.launcher.ui.AuraType
import com.aura.launcher.ui.ChevronGlyph
import com.aura.launcher.ui.auraSwitchColors
import kotlinx.coroutines.delay

/**
 * Section « Météo » des réglages : interrupteur (désactivé par défaut), source « Ma position » ou
 * « Ville… » (recherche via le géocodage d'Open-Meteo), unité °C / °F.
 */
@Composable
fun WeatherSettings(state: LauncherState, actions: LauncherActions) {
    Text(
        "Météo",
        color = AuraColors.Accent,
        style = AuraType.label,
        modifier = Modifier.padding(top = AuraSpace.xl + AuraSpace.xs, bottom = AuraSpace.xs),
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Switch) { actions.setWeatherEnabled(!state.weatherEnabled) }
            .padding(vertical = AuraSpace.s),
    ) {
        Text(
            "Météo sous l'heure",
            color = AuraColors.Text,
            style = AuraType.body.copy(fontSize = AuraType.date.fontSize),
            modifier = Modifier.weight(1f).padding(end = AuraSpace.l),
        )
        Switch(
            checked = state.weatherEnabled,
            onCheckedChange = actions::setWeatherEnabled,
            colors = auraSwitchColors(),
        )
    }

    if (state.weatherEnabled) {
        // « Ville… » choisie à la main mais encore aucune ville (ou on veut en changer) : champ de recherche.
        var editingCity by remember { mutableStateOf(false) }
        val showSearch = state.weatherUseCity && (state.weatherCity == null || editingCity)

        Pills(
            label = "Lieu",
            options = listOf("Ma position", "Ville…"),
            selected = if (state.weatherUseCity) 1 else 0,
            onSelect = { index ->
                if (index == 0) {
                    editingCity = false
                    actions.useWeatherLocation()
                } else if (state.weatherUseCity) {
                    editingCity = true
                } else {
                    actions.useWeatherCity()
                }
            },
        )

        if (state.weatherUseCity) {
            val city = state.weatherCity
            if (showSearch) {
                CitySearch(
                    actions = actions,
                    onPicked = { place ->
                        editingCity = false
                        actions.setWeatherCity(place)
                    },
                )
            } else if (city != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Button, onClickLabel = "Changer de ville") { editingCity = true }
                        .padding(vertical = AuraSpace.s),
                ) {
                    Column(Modifier.weight(1f).padding(end = AuraSpace.l)) {
                        Text(city.name, color = AuraColors.Text, style = AuraType.body.copy(fontSize = AuraType.date.fontSize))
                        if (city.detail.isNotBlank()) {
                            Text(city.detail, color = AuraColors.TextDim, style = AuraType.bodySmall)
                        }
                    }
                    Text("Changer", color = AuraColors.Accent, style = AuraType.label)
                }
            }
        }

        Pills(
            label = "Unité",
            options = listOf("°C", "°F"),
            selected = if (state.weatherFahrenheit) 1 else 0,
            onSelect = { actions.setWeatherFahrenheit(it == 1) },
        )

        state.weatherStatus?.let {
            Text(
                it,
                color = AuraColors.TextDim,
                style = AuraType.bodySmall,
                modifier = Modifier.padding(vertical = AuraSpace.xs),
            )
        }
    }

    Text(
        "Prévisions d'Open-Meteo, service gratuit sans compte. Seule une position arrondie à environ 1 km " +
            "(ou la ville choisie) est envoyée, rien d'autre. Toucher la météo sous l'heure affiche les prévisions.",
        color = AuraColors.TextFaint,
        style = AuraType.caption,
        modifier = Modifier.padding(top = AuraSpace.xs, bottom = AuraSpace.s),
    )
}

/** Recherche de ville : résultats à mesure de la saisie (avec un court délai), touche pour choisir. */
@Composable
private fun CitySearch(actions: LauncherActions, onPicked: (WeatherPlace) -> Unit) {
    val focus = LocalFocusManager.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<WeatherPlace>?>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    // Incrémenté par la touche « Rechercher » du clavier pour relancer sans attendre.
    var trigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(query, trigger) {
        val q = query.trim()
        if (q.length < 2) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        if (trigger == 0) delay(400)
        searching = true
        results = actions.searchWeatherCity(q)
        searching = false
    }

    val shape = auraShape(AuraRadius.m)
    BasicTextField(
        value = query,
        onValueChange = {
            query = it
            trigger = 0
        },
        singleLine = true,
        textStyle = AuraType.body.copy(color = AuraColors.Text),
        cursorBrush = SolidColor(AuraColors.Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { trigger++ }),
        modifier = Modifier.fillMaxWidth().padding(vertical = AuraSpace.s),
        decorationBox = { inner ->
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(AuraColors.Surface)
                    .border(1.dp, AuraColors.Hairline, shape)
                    .heightIn(min = 52.dp)
                    .padding(horizontal = AuraSpace.l),
            ) {
                if (query.isEmpty()) {
                    Text("Nom de la ville", color = AuraColors.TextFaint, style = AuraType.body)
                }
                inner()
            }
        },
    )

    val list = results
    when {
        searching -> Hint("Recherche…")
        list == null -> Hint("Pas de connexion : réessaie dans un instant.")
        list.isEmpty() && query.trim().length >= 2 -> Hint("Aucune ville trouvée.")
        else -> list.forEach { place ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(role = Role.Button) {
                        focus.clearFocus()
                        onPicked(place)
                    }
                    .padding(vertical = AuraSpace.s),
            ) {
                Column(Modifier.weight(1f).padding(end = AuraSpace.l)) {
                    Text(place.name, color = AuraColors.Text, style = AuraType.body)
                    if (place.detail.isNotBlank()) {
                        Text(
                            place.detail,
                            color = AuraColors.TextDim,
                            style = AuraType.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                ChevronGlyph(AuraColors.TextFaint)
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = AuraColors.TextDim, style = AuraType.bodySmall, modifier = Modifier.padding(vertical = AuraSpace.s))
}

/** Choix exclusif en pastilles, la sélection cerclée de lavande (comme « Taille du texte »). */
@Composable
private fun Pills(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = AuraSpace.m)) {
        Text(label, color = AuraColors.Text, style = AuraType.body.copy(fontSize = AuraType.date.fontSize))
        Spacer(Modifier.height(AuraSpace.m))
        Row(horizontalArrangement = Arrangement.spacedBy(AuraSpace.s)) {
            options.forEachIndexed { index, name ->
                val isSelected = index == selected
                val shape = auraShape(AuraRadius.s)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .clip(shape)
                        .background(if (isSelected) AuraColors.Accent.copy(alpha = 0.14f) else AuraColors.Surface)
                        .border(1.dp, if (isSelected) AuraColors.Accent else AuraColors.Hairline, shape)
                        .clickable(role = Role.RadioButton) { onSelect(index) }
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

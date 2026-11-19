package com.aura.launcher.ui.settings

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aura.launcher.DaySet
import com.aura.launcher.HomeProfile
import com.aura.launcher.LauncherActions
import com.aura.launcher.LauncherState
import com.aura.launcher.ScreenTime
import com.aura.launcher.TimeWindow
import com.aura.launcher.formatClock
import com.aura.launcher.formatClockAt
import com.aura.launcher.ui.AuraColors
import com.aura.launcher.ui.AuraRadius
import com.aura.launcher.ui.AuraSpace
import com.aura.launcher.ui.AuraType
import com.aura.launcher.ui.SectionTitle
import com.aura.launcher.ui.auraShape
import com.aura.launcher.ui.auraSwitchColors
import java.util.Calendar

/*
 * --- Concentration / Profils d'accueil / Bilan de la semaine --- trois sections des réglages.
 */

/** Section « Concentration » : apps mises de côté, plage horaire, lancement à la main. */
@Composable
fun FocusSection(state: LauncherState, actions: LauncherActions) {
    var pickingApps by remember { mutableStateOf(false) }
    val focus = state.focus

    SectionTitle("Concentration")
    Caption(
        "Pendant la concentration, les apps choisies disparaissent de l'accueil et de la liste. " +
            "Elles restent trouvables par la recherche, derrière une pause de 5 secondes.",
    )
    val count = focus.apps.size
    Line(
        "Apps mises de côté",
        if (count == 0) "Aucune pour l'instant" else state.apps.filter { it.key in focus.apps }
            .joinToString(", ") { it.label }.ifEmpty { "$count app(s)" },
    ) { pickingApps = true }

    if (state.focusActive && count > 0) {
        Line(
            "En cours" + (state.focusEndsAt?.let { " jusqu'à ${formatClockAt(it)}" } ?: ""),
            "Toucher pour arrêter",
        ) { actions.stopFocus() }
    } else {
        Line("Lancer maintenant…", "30 min, 1 h, 2 h ou 4 h") { state.focusDialogOpen = true }
    }

    Toggle("Tous les jours aux mêmes heures", focus.scheduled) {
        actions.setFocusSchedule(it, focus.window)
    }
    if (focus.scheduled) {
        WindowRows(focus.window) { actions.setFocusSchedule(true, it) }
    }

    if (pickingApps) {
        AppsPicker(state, selected = focus.apps, onToggle = { app, on -> actions.setFocusApp(app, on) }) {
            pickingApps = false
        }
    }
}

/** Section « Profils d'accueil » : Perso et Travail, chacun avec ses favoris. */
@Composable
fun ProfileSection(state: LauncherState, actions: LauncherActions) {
    val p = state.profiles
    SectionTitle("Profils d'accueil")
    Toggle("Deux accueils : Perso et Travail", p.enabled) { actions.setProfiles(p.copy(enabled = it)) }
    if (!p.enabled) {
        Caption("Chaque profil garde ses propres favoris. On passe de l'un à l'autre en touchant son nom sous l'heure, ou par un geste.")
        return
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(AuraSpace.s),
        modifier = Modifier.padding(vertical = AuraSpace.s),
    ) {
        listOf(HomeProfile.PERSO, HomeProfile.TRAVAIL).forEach { profile ->
            Pill(HomeProfile.label(profile), state.homeProfile == profile) { actions.switchProfile(profile) }
        }
    }
    Caption(
        "Les favoris épinglés ou retirés vont dans le profil affiché. Toucher son nom sous l'heure pour changer.",
    )
    Toggle("Passer sur Travail automatiquement", p.auto) { actions.setProfiles(p.copy(auto = it)) }
    if (p.auto) {
        WindowRows(p.window) { actions.setProfiles(p.copy(window = it)) }
        Caption("En dehors de ces heures, l'accueil revient sur Perso. Un changement à la main tient jusqu'au prochain changement d'horaire.")
    }
}

/** Section « Bilan de la semaine » : temps passé chaque jour dans les apps suivies. */
@Composable
fun WeeklyReport(state: LauncherState) {
    val context = LocalContext.current
    SectionTitle("Bilan de la semaine")
    val tracked = remember(state.appLimits, state.limitedApps, state.focus.apps, state.settingsOpen) {
        ScreenTime.tracked(context)
    }
    if (tracked.isEmpty()) {
        Caption("Limite une app ou mets-la de côté pour la concentration : le temps passé dedans apparaîtra ici.")
        return
    }
    val now = System.currentTimeMillis()
    // 7 derniers jours, du plus ancien à aujourd'hui.
    val days = (6 downTo 0).map { ago -> tracked.associateWith { ScreenTime.usedOn(it, ago, now) } }
    val totals = days.map { it.values.sum() }
    val week = totals.sum()

    Text(
        "${formatDuration(week)} cette semaine · ${formatDuration(week / 7)} par jour en moyenne",
        color = AuraColors.Text,
        style = AuraType.body,
        modifier = Modifier.padding(vertical = AuraSpace.xs),
    )
    WeekChart(totals, now)

    val names = state.apps.associate { it.component.packageName to it.label }
    tracked.map { pkg -> pkg to days.sumOf { it[pkg] ?: 0L } }
        .sortedByDescending { it.second }
        .forEach { (pkg, ms) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
            ) {
                Text(
                    names[pkg] ?: pkg,
                    color = AuraColors.Text,
                    style = AuraType.row,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(formatDuration(ms), color = AuraColors.TextDim, style = AuraType.bodySmall)
            }
        }
    Caption(
        if (state.hasLockAccess) "Compté sur le téléphone par le service « Aura – verrouillage », rien n'est envoyé ailleurs."
        else "Le temps n'est compté que si le service « Aura – verrouillage » est activé (section Gestes).",
    )
}

/** Sept barres, une par jour ; celle d'aujourd'hui en couleur pleine. */
@Composable
private fun WeekChart(totals: List<Long>, now: Long) {
    val accent = AuraColors.Accent
    val track = AuraColors.Hairline
    val max = (totals.maxOrNull() ?: 0L).coerceAtLeast(60_000L)
    val letters = (6 downTo 0).map { ago ->
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_MONTH, -ago)
        }
        "DLMMJVS"[cal.get(Calendar.DAY_OF_WEEK) - 1].toString()
    }
    Column(Modifier.fillMaxWidth().padding(vertical = AuraSpace.s)) {
        Canvas(Modifier.fillMaxWidth().height(96.dp)) {
            val slot = size.width / 7f
            val barWidth = slot * 0.46f
            val radius = CornerRadius(barWidth / 3f)
            totals.forEachIndexed { i, ms ->
                val left = slot * i + (slot - barWidth) / 2f
                drawRoundRect(track, Offset(left, 0f), Size(barWidth, size.height), radius, alpha = 0.35f)
                val h = size.height * (ms.toFloat() / max)
                if (h > 0f) {
                    drawRoundRect(
                        accent,
                        Offset(left, size.height - h),
                        Size(barWidth, h),
                        radius,
                        alpha = if (i == totals.lastIndex) 1f else 0.5f,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = AuraSpace.xs)) {
            letters.forEachIndexed { i, letter ->
                Text(
                    letter,
                    color = if (i == letters.lastIndex) AuraColors.AccentLight else AuraColors.TextFaint,
                    style = AuraType.caption,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** « moins d'1 min », « 12 min », « 1 h 05 ». */
private fun formatDuration(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes < 1 -> "moins d'1 min"
        minutes < 60 -> "$minutes min"
        else -> "${minutes / 60} h %02d".format(minutes % 60)
    }
}

// --- Éléments communs ---

/** Début, fin et jours d'une plage horaire. */
@Composable
private fun WindowRows(window: TimeWindow, onChange: (TimeWindow) -> Unit) {
    var picking by remember { mutableStateOf<String?>(null) }
    Row(horizontalArrangement = Arrangement.spacedBy(AuraSpace.s), modifier = Modifier.padding(vertical = AuraSpace.xs)) {
        Pill("De ${formatClock(window.start)}", false) { picking = "start" }
        Pill("à ${formatClock(window.end)}", false) { picking = "end" }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(AuraSpace.s), modifier = Modifier.fillMaxWidth().padding(vertical = AuraSpace.xs)) {
        DaySet.entries.forEach { days ->
            Pill(days.short, window.days == days, Modifier.weight(1f)) { onChange(window.copy(days = days)) }
        }
    }
    picking?.let { which ->
        TimePickDialog(
            title = if (which == "start") "Début" else "Fin",
            current = if (which == "start") window.start else window.end,
            onPick = { minutes ->
                onChange(if (which == "start") window.copy(start = minutes) else window.copy(end = minutes))
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

/** Choix d'une heure, par demi-heure. */
@Composable
private fun TimePickDialog(title: String, current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val slots = (0 until 48).map { it * 30 }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (current / 30 - 2).coerceIn(0, 47))
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AuraColors.Surface,
        titleContentColor = AuraColors.Text,
        shape = auraShape(AuraRadius.xl),
        title = { Text(title, style = AuraType.screenTitle.copy(fontSize = AuraType.favorite.fontSize)) },
        text = {
            LazyColumn(state = listState, modifier = Modifier.heightIn(max = 360.dp)) {
                items(slots) { minutes ->
                    Text(
                        formatClock(minutes),
                        color = if (minutes == current) AuraColors.AccentLight else AuraColors.Text,
                        style = AuraType.row,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(auraShape(AuraRadius.s))
                            .clickable(role = Role.RadioButton) { onPick(minutes) }
                            .padding(horizontal = AuraSpace.s, vertical = AuraSpace.m),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Annuler", color = AuraColors.TextDim, style = AuraType.button) }
        },
    )
}

/** Liste des apps à cocher. */
@Composable
private fun AppsPicker(
    state: LauncherState,
    selected: Set<String>,
    onToggle: (com.aura.launcher.AppEntry, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AuraColors.Surface,
        titleContentColor = AuraColors.Text,
        shape = auraShape(AuraRadius.xl),
        title = { Text("Apps à mettre de côté", style = AuraType.screenTitle.copy(fontSize = AuraType.favorite.fontSize)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(state.visibleApps, key = { it.key }) { app ->
                    val on = app.key in selected
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(auraShape(AuraRadius.s))
                            .clickable(role = Role.Checkbox) { onToggle(app, !on) }
                            .padding(horizontal = AuraSpace.s, vertical = AuraSpace.m),
                    ) {
                        Text(
                            app.label,
                            color = if (on) AuraColors.AccentLight else AuraColors.Text,
                            style = AuraType.row,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (on) Text("✓", color = AuraColors.Accent, style = AuraType.row)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Terminé", color = AuraColors.Accent, style = AuraType.button) }
        },
    )
}

@Composable
private fun Line(title: String, detail: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = AuraSpace.s),
    ) {
        Text(title, color = AuraColors.Text, style = AuraType.body.copy(fontSize = AuraType.date.fontSize))
        Text(detail, color = AuraColors.Accent, style = AuraType.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
private fun Pill(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = auraShape(AuraRadius.s)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(if (selected) AuraColors.Accent.copy(alpha = 0.14f) else AuraColors.Surface)
            .border(1.dp, if (selected) AuraColors.Accent else AuraColors.Hairline, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = AuraSpace.m),
    ) {
        Text(label, color = if (selected) AuraColors.AccentLight else AuraColors.TextDim, style = AuraType.label, maxLines = 1)
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text,
        color = AuraColors.TextFaint,
        style = AuraType.caption,
        modifier = Modifier.padding(vertical = AuraSpace.xs),
    )
    Spacer(Modifier.height(AuraSpace.xxs))
}

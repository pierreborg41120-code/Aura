package com.aura.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aura.launcher.BuildConfig
import com.aura.launcher.LauncherActions
import com.aura.launcher.LauncherState

@Composable
fun SettingsScreen(state: LauncherState, actions: LauncherActions) {
    Column(
        Modifier
            .fillMaxSize()
            .themeBackdrop()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AuraSpace.gutter, vertical = AuraSpace.xl),
    ) {
        Text("Réglages", color = AuraColors.Text, style = AuraType.screenTitle)
        Spacer(Modifier.height(AuraSpace.l))
        // --- Apparence ---
        LinkRow("Apparence") { actions.openAppearanceEditor() }
        com.aura.launcher.ui.settings.HomeSettings(state, actions) // --- Accueil ---

        ToggleRow("Afficher les icônes", state.showIcons, actions::setShowIcons)
        ToggleRow(
            "Ouvrir automatiquement l'app quand la recherche n'en trouve qu'une",
            state.autoLaunch,
            actions::setAutoLaunch,
        )

        ChoiceRow(
            label = "Taille du texte",
            options = listOf("Petit" to 0.85f, "Normal" to 1f, "Grand" to 1.15f),
            selected = state.textScale,
            onSelect = actions::setTextScale,
        )

        com.aura.launcher.ui.settings.DrawerSettings(state, actions) // --- Tiroir et recherche ---
        SectionTitle("Notifications et musique")
        if (state.hasNotificationAccess) {
            ToggleRow(
                "Aperçu des notifications sous les favoris",
                state.showNotificationPreviews,
                actions::setShowNotificationPreviews,
            )
            com.aura.launcher.ui.settings.NotificationSettings(state, actions) // --- Réponses aux notifications ---
            Diagnostics(state)
            LinkRow("Relancer le service de notifications") { actions.restartNotificationService() }
            LinkRow("Ouvrir l'accès aux notifications d'Android") { actions.openNotificationAccessSettings() }
        } else {
            PermissionRow(
                title = "Activer l'accès aux notifications",
                description = "Affiche un point sur les apps qui ont des notifications, un aperçu sous tes favoris " +
                    "et un mini-lecteur quand de la musique joue. Rien ne quitte ton téléphone.",
                onClick = actions::openNotificationAccessSettings,
            )
        }

        SectionTitle("Gestes")
        if (state.hasLockAccess) {
            Text(
                "Double-toucher l'accueil verrouille l'écran ✓",
                color = AuraColors.TextDim,
                style = AuraType.body,
                modifier = Modifier.padding(vertical = AuraSpace.m),
            )
        } else {
            PermissionRow(
                title = "Double-toucher pour verrouiller",
                description = "Dans la page qui s'ouvre, choisis « Aura – verrouillage » et active-le. " +
                    "Aura ne lit pas le contenu de ton écran.",
                onClick = actions::openLockAccessSettings,
            )
        }

        if (!state.hasNotificationAccess || !state.hasLockAccess) {
            Text(
                "Option grisée ou « paramètre restreint » ? Va dans Paramètres → Applications → Aura, " +
                    "touche ⋮ en haut à droite, puis « Autoriser les paramètres restreints », et réessaie.",
                color = AuraColors.TextFaint,
                style = AuraType.caption,
                modifier = Modifier.padding(top = AuraSpace.xs, bottom = AuraSpace.s),
            )
        }

        SectionTitle("Système")
        LinkRow("Assistant de configuration") { actions.openOnboarding() }
        LinkRow("Changer le fond d'écran") { actions.openWallpaperPicker() }
        LinkRow("Choisir le launcher par défaut") { actions.openDefaultLauncherSettings() }

        SectionTitle("Apps masquées")

        val byKey = state.apps.associateBy { it.key }
        val hidden = state.hidden.mapNotNull { byKey[it] }.sortedBy { it.searchKey }
        if (hidden.isEmpty()) {
            Text(
                "Aucune. Appui long sur une app dans la liste, puis « Masquer ».",
                color = AuraColors.TextDim,
                style = AuraType.body,
                modifier = Modifier.padding(vertical = AuraSpace.s),
            )
        } else {
            hidden.forEach { app ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    Text(app.label, color = AuraColors.Text, style = AuraType.row, modifier = Modifier.weight(1f))
                    Text(
                        "Afficher",
                        color = AuraColors.Accent,
                        style = AuraType.label,
                        modifier = Modifier
                            .clip(auraShape(AuraRadius.s))
                            .clickable { actions.setHidden(app.key, false) }
                            .padding(horizontal = AuraSpace.m, vertical = AuraSpace.s + AuraSpace.xxs),
                    )
                }
            }
        }

        // --- Sous l'heure : batterie + agenda ---
        SectionTitle("Sous l'heure")
        ToggleRow("Batterie", state.showBattery, actions::setShowBattery)
        ToggleRow(
            "Prochain rendez-vous",
            state.showNextEvent && state.hasCalendarAccess,
            actions::setShowNextEvent,
        )
        Text(
            "Affiche le prochain évènement des 24 heures à venir. Aura lit ton agenda sur le téléphone, " +
                "rien n'est envoyé ailleurs.",
            color = AuraColors.TextFaint,
            style = AuraType.caption,
            modifier = Modifier.padding(bottom = AuraSpace.s),
        )
        com.aura.launcher.ui.settings.WeatherSettings(state, actions) // --- Météo ---

        Spacer(Modifier.height(AuraSpace.xxl + AuraSpace.xs))
        Text(
            "Aura ${BuildConfig.VERSION_NAME} · gratuit, sans pub, sans pistage",
            color = AuraColors.TextFaint,
            style = AuraType.caption,
        )
        Spacer(Modifier.height(AuraSpace.m))
        LinkRow("Fermer") { state.settingsOpen = false }
    }
}

/** Ligne de réglage : libellé à gauche, interrupteur aux couleurs d'Aura à droite. */
@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
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

/** Ligne qui ouvre une page ou lance une action : libellé et petit chevron dessiné. */
@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
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

/** Titre de section : lavande, graisse moyenne, avec l'espace qui le sépare de la section précédente. */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = AuraColors.Accent,
        style = AuraType.label,
        modifier = Modifier.padding(top = AuraSpace.xl + AuraSpace.xs, bottom = AuraSpace.xs),
    )
}

@Composable
private fun PermissionRow(title: String, description: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = AuraSpace.m),
    ) {
        Column(Modifier.weight(1f).padding(end = AuraSpace.l)) {
            Text(title, color = AuraColors.Text, style = AuraType.body.copy(fontSize = AuraType.date.fontSize))
            Spacer(Modifier.height(AuraSpace.xxs))
            Text(description, color = AuraColors.TextDim, style = AuraType.bodySmall)
        }
        ChevronGlyph(AuraColors.Accent)
    }
}

/** Choix exclusif (taille du texte) : options en pastilles, la sélection cerclée de lavande. */
@Composable
private fun ChoiceRow(
    label: String,
    options: List<Pair<String, Float>>,
    selected: Float,
    onSelect: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = AuraSpace.m)) {
        Text(label, color = AuraColors.Text, style = AuraType.body.copy(fontSize = AuraType.date.fontSize))
        Row(
            horizontalArrangement = Arrangement.spacedBy(AuraSpace.s),
            modifier = Modifier.padding(top = AuraSpace.m),
        ) {
            options.forEach { (name, value) ->
                val isSelected = kotlin.math.abs(value - selected) < 0.01f
                val shape = auraShape(AuraRadius.s)
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

/** Petit état des lieux pour comprendre pourquoi les notifications ou le lecteur n'apparaissent pas. */
@Composable
private fun Diagnostics(state: LauncherState) {
    fun line(ok: Boolean, text: String) = (if (ok) "✓ " else "✗ ") + text
    val lines = listOf(
        line(true, "Accès aux notifications accordé"),
        line(
            state.notificationServiceConnected,
            if (state.notificationServiceConnected) "Service de notifications connecté"
            else "Service de notifications pas connecté",
        ),
        line(
            state.notifications.isNotEmpty(),
            "Apps avec des notifications : ${state.notifications.size}",
        ),
        line(
            state.mediaSessionsAllowed,
            if (state.mediaSessionsAllowed) "Lecture multimédia accessible"
            else "Lecture multimédia refusée par Android (secours via les notifications)",
        ),
        line(state.media != null, "Musique détectée : ${state.media?.title ?: "aucune"}"),
    )
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = AuraSpace.s)
            .clip(auraShape(AuraRadius.m))
            .background(AuraColors.Surface)
            .border(1.dp, AuraColors.Hairline, auraShape(AuraRadius.m))
            .padding(AuraSpace.l),
    ) {
        Text("Diagnostic", color = AuraColors.TextDim, style = AuraType.label)
        Spacer(Modifier.height(AuraSpace.xs))
        lines.forEach { Text(it, color = AuraColors.Text, style = AuraType.bodySmall) }
    }
}

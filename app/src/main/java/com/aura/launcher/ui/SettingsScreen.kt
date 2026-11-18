package com.aura.launcher.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.launcher.BuildConfig
import com.aura.launcher.LauncherActions
import com.aura.launcher.LauncherState

@Composable
fun SettingsScreen(state: LauncherState, actions: LauncherActions) {
    Column(
        Modifier
            .fillMaxSize()
            .background(AuraColors.DrawerScrim)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        Text("Réglages", color = AuraColors.Text, fontSize = 32.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(24.dp))

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

        Spacer(Modifier.height(20.dp))
        SectionTitle("Notifications et musique")
        if (state.hasNotificationAccess) {
            ToggleRow(
                "Aperçu des notifications sous les favoris",
                state.showNotificationPreviews,
                actions::setShowNotificationPreviews,
            )
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

        Spacer(Modifier.height(20.dp))
        SectionTitle("Gestes")
        if (state.hasLockAccess) {
            Text(
                "Double-toucher l'accueil verrouille l'écran ✓",
                color = AuraColors.TextDim,
                fontSize = 15.sp,
                modifier = Modifier.padding(vertical = 12.dp),
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
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
        }

        Spacer(Modifier.height(20.dp))
        SectionTitle("Système")
        LinkRow("Assistant de configuration") { actions.openOnboarding() }
        LinkRow("Changer le fond d'écran") { actions.openWallpaperPicker() }
        LinkRow("Choisir le launcher par défaut") { actions.openDefaultLauncherSettings() }

        Spacer(Modifier.height(28.dp))
        SectionTitle("Apps masquées")

        val byKey = state.apps.associateBy { it.key }
        val hidden = state.hidden.mapNotNull { byKey[it] }.sortedBy { it.searchKey }
        if (hidden.isEmpty()) {
            Text(
                "Aucune. Appui long sur une app dans la liste, puis « Masquer ».",
                color = AuraColors.TextDim,
                fontSize = 15.sp,
            )
        } else {
            hidden.forEach { app ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                ) {
                    Text(app.label, color = AuraColors.Text, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    Text(
                        "Afficher",
                        color = AuraColors.Accent,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .clickable { actions.setHidden(app.key, false) }
                            .padding(8.dp),
                    )
                }
            }
        }

        // --- Sous l'heure : batterie + agenda ---
        Spacer(Modifier.height(28.dp))
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
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Spacer(Modifier.height(36.dp))
        Text(
            "Aura ${BuildConfig.VERSION_NAME} · gratuit, sans pub, sans pistage",
            color = AuraColors.TextFaint,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(12.dp))
        LinkRow("Fermer") { state.settingsOpen = false }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 12.dp),
    ) {
        Text(label, color = AuraColors.Text, fontSize = 17.sp, modifier = Modifier.weight(1f).padding(end = 16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = AuraColors.Accent),
        )
    }
}

@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = AuraColors.Text,
        fontSize = 17.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = AuraColors.Accent,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun PermissionRow(title: String, description: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(title, color = AuraColors.Text, fontSize = 17.sp)
        Text(description, color = AuraColors.TextDim, fontSize = 14.sp, lineHeight = 19.sp)
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    options: List<Pair<String, Float>>,
    selected: Float,
    onSelect: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(label, color = AuraColors.Text, fontSize = 17.sp)
        Row(Modifier.padding(top = 8.dp)) {
            options.forEach { (name, value) ->
                val isSelected = kotlin.math.abs(value - selected) < 0.01f
                Text(
                    name,
                    color = if (isSelected) AuraColors.Accent else AuraColors.TextDim,
                    fontSize = 16.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .clickable { onSelect(value) }
                        .padding(end = 24.dp, top = 4.dp, bottom = 4.dp),
                )
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
            .padding(vertical = 8.dp)
            .background(AuraColors.Surface, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Text("Diagnostic", color = AuraColors.TextDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        lines.forEach { Text(it, color = AuraColors.Text, fontSize = 14.sp, lineHeight = 20.sp) }
    }
}

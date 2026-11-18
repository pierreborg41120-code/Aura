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

        LinkRow("Changer le fond d'écran") { actions.openWallpaperPicker() }
        LinkRow("Choisir le launcher par défaut") { actions.openDefaultLauncherSettings() }

        Spacer(Modifier.height(28.dp))
        Text("Apps masquées", color = AuraColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

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

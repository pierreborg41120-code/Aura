package com.aura.launcher.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import com.aura.launcher.ui.auraSwitchColors

/**
 * Réglages des réponses aux notifications, affichés dans la section « Notifications et musique ».
 * N'apparaît que si Aura a l'accès aux notifications (sans lui, rien à répondre).
 */
@Composable
fun NotificationSettings(state: LauncherState, actions: LauncherActions) {
    if (!state.hasNotificationAccess) return
    val checked = state.replyFromHome
    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.Switch) { actions.setReplyFromHome(!checked) }
                .padding(vertical = AuraSpace.s),
        ) {
            Text(
                "Répondre depuis l'accueil",
                color = AuraColors.Text,
                style = AuraType.body.copy(fontSize = AuraType.date.fontSize),
                modifier = Modifier.weight(1f).padding(end = AuraSpace.l),
            )
            Switch(checked = checked, onCheckedChange = actions::setReplyFromHome, colors = auraSwitchColors())
        }
        Text(
            "Dans la carte d'une app (glisse un favori vers la droite, ou touche son aperçu), réponds aux " +
                "messages et efface les notifications sans ouvrir l'app.",
            color = AuraColors.TextFaint,
            style = AuraType.caption,
            modifier = Modifier.padding(bottom = AuraSpace.s),
        )
    }
}

package com.aura.launcher.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.aura.launcher.LauncherActions
import com.aura.launcher.ui.AuraColors
import com.aura.launcher.ui.AuraRadius
import com.aura.launcher.ui.AuraSpace
import com.aura.launcher.ui.AuraType
import com.aura.launcher.ui.LinkRow
import com.aura.launcher.ui.SectionTitle
import com.aura.launcher.ui.auraShape

/**
 * --- Sauvegarde --- exporter tous les réglages dans un fichier, ou les restaurer depuis ce fichier.
 * Rien ne quitte le téléphone : c'est l'utilisateur qui choisit où enregistrer le fichier.
 */
@Composable
fun BackupSettings(actions: LauncherActions) {
    var confirmImport by remember { mutableStateOf(false) }

    SectionTitle("Sauvegarde")
    LinkRow("Exporter mes réglages") { actions.exportBackup() }
    LinkRow("Importer une sauvegarde") { confirmImport = true }
    Text(
        "Favoris, dossiers, noms, apps masquées, apparence et réglages, dans un fichier que tu ranges où tu veux " +
            "(Téléchargements, Drive…). Les widgets ne sont pas sauvegardés : Android les lie à ce téléphone.",
        color = AuraColors.TextFaint,
        style = AuraType.caption,
        modifier = Modifier.padding(top = AuraSpace.xs, bottom = AuraSpace.s),
    )

    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            containerColor = AuraColors.Surface,
            titleContentColor = AuraColors.Text,
            textContentColor = AuraColors.TextDim,
            shape = auraShape(AuraRadius.xl),
            title = { Text("Importer une sauvegarde ?", style = AuraType.screenTitle.copy(fontSize = AuraType.favorite.fontSize)) },
            text = {
                Text(
                    "Tes réglages actuels seront remplacés par ceux du fichier, puis Aura redémarrera.",
                    style = AuraType.body,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmImport = false
                    actions.importBackup()
                }) { Text("Choisir le fichier", color = AuraColors.Accent, style = AuraType.button) }
            },
            dismissButton = {
                TextButton(onClick = { confirmImport = false }) {
                    Text("Annuler", color = AuraColors.TextDim, style = AuraType.button)
                }
            },
        )
    }
}

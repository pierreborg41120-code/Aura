package com.aura.launcher.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.aura.launcher.AppEntry

/** Boîte « Renommer », aux couleurs et aux rayons d'Aura. */
@Composable
fun RenameDialog(app: AppEntry, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var text by remember(app.key) { mutableStateOf(app.label) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AuraColors.Surface,
        titleContentColor = AuraColors.Text,
        textContentColor = AuraColors.TextDim,
        shape = auraShape(AuraRadius.xl),
        title = { Text("Renommer", style = AuraType.screenTitle.copy(fontSize = AuraType.favorite.fontSize)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = AuraType.row,
                placeholder = { Text(app.originalLabel, style = AuraType.row) },
                shape = auraShape(AuraRadius.s),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = AuraColors.Text,
                    unfocusedTextColor = AuraColors.Text,
                    focusedBorderColor = AuraColors.Accent,
                    unfocusedBorderColor = AuraColors.Hairline,
                    cursorColor = AuraColors.Accent,
                    focusedPlaceholderColor = AuraColors.TextFaint,
                    unfocusedPlaceholderColor = AuraColors.TextFaint,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onRename(text) }) {
                Text("Renommer", color = AuraColors.Accent, style = AuraType.button)
            }
        },
        dismissButton = {
            if (app.isRenamed) {
                TextButton(onClick = { onRename(app.originalLabel) }) {
                    Text("Nom d'origine", color = AuraColors.TextDim, style = AuraType.button)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Annuler", color = AuraColors.TextDim, style = AuraType.button)
                }
            }
        },
    )
}

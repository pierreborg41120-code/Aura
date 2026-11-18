package com.aura.launcher.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.aura.launcher.AppEntry

@Composable
fun RenameDialog(app: AppEntry, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var text by remember(app.key) { mutableStateOf(app.label) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renommer") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text(app.originalLabel) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onRename(text) }) { Text("OK") }
        },
        dismissButton = {
            if (app.isRenamed) {
                TextButton(onClick = { onRename(app.originalLabel) }) { Text("Nom d'origine") }
            } else {
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
        },
    )
}

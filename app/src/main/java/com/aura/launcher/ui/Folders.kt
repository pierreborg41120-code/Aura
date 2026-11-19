package com.aura.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aura.launcher.AppEntry
import com.aura.launcher.Folder
import com.aura.launcher.HomeAlignment
import com.aura.launcher.HomeItem
import com.aura.launcher.LauncherActions
import com.aura.launcher.LauncherState

/*
 * --- Dossiers --- ligne de dossier sur l'accueil, fenêtre du dossier ouvert, et boîtes
 * « Ajouter à un dossier » / « Renommer le dossier ».
 */

/**
 * Un dossier sur l'accueil : aperçu de ses (jusqu'à 4) icônes, nom, point si l'une de ses apps a une
 * notification. Toucher l'ouvre ; appui long : menu (monter, descendre, renommer, supprimer).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderRow(
    group: HomeItem.Group,
    state: LauncherState,
    actions: LauncherActions,
    showIcon: Boolean,
    iconSize: Dp,
    textStyle: TextStyle,
    alignment: HomeAlignment,
    menu: List<MenuAction>,
) {
    val iconShown = showIcon && alignment != HomeAlignment.CENTER
    val alignEnd = alignment == HomeAlignment.END
    val badge = group.apps.any { state.notificationsFor(it) != null }
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = when (alignment) {
                HomeAlignment.START -> Arrangement.Start
                HomeAlignment.CENTER -> Arrangement.Center
                HomeAlignment.END -> Arrangement.End
            },
            modifier = Modifier
                .fillMaxWidth()
                .clip(auraShape(AuraRadius.s))
                .combinedClickable(
                    onClickLabel = "Ouvrir le dossier",
                    onClick = { state.openFolder = group.folder.id },
                    onLongClick = {
                        haptics.auraHaptic(HapticFeedbackType.LongPress)
                        menuOpen = true
                    },
                )
                .padding(vertical = AuraSpace.s + AuraSpace.xxs),
        ) {
            if (iconShown && !alignEnd) {
                FolderGlyph(group.apps, actions, iconSize)
                Spacer(Modifier.width(AuraSpace.l))
            }
            if (badge && alignEnd) {
                HaloDot(size = 12.dp)
                Spacer(Modifier.width(AuraSpace.s))
            }
            Text(
                text = group.folder.name,
                style = textStyle,
                color = AuraColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (badge && !alignEnd) {
                Spacer(Modifier.width(AuraSpace.s))
                HaloDot(size = 12.dp)
            }
            if (iconShown && alignEnd) {
                Spacer(Modifier.width(AuraSpace.l))
                FolderGlyph(group.apps, actions, iconSize)
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            menu.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label, style = AuraType.body) },
                    onClick = {
                        menuOpen = false
                        item.onClick()
                    },
                )
            }
        }
    }
}

/** Aperçu du dossier : grille 2 × 2 des premières icônes, dans un cadre de la forme du thème. */
@Composable
private fun FolderGlyph(apps: List<AppEntry>, actions: LauncherActions, size: Dp) {
    val shape = auraShape(AuraRadius.s)
    val inner = (size - 6.dp) / 2
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(AuraColors.Surface.copy(alpha = 0.7f))
            .border(1.dp, AuraColors.Hairline, shape)
            .padding(2.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            apps.take(4).chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    row.forEach { AppIcon(it, actions, inner) }
                }
            }
        }
    }
}

/** Fenêtre du dossier ouvert, au centre, par-dessus un voile ; toucher le voile ou Retour la ferme. */
@Composable
fun FolderSheet(state: LauncherState, actions: LauncherActions) {
    val group = state.openFolder?.let { id ->
        state.homeItems.filterIsInstance<HomeItem.Group>().firstOrNull { it.folder.id == id }
    }
    AnimatedVisibility(
        visible = group != null,
        enter = fadeIn(tween(AuraMotion.standard)) + scaleIn(tween(AuraMotion.gentle, easing = AuraMotion.enter), initialScale = 0.94f),
        exit = fadeOut(tween(AuraMotion.quick)),
    ) {
        // Garde le dernier dossier affiché pendant l'animation de sortie.
        val shown = remember { mutableStateOf(group) }
        if (group != null) shown.value = group
        val g = shown.value ?: return@AnimatedVisibility
        BackHandler(enabled = state.openFolder != null) { state.openFolder = null }

        Box(
            Modifier
                .fillMaxSize()
                .background(AuraColors.SheetScrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { state.openFolder = null }
                .systemBarsPadding()
                .padding(AuraSpace.gutter),
            contentAlignment = Alignment.Center,
        ) {
            val shape = auraShape(AuraRadius.xl)
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .clip(shape)
                    .background(AuraColors.Surface)
                    .border(1.dp, AuraColors.Hairline, shape)
                    // Les touchers dans la carte ne ferment pas le dossier.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {}
                    .padding(AuraSpace.xl),
            ) {
                Text(
                    g.folder.name,
                    color = AuraColors.Text,
                    style = AuraType.screenTitle.copy(fontSize = AuraType.favorite.fontSize).homeFont(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = AuraSpace.m),
                )
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    g.apps.forEach { app ->
                        AppRow(
                            app = app,
                            actions = object : LauncherActions by actions {
                                // Lancer une app referme le dossier.
                                override fun launch(app: AppEntry) {
                                    state.openFolder = null
                                    actions.launch(app)
                                }
                            },
                            showIcon = state.showIcons,
                            iconSize = (30 * state.textScale).dp,
                            textStyle = AuraType.row.scaled(state.textScale).homeFont(),
                            badge = state.notificationsFor(app) != null,
                            menu = listOf(
                                MenuAction("Retirer du dossier") { actions.removeFromFolder(app, g.folder.id) },
                                MenuAction("Renommer") { state.renaming = app },
                                MenuAction("Infos de l'app") { actions.openAppInfo(app) },
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** « Ajouter à un dossier » : dossiers existants, ou nom d'un nouveau dossier. */
@Composable
fun AddToFolderDialog(state: LauncherState, actions: LauncherActions) {
    val app = state.folderPicking ?: return
    var name by remember(app.key) { mutableStateOf("") }
    val current = state.folders.firstOrNull { app.key in it.apps }
    AlertDialog(
        onDismissRequest = { state.folderPicking = null },
        containerColor = AuraColors.Surface,
        titleContentColor = AuraColors.Text,
        textContentColor = AuraColors.TextDim,
        shape = auraShape(AuraRadius.xl),
        title = { Text("Ranger « ${app.label} »", style = AuraType.screenTitle.copy(fontSize = AuraType.favorite.fontSize)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                state.folders.filter { it.id != current?.id }.forEach { folder ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = AuraSpace.touch)
                            .clip(auraShape(AuraRadius.s))
                            .clickable(role = Role.Button) { actions.addToFolder(app, folder.id, null) }
                            .padding(horizontal = AuraSpace.s),
                    ) {
                        Text(folder.name, color = AuraColors.Text, style = AuraType.row, modifier = Modifier.weight(1f))
                        Text("${folder.apps.size}", color = AuraColors.TextFaint, style = AuraType.caption)
                    }
                }
                Text(
                    "Nouveau dossier",
                    color = AuraColors.Accent,
                    style = AuraType.label,
                    modifier = Modifier.padding(top = AuraSpace.m, bottom = AuraSpace.s),
                )
                FolderNameField(name, placeholder = "Nom du dossier") { name = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { actions.addToFolder(app, null, name) }, enabled = name.isNotBlank()) {
                Text(
                    "Créer",
                    color = if (name.isNotBlank()) AuraColors.Accent else AuraColors.TextFaint,
                    style = AuraType.button,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { state.folderPicking = null }) {
                Text("Annuler", color = AuraColors.TextDim, style = AuraType.button)
            }
        },
    )
}

/** Renommer un dossier. */
@Composable
fun RenameFolderDialog(state: LauncherState, actions: LauncherActions) {
    val folder: Folder = state.renamingFolder ?: return
    var name by remember(folder.id) { mutableStateOf(folder.name) }
    AlertDialog(
        onDismissRequest = { state.renamingFolder = null },
        containerColor = AuraColors.Surface,
        titleContentColor = AuraColors.Text,
        shape = auraShape(AuraRadius.xl),
        title = { Text("Renommer le dossier", style = AuraType.screenTitle.copy(fontSize = AuraType.favorite.fontSize)) },
        text = { FolderNameField(name, placeholder = folder.name) { name = it } },
        confirmButton = {
            TextButton(onClick = { actions.renameFolder(folder.id, name) }, enabled = name.isNotBlank()) {
                Text("Renommer", color = AuraColors.Accent, style = AuraType.button)
            }
        },
        dismissButton = {
            TextButton(onClick = { state.renamingFolder = null }) {
                Text("Annuler", color = AuraColors.TextDim, style = AuraType.button)
            }
        },
    )
}

@Composable
private fun FolderNameField(value: String, placeholder: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = AuraType.row,
        placeholder = { Text(placeholder, style = AuraType.row) },
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
}

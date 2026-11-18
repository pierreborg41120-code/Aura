package com.aura.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.aura.launcher.AppEntry
import com.aura.launcher.LauncherActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Une entrée de menu contextuel (appui long). */
data class MenuAction(val label: String, val onClick: () -> Unit)

/**
 * Une ligne « icône + nom » cliquable, avec un menu sur appui long.
 * [badge] affiche un point (notifications en attente), [subtitle] une ligne d'aperçu sous le nom.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRow(
    app: AppEntry,
    actions: LauncherActions,
    showIcon: Boolean,
    iconSize: Dp,
    fontSize: TextUnit,
    menu: List<MenuAction>,
    modifier: Modifier = Modifier,
    verticalPadding: Dp = 10.dp,
    badge: Boolean = false,
    subtitle: String? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    Box(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { actions.launch(app) },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    },
                )
                .padding(vertical = verticalPadding),
        ) {
            if (showIcon) {
                AppIcon(app, actions, iconSize)
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f, fill = false)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = app.label,
                        fontSize = fontSize,
                        color = AuraColors.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (badge) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .size(7.dp)
                                .background(AuraColors.Accent, CircleShape),
                        )
                    }
                }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = fontSize * 0.58f,
                        color = AuraColors.TextDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            menu.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label) },
                    onClick = {
                        menuOpen = false
                        item.onClick()
                    },
                )
            }
        }
    }
}

@Composable
fun AppIcon(app: AppEntry, actions: LauncherActions, size: Dp) {
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val icon by produceState<ImageBitmap?>(initialValue = null, app.key, sizePx) {
        value = withContext(Dispatchers.IO) { actions.loadIcon(app, sizePx) }
    }
    Box(Modifier.size(size)) {
        icon?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.size(size)) }
    }
}

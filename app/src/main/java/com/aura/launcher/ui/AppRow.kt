package com.aura.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aura.launcher.AppEntry
import com.aura.launcher.LauncherActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Une entrée de menu contextuel (appui long). */
data class MenuAction(val label: String, val onClick: () -> Unit)

/**
 * Une ligne « icône + nom » cliquable, avec un menu sur appui long.
 * [badge] affiche le point « éclipse » (notifications en attente), [subtitle] une ligne d'aperçu.
 * [textStyle] porte la taille (déjà multipliée par le réglage de taille) et, sur l'accueil, l'ombre.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRow(
    app: AppEntry,
    actions: LauncherActions,
    showIcon: Boolean,
    iconSize: Dp,
    textStyle: TextStyle,
    menu: List<MenuAction>,
    modifier: Modifier = Modifier,
    verticalPadding: Dp = AuraSpace.s + AuraSpace.xxs,
    badge: Boolean = false,
    subtitle: String? = null,
    // --- Réponses aux notifications --- toucher l'aperçu (au lieu de lancer l'app), si fourni.
    onSubtitleClick: (() -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    Box(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                // Retour visuel au toucher arrondi, légèrement débordant pour ne pas coller au texte.
                .clip(RoundedCornerShape(AuraRadius.s))
                .combinedClickable(
                    onClick = { actions.launch(app) },
                    onLongClick = {
                        haptics.auraHaptic(HapticFeedbackType.LongPress)
                        menuOpen = true
                    },
                )
                .padding(vertical = verticalPadding),
        ) {
            if (showIcon) {
                AppIcon(app, actions, iconSize)
                Spacer(Modifier.width(AuraSpace.l))
            }
            Column(Modifier.weight(1f, fill = false)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = app.label,
                        style = textStyle,
                        color = AuraColors.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (badge) {
                        Spacer(Modifier.width(AuraSpace.s))
                        HaloDot(size = 12.dp)
                    }
                }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = textStyle.copy(
                            fontSize = textStyle.fontSize * 0.58f,
                            fontWeight = AuraType.bodySmall.fontWeight,
                            letterSpacing = AuraType.bodySmall.letterSpacing,
                        ),
                        color = AuraColors.TextDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(top = AuraSpace.xxs)
                            .then(
                                if (onSubtitleClick != null) {
                                    Modifier
                                        .clip(RoundedCornerShape(AuraRadius.s))
                                        .clickable(onClickLabel = "Voir les notifications", onClick = onSubtitleClick)
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            // --- Raccourcis d'app ---
            ShortcutMenuItems(app, actions) { menuOpen = false }
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

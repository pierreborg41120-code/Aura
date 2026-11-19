package com.aura.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.aura.launcher.LauncherActions
import com.aura.launcher.LauncherState
import com.aura.launcher.VeilLevel

/*
 * --- Apparence --- ce qui se pose sur le fond d'écran : voile et flou.
 */

/**
 * Couleurs du dégradé vertical posé sur le fond d'écran pour un niveau de voile.
 * TODO(thème) : AUCUN, MOYEN, FORT (~45 %). Pour l'instant tous les niveaux donnent « Léger ».
 */
fun wallpaperVeil(level: VeilLevel): List<Color> = AuraColors.WallpaperVeil

/**
 * Flou du fond d'écran pendant que le tiroir est ouvert (réglage blurDrawer, Android 12+).
 * Appelé une fois depuis LauncherScreen ; passe par [LauncherActions.setWallpaperBlur].
 * TODO(thème) : animer le rayon à l'ouverture / fermeture du tiroir.
 */
@Composable
fun DrawerBlurEffect(state: LauncherState, actions: LauncherActions) {
}

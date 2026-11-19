package com.aura.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Color
import com.aura.launcher.LauncherActions
import com.aura.launcher.LauncherState
import com.aura.launcher.VeilLevel
import kotlin.math.roundToInt

/*
 * --- Apparence --- ce qui se pose sur le fond d'écran : voile et flou.
 */

/** Voile « Aucun » : transparent (deux couleurs, le dégradé en exige au moins deux). */
private val VeilNone = listOf(Color.Transparent, Color.Transparent)

/** Voile « Moyen » : ~30 % de noir en moyenne, plus dense en haut et en bas. */
private val VeilMedium = listOf(Color(0x80000000), Color(0x33000000), Color(0x33000000), Color(0x99000000))

/** Voile « Fort » : ~45 % de noir en moyenne, toujours plus dense en haut et en bas. */
private val VeilStrong = listOf(Color(0xA6000000), Color(0x59000000), Color(0x59000000), Color(0xB8000000))

/** Couleurs du dégradé vertical posé sur le fond d'écran pour un niveau de voile. */
fun wallpaperVeil(level: VeilLevel): List<Color> = when (level) {
    VeilLevel.NONE -> VeilNone
    VeilLevel.LIGHT -> AuraColors.WallpaperVeil
    VeilLevel.MEDIUM -> VeilMedium
    VeilLevel.STRONG -> VeilStrong
}

/** Rayon du flou du fond d'écran, tiroir ouvert (pixels). */
private const val DRAWER_BLUR_PX = 70f

/**
 * Flou du fond d'écran pendant que le tiroir est ouvert (réglage blurDrawer, Android 12+).
 * Appelé une fois depuis LauncherScreen ; passe par [LauncherActions.setWallpaperBlur].
 * Le rayon suit l'ouverture du tiroir (animé, sauf si les animations sont coupées) et revient
 * toujours à 0 quand le tiroir se ferme, que le réglage est coupé ou que le flou n'est plus possible.
 */
@Composable
fun DrawerBlurEffect(state: LauncherState, actions: LauncherActions) {
    val enabled = LocalAppearance.current.blurDrawer && state.blurSupported
    val open = enabled && state.drawerOpen
    val reducedMotion = rememberReducedMotion()
    val currentActions by rememberUpdatedState(actions)
    val radius = remember { Animatable(0f) }

    LaunchedEffect(open, enabled) {
        val target = if (open) DRAWER_BLUR_PX else 0f
        if (reducedMotion || !enabled) {
            // Animations coupées, ou réglage désactivé : on saute directement à la valeur.
            radius.snapTo(target)
        } else {
            radius.animateTo(
                target,
                tween(
                    if (open) AuraMotion.gentle else AuraMotion.standard,
                    easing = if (open) AuraMotion.enter else AuraMotion.exit,
                ),
            )
        }
    }
    // Transmet le rayon à la fenêtre (MainActivity ignore les valeurs inchangées).
    LaunchedEffect(Unit) {
        snapshotFlow { radius.value.roundToInt() }.collect { currentActions.setWallpaperBlur(it) }
    }
    DisposableEffect(Unit) {
        onDispose { currentActions.setWallpaperBlur(0) }
    }
}

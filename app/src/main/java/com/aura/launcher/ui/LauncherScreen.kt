package com.aura.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.aura.launcher.LauncherActions
import com.aura.launcher.LauncherState

@Composable
fun LauncherScreen(state: LauncherState, actions: LauncherActions) {
    // Le launcher est la racine : le bouton Retour ne doit jamais le « quitter ».
    BackHandler(enabled = true) {
        when {
            state.renaming != null -> state.renaming = null
            state.onboardingOpen -> actions.finishOnboarding()
            state.settingsOpen -> state.settingsOpen = false
            state.drawerOpen -> state.drawerOpen = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            // Léger dégradé pour que le texte reste lisible sur n'importe quel fond d'écran
            // (plus marqué en haut, sous l'heure, et en bas, sous les favoris ; l'ombre de texte fait le reste).
            .background(
                Brush.verticalGradient(
                    AuraColors.WallpaperVeil,
                ),
            ),
    ) {
        HomeScreen(state, actions)

        AnimatedVisibility(
            visible = state.drawerOpen,
            // Le tiroir monte d'un sixième d'écran en se dévoilant, et repart plus vite qu'il n'est venu.
            enter = slideInVertically(tween(AuraMotion.gentle, easing = AuraMotion.enter)) { it / 6 } +
                fadeIn(tween(AuraMotion.standard)),
            exit = slideOutVertically(tween(AuraMotion.standard, easing = AuraMotion.exit)) { it / 8 } +
                fadeOut(tween(AuraMotion.quick + 40)),
        ) {
            AppDrawer(state, actions)
        }

        AnimatedVisibility(
            visible = state.settingsOpen,
            enter = fadeIn(tween(AuraMotion.standard)) +
                scaleIn(tween(AuraMotion.gentle, easing = AuraMotion.enter), initialScale = 0.97f),
            exit = fadeOut(tween(AuraMotion.quick)),
        ) {
            SettingsScreen(state, actions)
        }

        AnimatedVisibility(
            visible = state.onboardingOpen,
            enter = fadeIn(tween(AuraMotion.gentle)),
            exit = fadeOut(tween(AuraMotion.standard)),
        ) {
            OnboardingScreen(state, actions)
        }

        state.renaming?.let { app ->
            RenameDialog(
                app = app,
                onDismiss = { state.renaming = null },
                onRename = { label ->
                    actions.rename(app, label)
                    state.renaming = null
                },
            )
        }

        // --- Raccourcis d'app ---
        ShortcutsSheet(state, actions)

        // --- Météo ---
        WeatherSheet(state)

        // --- Accueil ---
        AnimatedVisibility(
            visible = state.homeWidgetPicking,
            enter = fadeIn(tween(AuraMotion.standard)) +
                slideInVertically(tween(AuraMotion.gentle, easing = AuraMotion.enter)) { it / 8 },
            exit = fadeOut(tween(AuraMotion.quick)),
        ) {
            HomeWidgetPicker(state, actions)
        }
    }
}

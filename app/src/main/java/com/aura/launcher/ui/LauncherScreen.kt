package com.aura.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
            // Léger dégradé pour que le texte reste lisible sur n'importe quel fond d'écran.
            .background(
                Brush.verticalGradient(
                    listOf(Color(0x66000000), Color(0x22000000), Color(0x88000000)),
                ),
            ),
    ) {
        HomeScreen(state, actions)

        AnimatedVisibility(
            visible = state.drawerOpen,
            enter = slideInVertically { it / 3 } + fadeIn(),
            exit = slideOutVertically { it / 3 } + fadeOut(),
        ) {
            AppDrawer(state, actions)
        }

        AnimatedVisibility(
            visible = state.settingsOpen,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            SettingsScreen(state, actions)
        }

        AnimatedVisibility(
            visible = state.onboardingOpen,
            enter = fadeIn(),
            exit = fadeOut(),
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
    }
}

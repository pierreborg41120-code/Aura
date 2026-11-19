package com.aura.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.Alignment
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.draw.clipToBounds
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
            // --- Limiteur / récap ---
            state.limitChoosing != null -> state.limitChoosing = null
            state.pendingLimited != null -> state.pendingLimited = null
            state.recapOpen -> state.recapOpen = false
            // --- Dossiers ---
            state.folderPicking != null -> state.folderPicking = null
            state.renamingFolder != null -> state.renamingFolder = null
            state.openFolder != null -> state.openFolder = null
            // --- Apparence --- (l'éditeur peut intercepter Retour avant pour proposer d'annuler)
            state.appearanceEditing -> actions.cancelAppearance()
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
                    // --- Apparence --- niveau de voile choisi (ui/WallpaperLook.kt).
                    wallpaperVeil(LocalAppearance.current.veil),
                ),
            ),
    ) {
        // --- Apparence --- flou du fond d'écran à l'ouverture du tiroir.
        DrawerBlurEffect(state, actions)

        // --- Apparence --- pendant l'édition, l'accueil (en aperçu) se resserre au-dessus du panneau,
        // qui monte depuis le bas ; à la fermeture, le panneau redescend et l'accueil reprend sa place.
        val reducedMotion = rememberReducedMotion()
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                HomeScreen(state, actions, preview = state.appearanceEditing)
            }
            AnimatedVisibility(
                visible = state.appearanceEditing,
                enter = if (reducedMotion) EnterTransition.None
                else expandVertically(tween(AuraMotion.gentle, easing = AuraMotion.enter), expandFrom = Alignment.Top) +
                    slideInVertically(tween(AuraMotion.gentle, easing = AuraMotion.enter)) { it } +
                    fadeIn(tween(AuraMotion.standard)),
                exit = if (reducedMotion) ExitTransition.None
                else shrinkVertically(tween(AuraMotion.standard, easing = AuraMotion.exit), shrinkTowards = Alignment.Top) +
                    slideOutVertically(tween(AuraMotion.standard, easing = AuraMotion.exit)) { it } +
                    fadeOut(tween(AuraMotion.standard)),
            ) {
                AppearanceEditor(state, actions)
            }
        }

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

        // --- Récap des notifications ---
        RecapSheet(state, actions)

        // --- Dossiers --- fenêtre du dossier ouvert, puis ses boîtes de dialogue.
        FolderSheet(state, actions)
        AddToFolderDialog(state, actions)
        RenameFolderDialog(state, actions)

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

        // --- Limiteur --- choix de la limite, puis écran de pause par-dessus tout le reste.
        LimitDialog(state, actions)
        FocusDialog(state, actions) // --- Concentration ---
        LimiterPause(state, actions)
    }
}

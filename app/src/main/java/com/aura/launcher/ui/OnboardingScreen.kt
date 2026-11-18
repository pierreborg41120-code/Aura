package com.aura.launcher.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.launcher.LauncherActions
import com.aura.launcher.LauncherState

/** Assistant affiché au premier lancement (et accessible depuis les réglages). */
@Composable
fun OnboardingScreen(state: LauncherState, actions: LauncherActions) {
    val essentialsDone = state.isDefaultHome && state.hasNotificationAccess
    val allDone = essentialsDone && state.hasLockAccess
    val restrictedSettingsApply = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFA0B0B10))
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        Text("Bienvenue dans Aura", color = AuraColors.Text, fontSize = 32.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(8.dp))
        Text(
            "Trois réglages pour en profiter pleinement. Android ne permet pas de les activer à ta place : " +
                "chaque bouton t'emmène au bon endroit, tu n'as plus qu'à activer.",
            color = AuraColors.TextDim,
            fontSize = 15.sp,
            lineHeight = 21.sp,
        )
        Spacer(Modifier.height(24.dp))

        StepCard(
            number = 1,
            done = state.isDefaultHome,
            title = "Faire d'Aura ton écran d'accueil",
            description = "Pour arriver sur Aura quand tu appuies sur Accueil.",
        ) {
            StepButton("Choisir Aura") { actions.requestDefaultHome() }
        }

        StepCard(
            number = 2,
            done = state.hasNotificationAccess,
            title = "Activer l'accès aux notifications",
            description = "Pour les points sur les apps, l'aperçu des messages et le lecteur de musique. " +
                "Rien ne quitte ton téléphone.",
        ) {
            StepButton("Activer l'accès") { actions.openNotificationAccessSettings() }
            if (restrictedSettingsApply) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "Android affiche « L'accès a été refusé » ou l'interrupteur est grisé ? C'est normal pour " +
                        "une app installée hors du Play Store :",
                    color = AuraColors.TextDim,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "1. Touche le bouton ci-dessous pour ouvrir la fiche d'Aura\n" +
                        "2. Touche ⋮ en haut à droite\n" +
                        "3. Choisis « Autoriser les paramètres restreints » et confirme\n" +
                        "4. Reviens ici et touche à nouveau « Activer l'accès »",
                    color = AuraColors.Text,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                )
                Spacer(Modifier.height(10.dp))
                StepButton("Ouvrir la fiche d'Aura", secondary = true) { actions.openAuraAppDetails() }
            }
        }

        StepCard(
            number = 3,
            done = state.hasLockAccess,
            title = "Double-toucher pour verrouiller",
            description = "Facultatif. Dans la page Accessibilité, choisis « Aura – verrouillage » et active-le " +
                "(parfois rangé dans « Applications installées »). Aura ne lit pas ton écran.",
        ) {
            StepButton("Activer", secondary = true) { actions.openLockAccessSettings() }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = when {
                allDone -> "Tout est prêt !"
                essentialsDone -> "L'essentiel est prêt."
                else -> "Tu pourras retrouver cet assistant dans les réglages d'Aura."
            },
            color = AuraColors.TextDim,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (essentialsDone) AuraColors.Accent else AuraColors.Surface)
                .clickable { actions.finishOnboarding() }
                .padding(vertical = 16.dp),
        ) {
            Text(
                if (essentialsDone) "C'est parti" else "Plus tard",
                color = if (essentialsDone) Color(0xFF0B0B10) else AuraColors.Text,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun StepCard(
    number: Int,
    done: Boolean,
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .background(AuraColors.Surface, RoundedCornerShape(16.dp))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(30.dp)
                    .background(if (done) AuraColors.Accent else Color(0x33F2F2F7), CircleShape),
            ) {
                Text(
                    if (done) "✓" else number.toString(),
                    color = if (done) Color(0xFF0B0B10) else AuraColors.Text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(title, color = AuraColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(8.dp))
        Text(description, color = AuraColors.TextDim, fontSize = 14.sp, lineHeight = 19.sp)
        if (done) {
            Spacer(Modifier.height(8.dp))
            Text("Fait", color = AuraColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        } else {
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun StepButton(label: String, secondary: Boolean = false, onClick: () -> Unit) {
    Row(horizontalArrangement = Arrangement.Start) {
        Box(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (secondary) Color(0x22F2F2F7) else AuraColors.Accent)
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 11.dp),
        ) {
            Text(
                label,
                color = if (secondary) AuraColors.Text else Color(0xFF0B0B10),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

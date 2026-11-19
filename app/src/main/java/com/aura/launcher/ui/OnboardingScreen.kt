package com.aura.launcher.ui

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
            .background(AuraColors.Night.copy(alpha = 0.98f))
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AuraSpace.xl, vertical = AuraSpace.gutter),
    ) {
        Text("Bienvenue dans Aura", color = AuraColors.Text, style = AuraType.screenTitle)
        Spacer(Modifier.height(AuraSpace.s))
        Text(
            "Trois réglages pour en profiter pleinement. Android ne permet pas de les activer à ta place : " +
                "chaque bouton t'emmène au bon endroit, tu n'as plus qu'à activer.",
            color = AuraColors.TextDim,
            style = AuraType.body,
        )
        Spacer(Modifier.height(AuraSpace.xl))

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
                Spacer(Modifier.height(AuraSpace.l))
                Text(
                    "Android affiche « L'accès a été refusé » ou l'interrupteur est grisé ? C'est normal pour " +
                        "une app installée hors du Play Store :",
                    color = AuraColors.TextDim,
                    style = AuraType.bodySmall,
                )
                Spacer(Modifier.height(AuraSpace.s))
                Text(
                    "1. Touche le bouton ci-dessous pour ouvrir la fiche d'Aura\n" +
                        "2. Touche ⋮ en haut à droite\n" +
                        "3. Choisis « Autoriser les paramètres restreints » et confirme\n" +
                        "4. Reviens ici et touche à nouveau « Activer l'accès »",
                    color = AuraColors.Text,
                    style = AuraType.bodySmall.copy(lineHeight = AuraType.body.lineHeight),
                )
                Spacer(Modifier.height(AuraSpace.m))
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

        Spacer(Modifier.height(AuraSpace.m))
        Text(
            text = when {
                allDone -> "Tout est prêt !"
                essentialsDone -> "L'essentiel est prêt."
                else -> "Tu pourras retrouver cet assistant dans les réglages d'Aura."
            },
            color = AuraColors.TextDim,
            style = AuraType.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(AuraSpace.m))
        val finalShape = auraShape(AuraRadius.m)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(finalShape)
                .background(if (essentialsDone) AuraColors.Accent else AuraColors.Surface)
                .border(1.dp, if (essentialsDone) AuraColors.Accent else AuraColors.Hairline, finalShape)
                .clickable(role = Role.Button) { actions.finishOnboarding() },
        ) {
            Text(
                if (essentialsDone) "C'est parti" else "Plus tard",
                color = if (essentialsDone) AuraColors.OnAccent else AuraColors.Text,
                style = AuraType.button.copy(fontSize = AuraType.date.fontSize),
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
    val shape = auraShape(AuraRadius.l)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = AuraSpace.m)
            .clip(shape)
            .background(AuraColors.Surface)
            .border(1.dp, if (done) AuraColors.Accent.copy(alpha = 0.35f) else AuraColors.Hairline, shape)
            .padding(AuraSpace.l + AuraSpace.xxs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepMarker(number, done)
            Spacer(Modifier.width(AuraSpace.m))
            Text(title, color = AuraColors.Text, style = AuraType.row.copy(fontWeight = FontWeight.Normal))
        }
        Spacer(Modifier.height(AuraSpace.s))
        Text(description, color = AuraColors.TextDim, style = AuraType.bodySmall)
        if (done) {
            Spacer(Modifier.height(AuraSpace.s))
            Text("Fait", color = AuraColors.Accent, style = AuraType.label)
        } else {
            Spacer(Modifier.height(AuraSpace.l))
            content()
        }
    }
}

/**
 * Repère d'étape, en écho à l'icône : un anneau lavande avec le numéro tant que l'étape reste à
 * faire, un disque lavande avec une coche dessinée une fois qu'elle est faite.
 */
@Composable
private fun StepMarker(number: Int, done: Boolean) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(30.dp)) {
        Canvas(Modifier.size(30.dp)) {
            val r = size.minDimension / 2f
            if (done) {
                drawCircle(AuraColors.Accent, radius = r)
                val stroke = 2.dp.toPx()
                val path = Path().apply {
                    moveTo(size.width * 0.3f, size.height * 0.52f)
                    lineTo(size.width * 0.44f, size.height * 0.66f)
                    lineTo(size.width * 0.71f, size.height * 0.37f)
                }
                drawPath(path, AuraColors.OnAccent, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            } else {
                drawCircle(AuraColors.Accent.copy(alpha = 0.7f), radius = r - 0.75.dp.toPx(), style = Stroke(1.5.dp.toPx()))
            }
        }
        if (!done) {
            Text(number.toString(), color = AuraColors.AccentLight, style = AuraType.label)
        }
    }
}

@Composable
private fun StepButton(label: String, secondary: Boolean = false, onClick: () -> Unit) {
    val shape = auraShape(AuraRadius.s)
    Row(horizontalArrangement = Arrangement.Start) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .heightIn(min = AuraSpace.touch)
                .clip(shape)
                .background(if (secondary) AuraColors.SurfaceRaised else AuraColors.Accent)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = AuraSpace.l + AuraSpace.xxs),
        ) {
            Text(
                label,
                color = if (secondary) AuraColors.Text else AuraColors.OnAccent,
                style = AuraType.button,
            )
        }
    }
}

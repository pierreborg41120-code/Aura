package com.aura.launcher.ui

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aura.launcher.ClockStyle
import com.aura.launcher.HomeAlignment
import com.aura.launcher.LauncherActions
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * --- Apparence --- horloge d'accueil (heure + date) dans le style choisi.
 * Toucher l'heure ouvre les alarmes, toucher la date ouvre l'agenda.
 * TODO(horloges) : STACKED, WORDS, COMPACT. Pour l'instant tous les styles affichent THIN.
 */
@Composable
fun AuraClock(style: ClockStyle, alignment: HomeAlignment, actions: LauncherActions) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Se met à jour pile au changement de minute.
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000 - now % 60_000)
        }
    }

    val time = DateFormat.getTimeFormat(context).format(Date(now))
    val date = SimpleDateFormat("EEEE d MMMM", Locale.getDefault())
        .format(Date(now))
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = alignment.horizontal()) {
        Text(
            text = time,
            color = AuraColors.Text,
            style = AuraType.clock.onWallpaper(AuraShadow.wide),
            modifier = Modifier
                // Les chiffres fins ont une approche à gauche : on recolle visuellement à la marge.
                .graphicsLayer { translationX = -2.dp.toPx() }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { actions.openAlarms() },
        )
        Text(
            text = date,
            color = AuraColors.TextDim,
            style = AuraType.date.onWallpaper(),
            modifier = Modifier
                .padding(top = AuraSpace.xxs)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { actions.openCalendar() },
        )
    }
}

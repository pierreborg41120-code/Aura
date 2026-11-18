package com.aura.launcher.ui

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aura.launcher.BatteryInfo
import com.aura.launcher.LauncherActions
import com.aura.launcher.LauncherState
import com.aura.launcher.NextEvent
import com.aura.launcher.WeatherData
import com.aura.launcher.displayTemp
import com.aura.launcher.weatherKind
import com.aura.launcher.weatherLabel
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Date

/** Style commun de la ligne : un cran sous la date, avec l'ombre de lisibilité sur le fond d'écran. */
private val StatusStyle = AuraType.status.onWallpaper()

/** Espace fine insécable avant « % », à la française. */
private const val NNBSP = ' '

/**
 * Infos sous l'heure et la date : batterie et météo sur une ligne, puis prochain rendez-vous.
 * Rien n'est affiché si tout est désactivé ou indisponible.
 */
@Composable
fun StatusLine(state: LauncherState, actions: LauncherActions) {
    val battery = state.battery.takeIf { state.showBattery }
    val event = state.nextEvent.takeIf { state.showNextEvent && state.hasCalendarAccess }
    // --- Météo ---
    val weather = state.weather.takeIf { state.weatherEnabled }
    if (battery == null && event == null && weather == null) return

    Column(Modifier.padding(top = AuraSpace.s)) {
        // Batterie et météo partagent la même ligne, séparées par « · ».
        if (battery != null || weather != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                battery?.let { BatteryRow(it) }
                if (battery != null && weather != null) StatusText(" · ", AuraColors.TextDim)
                weather?.let {
                    WeatherChip(it, state.weatherFahrenheit) { state.weatherSheetOpen = true }
                }
            }
        }
        event?.let { EventRow(it) { actions.openEvent(it) } }
    }
}

// --- Météo ---
/** « ☀ 14° » : pictogramme dessiné et température ; un toucher ouvre les prévisions. */
@Composable
private fun WeatherChip(data: WeatherData, fahrenheit: Boolean, onClick: () -> Unit) {
    val now = data.now()
    val temp = displayTemp(now.temp, fahrenheit)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = "Voir les prévisions",
                onClick = onClick,
            )
            // Zone de toucher confortable sans écarter visuellement la ligne.
            .padding(vertical = AuraSpace.s + AuraSpace.xxs)
            .semantics(mergeDescendants = true) {
                contentDescription = "${weatherLabel(now.code)}, $temp degrés"
            },
    ) {
        WeatherGlyph(weatherKind(now.code), now.isDay, AuraColors.TextDim, 15.dp, onWallpaper = true)
        Spacer(Modifier.width(AuraSpace.xs + AuraSpace.xxs))
        Text(text = "$temp°", color = AuraColors.TextDim, style = StatusStyle, maxLines = 1)
    }
}

@Composable
private fun BatteryRow(battery: BatteryInfo) {
    // Batterie faible et pas en charge : on la fait un peu ressortir, sans couleur criarde.
    val low = battery.percent <= 15 && !battery.charging
    val color = if (low) AuraColors.Text else AuraColors.TextDim
    val suffix = when {
        battery.full -> " · chargée"
        battery.charging -> " · en charge"
        else -> ""
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = AuraSpace.xs)) {
        BatteryGlyph(battery.percent, color)
        Spacer(Modifier.width(AuraSpace.s))
        Text(
            text = "${battery.percent}$NNBSP%$suffix",
            color = color,
            style = StatusStyle,
        )
    }
}

@Composable
private fun EventRow(event: NextEvent, onClick: () -> Unit) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Recalcule « Demain » quand on passe minuit, même si l'évènement ne change pas.
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000 - now % 60_000)
        }
    }

    val time = DateFormat.getTimeFormat(context).format(Date(event.begin))
    val tomorrow = !sameDay(now, event.begin)

    // Le titre seul est tronqué : l'heure reste toujours visible.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = "Ouvrir dans l'agenda",
                onClick = onClick,
            )
            // Zone de toucher confortable sans écarter visuellement la ligne.
            .padding(vertical = AuraSpace.s + AuraSpace.xxs),
    ) {
        if (tomorrow) StatusText("Demain · ", AuraColors.TextFaint)
        Text(
            text = event.title,
            color = AuraColors.TextDim,
            style = StatusStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        StatusText(" · $time", AuraColors.TextDim)
    }
}

@Composable
private fun StatusText(text: String, color: Color) {
    Text(text = text, color = color, style = StatusStyle, maxLines = 1)
}

/** Petite pile dessinée à la main : contour, téton, remplissage proportionnel. */
@Composable
private fun BatteryGlyph(percent: Int, color: Color) {
    Canvas(Modifier.size(width = 17.dp, height = 9.dp)) {
        val stroke = 1.2.dp.toPx()
        val nub = 1.6.dp.toPx()
        val bodyW = size.width - nub - stroke / 2
        val radius = CornerRadius(2.dp.toPx())
        drawRoundRect(
            color = AuraColors.TextShadow.copy(alpha = 0.3f),
            topLeft = Offset(0f, 1.dp.toPx()),
            size = Size(bodyW, size.height),
            cornerRadius = radius,
            style = Stroke(width = stroke * 2f),
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(bodyW - stroke / 2, size.height - stroke),
            cornerRadius = radius,
            style = Stroke(width = stroke),
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width - nub, size.height * 0.3f),
            size = Size(nub, size.height * 0.4f),
            cornerRadius = CornerRadius(nub / 2),
        )
        val inset = stroke + 1.dp.toPx()
        val innerW = bodyW - inset * 2 + stroke / 2
        val fillW = innerW * (percent.coerceIn(0, 100) / 100f)
        if (fillW > 0f) {
            drawRoundRect(
                color = color,
                topLeft = Offset(inset, inset),
                size = Size(fillW, size.height - inset * 2),
                cornerRadius = CornerRadius(1.dp.toPx()),
            )
        }
    }
}

private fun sameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
        ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

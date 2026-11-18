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
import androidx.compose.ui.unit.sp
import com.aura.launcher.BatteryInfo
import com.aura.launcher.LauncherActions
import com.aura.launcher.LauncherState
import com.aura.launcher.NextEvent
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Date

/** Taille commune de la ligne : un cran sous la date (17 sp), pour rester discrète. */
private val StatusSize = 14.sp
private val StatusLineHeight = 20.sp

/** Espace fine insécable avant « % », à la française. */
private const val NNBSP = ' '

/**
 * Infos sous l'heure et la date : batterie, puis prochain rendez-vous.
 * Rien n'est affiché si les deux sont désactivés ou indisponibles.
 */
@Composable
fun StatusLine(state: LauncherState, actions: LauncherActions) {
    val battery = state.battery.takeIf { state.showBattery }
    val event = state.nextEvent.takeIf { state.showNextEvent && state.hasCalendarAccess }
    if (battery == null && event == null) return

    Column(Modifier.padding(top = 6.dp)) {
        battery?.let { BatteryRow(it) }
        event?.let { EventRow(it) { actions.openEvent(it) } }
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
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        BatteryGlyph(battery.percent, color)
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${battery.percent}$NNBSP%$suffix",
            color = color,
            fontSize = StatusSize,
            lineHeight = StatusLineHeight,
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
            .padding(vertical = 10.dp),
    ) {
        if (tomorrow) StatusText("Demain · ", AuraColors.TextFaint)
        Text(
            text = event.title,
            color = AuraColors.TextDim,
            fontSize = StatusSize,
            lineHeight = StatusLineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        StatusText(" · $time", AuraColors.TextDim)
    }
}

@Composable
private fun StatusText(text: String, color: Color) {
    Text(text = text, color = color, fontSize = StatusSize, lineHeight = StatusLineHeight, maxLines = 1)
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

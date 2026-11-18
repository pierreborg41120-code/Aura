package com.aura.launcher.ui

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.launcher.DayForecast
import com.aura.launcher.HourForecast
import com.aura.launcher.LauncherState
import com.aura.launcher.WeatherData
import com.aura.launcher.displayTemp
import com.aura.launcher.weatherKind
import com.aura.launcher.weatherLabel
import java.time.format.TextStyle as DayStyle
import java.util.Date
import java.util.Locale

/** Au-dessus de ce seuil, la probabilité de pluie est affichée. */
private const val RAIN_SHOWN_FROM = 20

/** Chiffres à chasse fixe : les colonnes de températures restent alignées. */
private fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

/**
 * Feuille de prévisions, ouverte en touchant la météo sous l'heure :
 * maintenant en grand, les 12 prochaines heures (défilement horizontal), puis les 7 jours.
 */
@Composable
fun WeatherSheet(state: LauncherState) {
    val visible = state.weatherSheetOpen && state.weather != null
    // On garde le dernier contenu pour que la carte ne se vide pas pendant l'animation de fermeture.
    var held by remember { mutableStateOf<WeatherData?>(null) }
    state.weather?.let { if (it != held) held = it }

    val close = { state.weatherSheetOpen = false }
    BackHandler(enabled = visible) { close() }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(AuraMotion.standard)),
            exit = fadeOut(tween(AuraMotion.quick)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(AuraColors.SheetScrim)
                    .pointerInput(Unit) { detectTapGestures { close() } },
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(tween(AuraMotion.gentle, easing = AuraMotion.enter)) { it / 2 } +
                fadeIn(tween(AuraMotion.standard)),
            exit = slideOutVertically(tween(AuraMotion.standard, easing = AuraMotion.exit)) { it / 2 } +
                fadeOut(tween(AuraMotion.quick)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            held?.let { data ->
                WeatherCard(
                    data = data,
                    fahrenheit = state.weatherFahrenheit,
                    place = if (state.weatherUseCity) state.weatherCity?.name else null,
                    onClose = close,
                )
            }
        }
    }
}

@Composable
private fun WeatherCard(data: WeatherData, fahrenheit: Boolean, place: String?, onClose: () -> Unit) {
    val context = LocalContext.current
    val dismissDrag = with(LocalDensity.current) { 56.dp.toPx() }
    val shape = RoundedCornerShape(AuraRadius.xl)
    val maxBody = (LocalConfiguration.current.screenHeightDp * 0.8f).dp - 64.dp

    val nowMs = remember(data) { System.currentTimeMillis() }
    val now = data.now(nowMs)
    val hours = data.upcomingHours(nowMs)
    val days = data.upcomingDays(nowMs)
    val today = days.firstOrNull()?.takeIf { it.date == data.today(nowMs) }
    fun t(c: Double) = displayTemp(c, fahrenheit)

    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(AuraSpace.s)
            .clip(shape)
            .background(AuraColors.Surface)
            .border(1.dp, AuraColors.Hairline, shape)
            // Absorbe les touchers sur la carte (sinon ils fermeraient la feuille).
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        // Poignée + en-tête : « glisser vers le bas » ferme la feuille.
        Column(
            Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total > dismissDrag) onClose() },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            total += amount
                        },
                    )
                },
        ) {
            Box(
                Modifier
                    .padding(top = AuraSpace.s + AuraSpace.xxs)
                    .align(Alignment.CenterHorizontally)
                    .size(width = 32.dp, height = 4.dp)
                    .background(AuraColors.TextFaint.copy(alpha = 0.4f), CircleShape),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(horizontal = AuraSpace.xl),
            ) {
                Text(
                    text = place ?: "Météo",
                    color = AuraColors.Text,
                    style = AuraType.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                Text(
                    text = "Mise à jour " + DateFormat.getTimeFormat(context).format(Date(data.fetchedAt)),
                    color = AuraColors.TextDim,
                    style = AuraType.caption,
                    maxLines = 1,
                )
            }
        }

        Column(
            Modifier
                .heightIn(max = maxBody)
                .verticalScroll(rememberScrollState()),
        ) {
            // Maintenant : pictogramme et température en grand, conditions et min / max du jour à côté.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = AuraSpace.xl, end = AuraSpace.xl, top = AuraSpace.xs, bottom = AuraSpace.l)
                    .semantics(mergeDescendants = true) { },
            ) {
                WeatherGlyph(weatherKind(now.code), now.isDay, AuraColors.Text, 40.dp)
                Spacer(Modifier.width(AuraSpace.m))
                Text(
                    text = "${t(now.temp)}°",
                    color = AuraColors.Text,
                    style = AuraType.clock.copy(fontSize = 60.sp, lineHeight = 64.sp, letterSpacing = (-1.5).sp).tabular(),
                )
                Spacer(Modifier.width(AuraSpace.l))
                Column(Modifier.weight(1f)) {
                    Text(
                        weatherLabel(now.code),
                        color = AuraColors.Text,
                        style = AuraType.body,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (today != null) {
                        Text(
                            "Min ${t(today.min)}°  ·  Max ${t(today.max)}°",
                            color = AuraColors.TextDim,
                            style = AuraType.bodySmall.tabular(),
                        )
                    }
                }
            }

            if (hours.isNotEmpty()) {
                HorizontalDivider(color = AuraColors.Hairline, modifier = Modifier.padding(horizontal = AuraSpace.xl))
                SectionLabel("Prochaines heures")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = AuraSpace.l),
                    horizontalArrangement = Arrangement.spacedBy(AuraSpace.xxs),
                    modifier = Modifier.fillMaxWidth().padding(bottom = AuraSpace.m),
                ) {
                    items(hours, key = { it.epochSec }) { hour ->
                        HourCell(hour, first = hour == hours.first(), temp = t(hour.temp))
                    }
                }
            }

            if (days.isNotEmpty()) {
                HorizontalDivider(color = AuraColors.Hairline, modifier = Modifier.padding(horizontal = AuraSpace.xl))
                SectionLabel("7 jours")
                // Échelle commune à toutes les barres : on voit d'un coup d'œil les jours froids et chauds.
                val low = days.minOf { it.min }
                val high = days.maxOf { it.max }
                Column(Modifier.padding(horizontal = AuraSpace.xl)) {
                    days.forEachIndexed { i, day ->
                        DayRow(
                            day = day,
                            label = dayLabel(day, i == 0 && today != null),
                            min = t(day.min),
                            max = t(day.max),
                            low = low,
                            high = high,
                            // Point « maintenant » sur la barre d'aujourd'hui.
                            current = if (i == 0 && today != null) now.temp else null,
                        )
                    }
                }
            }

            Text(
                "Données : Open-Meteo",
                color = AuraColors.TextFaint,
                style = AuraType.caption,
                modifier = Modifier.padding(start = AuraSpace.xl, end = AuraSpace.xl, top = AuraSpace.m, bottom = AuraSpace.l),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = AuraColors.TextDim,
        style = AuraType.label,
        modifier = Modifier
            .padding(start = AuraSpace.xl, end = AuraSpace.xl, top = AuraSpace.l, bottom = AuraSpace.s)
            .semantics { heading() },
    )
}

/** Une heure : heure, pictogramme, température, et % de pluie s'il dépasse 20 %. */
@Composable
private fun HourCell(hour: HourForecast, first: Boolean, temp: Int) {
    val label = if (first) "Maint." else "${hour.hour} h"
    val rain = hour.precip?.takeIf { it > RAIN_SHOWN_FROM }
    val spoken = buildString {
        append(if (first) "Maintenant" else "${hour.hour} heures")
        append(", ${weatherLabel(hour.code)}, $temp degrés")
        rain?.let { append(", pluie $it pour cent") }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(52.dp)
            .padding(vertical = AuraSpace.xs)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Text(
            label,
            color = if (first) AuraColors.Text else AuraColors.TextDim,
            style = AuraType.caption.tabular(),
            maxLines = 1,
        )
        Spacer(Modifier.height(AuraSpace.s))
        WeatherGlyph(weatherKind(hour.code), hour.isDay, AuraColors.Text, 22.dp)
        Spacer(Modifier.height(AuraSpace.s))
        Text("$temp°", color = AuraColors.Text, style = AuraType.body.tabular(), maxLines = 1)
        // La ligne de pluie garde toujours sa hauteur : les cellules restent alignées.
        Text(
            rain?.let { "$it %" } ?: "",
            color = AuraColors.AccentLight,
            style = AuraType.caption.tabular(),
            maxLines = 1,
        )
    }
}

/** Un jour : nom, pictogramme, % de pluie, min, barre de plage, max. */
@Composable
private fun DayRow(
    day: DayForecast,
    label: String,
    min: Int,
    max: Int,
    low: Double,
    high: Double,
    current: Double?,
) {
    val rain = day.precip?.takeIf { it > RAIN_SHOWN_FROM }
    val spoken = buildString {
        append("$label, ${weatherLabel(day.code)}, minimum $min, maximum $max degrés")
        rain?.let { append(", pluie $it pour cent") }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Text(
            label,
            color = AuraColors.Text,
            style = AuraType.body,
            maxLines = 1,
            modifier = Modifier.width(56.dp),
        )
        WeatherGlyph(weatherKind(day.code), true, AuraColors.Text, 20.dp)
        Text(
            rain?.let { "$it %" } ?: "",
            color = AuraColors.AccentLight,
            style = AuraType.caption.tabular(),
            maxLines = 1,
            modifier = Modifier.width(44.dp).padding(start = AuraSpace.s),
        )
        Text(
            "$min°",
            color = AuraColors.TextDim,
            style = AuraType.body.tabular(),
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(40.dp),
        )
        RangeBar(
            min = day.min,
            max = day.max,
            low = low,
            high = high,
            current = current,
            modifier = Modifier.weight(1f).padding(horizontal = AuraSpace.m),
        )
        Text(
            "$max°",
            color = AuraColors.Text,
            style = AuraType.body.tabular(),
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(40.dp),
        )
    }
}

/** Barre de plage : la piste couvre toute la semaine, le segment le min–max du jour. */
@Composable
private fun RangeBar(min: Double, max: Double, low: Double, high: Double, current: Double?, modifier: Modifier) {
    Canvas(modifier.height(10.dp)) {
        val h = 4.dp.toPx()
        val y = (size.height - h) / 2
        val span = (high - low).takeIf { it > 0.1 } ?: 1.0
        fun x(v: Double) = (((v - low) / span).toFloat().coerceIn(0f, 1f)) * size.width
        val radius = CornerRadius(h / 2)
        drawRoundRect(
            color = AuraColors.Hairline,
            topLeft = Offset(0f, y),
            size = Size(size.width, h),
            cornerRadius = radius,
        )
        val start = x(min)
        val end = maxOf(x(max), start + h)
        drawRoundRect(
            color = AuraColors.TextDim,
            topLeft = Offset(start, y),
            size = Size(end - start, h),
            cornerRadius = radius,
        )
        current?.let {
            val cx = x(it).coerceIn(start, end)
            drawCircle(AuraColors.Surface, radius = 4.dp.toPx(), center = Offset(cx, size.height / 2))
            drawCircle(AuraColors.Text, radius = 3.dp.toPx(), center = Offset(cx, size.height / 2))
        }
    }
}

/** « Auj. », puis « Jeu. », « Ven. »… : jour abrégé, en français. */
private fun dayLabel(day: DayForecast, isToday: Boolean): String {
    if (isToday) return "Auj."
    val name = day.date.dayOfWeek.getDisplayName(DayStyle.SHORT, Locale.FRENCH)
        .replaceFirstChar { it.titlecase(Locale.FRENCH) }
    return name
}

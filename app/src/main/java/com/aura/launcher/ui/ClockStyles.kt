package com.aura.launcher.ui

import android.text.format.DateFormat
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.launcher.ClockStyle
import com.aura.launcher.HomeAlignment
import com.aura.launcher.LauncherActions
import kotlinx.coroutines.delay
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/*
 * --- Apparence --- horloge d'accueil (heure + date) dans le style choisi.
 *
 * - Fine (THIN) : l'horloge d'origine, inchangée.
 * - Empilée (STACKED) : heures au-dessus des minutes, grands chiffres fins presque collés.
 *   En 12 h, pas de « PM » géant : l'indicateur matin/après-midi rejoint discrètement la date.
 * - En toutes lettres (WORDS) : « Vingt et une heures / quarante-neuf », voir [frenchTimeWords].
 * - Compacte (COMPACT) : « 21:49 · Jeudi 19 novembre » sur une ligne ; date courte si ça déborde.
 *
 * Toujours : toucher l'heure ouvre les alarmes, toucher la date ouvre l'agenda, sans ondulation.
 */

/** Styles propres aux horloges (les tailles de base restent dans [AuraType]). */
private object ClockType {
    /** Empilée : très grands chiffres fins, interligne plus court que la taille pour coller les lignes. */
    val stacked = TextStyle(
        fontWeight = FontWeight.ExtraLight,
        fontSize = 104.sp,
        lineHeight = 90.sp,
        letterSpacing = (-3).sp,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both,
        ),
    )

    /** En toutes lettres : plus petit que l'horloge fine, graisse légère. */
    val words = TextStyle(
        fontWeight = FontWeight.Light,
        fontSize = 42.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.5).sp,
    )

    /** Compacte : heure un peu plus marquée que la date. */
    val compactTime = TextStyle(fontWeight = FontWeight.Normal, fontSize = 30.sp, letterSpacing = 0.sp)
    val compactDate = TextStyle(fontWeight = FontWeight.Light, fontSize = 30.sp, letterSpacing = 0.sp)
}

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

    val date = Date(now)
    val locale = Locale.getDefault()
    // 12/24 h suit le réglage du système (relu à chaque minute).
    val is24h = DateFormat.is24HourFormat(context)
    val time = DateFormat.getTimeFormat(context).format(date)
    val longDate = SimpleDateFormat("EEEE d MMMM", locale).format(date).capitalized(locale)

    val reducedMotion = rememberReducedMotion()
    val onAlarms = { actions.openAlarms() }
    val onCalendar = { actions.openCalendar() }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = alignment.horizontal()) {
        when (style) {
            ClockStyle.THIN -> {
                MinuteFade(time, reducedMotion, Modifier.thinOptics(alignment).noRippleClick(onAlarms)) {
                    Text(
                        text = it,
                        color = AuraColors.Text,
                        style = AuraType.clock.onWallpaper(AuraShadow.wide).homeFont(),
                    )
                }
                ClockDate(longDate, onCalendar)
            }

            ClockStyle.STACKED -> {
                val cal = Calendar.getInstance().also { it.time = date }
                val hour24 = cal.get(Calendar.HOUR_OF_DAY)
                val hours = if (is24h) "%02d".format(locale, hour24)
                else (if (hour24 % 12 == 0) 12 else hour24 % 12).toString()
                val minutes = "%02d".format(locale, cal.get(Calendar.MINUTE))
                MinuteFade(
                    "$hours\n$minutes",
                    reducedMotion,
                    Modifier
                        .thinOptics(alignment)
                        .readAs(time, "Ouvrir les alarmes", onAlarms)
                        .noRippleClick(onAlarms),
                ) {
                    Text(
                        text = it,
                        color = AuraColors.Text,
                        style = ClockType.stacked.onWallpaper(AuraShadow.wide).homeFont(),
                        textAlign = alignment.textAlign(),
                    )
                }
                // En 12 h, l'indicateur matin / après-midi se glisse en petit après la date.
                val amPm = if (is24h) null
                else DateFormatSymbols.getInstance(locale).amPmStrings[cal.get(Calendar.AM_PM)]
                ClockDate(
                    if (amPm == null) longDate else "$longDate · $amPm",
                    onCalendar,
                    Modifier.padding(top = AuraSpace.m),
                )
            }

            ClockStyle.WORDS -> {
                val cal = Calendar.getInstance().also { it.time = date }
                val (hourPart, minutePart) = frenchTimeWordsParts(
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    is24h,
                )
                // L'heure et les minutes sur deux lignes (l'heure peut elle-même passer sur deux lignes).
                val words = if (minutePart == null) hourPart else "$hourPart\n$minutePart"
                MinuteFade(
                    words,
                    reducedMotion,
                    Modifier
                        .readAs(time, "Ouvrir les alarmes", onAlarms)
                        .noRippleClick(onAlarms),
                ) {
                    Text(
                        text = it,
                        color = AuraColors.Text,
                        style = ClockType.words.onWallpaper(AuraShadow.wide).homeFont(),
                        textAlign = alignment.textAlign(),
                    )
                }
                ClockDate(longDate, onCalendar, Modifier.padding(top = AuraSpace.s))
            }

            ClockStyle.COMPACT -> CompactClock(time, date, longDate, locale, alignment, reducedMotion, onAlarms, onCalendar)
        }
    }
}

/** Heure · date sur une seule ligne. La date passe en forme courte si la ligne déborde. */
@Composable
private fun CompactClock(
    time: String,
    date: Date,
    longDate: String,
    locale: Locale,
    alignment: HomeAlignment,
    reducedMotion: Boolean,
    onAlarms: () -> Unit,
    onCalendar: () -> Unit,
) {
    val timeStyle = ClockType.compactTime.onWallpaper(AuraShadow.wide).homeFont()
    val dateStyle = ClockType.compactDate.onWallpaper().homeFont()
    val separator = "  ·  "
    val measurer = rememberTextMeasurer()

    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = alignment.boxAlignment()) {
        val maxPx = constraints.maxWidth
        fun widthOf(text: String, style: TextStyle) =
            measurer.measure(text, style, softWrap = false, maxLines = 1).size.width
        val fits = widthOf(time, timeStyle) + widthOf(separator + longDate, dateStyle) <= maxPx
        val shownDate = if (fits) longDate
        else SimpleDateFormat("EEE d MMM", locale).format(date).capitalized(locale)

        Row(verticalAlignment = Alignment.Bottom) {
            MinuteFade(time, reducedMotion, Modifier.noRippleClick(onAlarms)) {
                Text(text = it, color = AuraColors.Text, style = timeStyle, maxLines = 1, softWrap = false)
            }
            Text(
                text = separator,
                color = AuraColors.TextFaint,
                style = dateStyle,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = shownDate,
                color = AuraColors.TextDim,
                style = dateStyle,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.noRippleClick(onCalendar),
            )
        }
    }
}

/** Date sous l'horloge (Fine, Empilée, En toutes lettres) : toucher → agenda. */
@Composable
private fun ClockDate(text: String, onCalendar: () -> Unit, modifier: Modifier = Modifier.padding(top = AuraSpace.xxs)) {
    Text(
        text = text,
        color = AuraColors.TextDim,
        style = AuraType.date.onWallpaper().homeFont(),
        modifier = modifier.noRippleClick(onCalendar),
    )
}

/** Fondu croisé court quand la minute change (aucune animation si elles sont coupées). */
@Composable
private fun MinuteFade(
    value: String,
    reducedMotion: Boolean,
    modifier: Modifier,
    content: @Composable (String) -> Unit,
) {
    Crossfade(
        targetState = value,
        modifier = modifier,
        animationSpec = if (reducedMotion) snap() else tween(AuraMotion.quick),
        label = "clock",
    ) { content(it) }
}

/** Les chiffres fins ont une approche sur les côtés : on recolle visuellement à la marge. */
private fun Modifier.thinOptics(alignment: HomeAlignment): Modifier {
    val shift: Dp = when (alignment) {
        HomeAlignment.START -> (-2).dp
        HomeAlignment.CENTER -> 0.dp
        HomeAlignment.END -> 2.dp
    }
    return if (shift == 0.dp) this else graphicsLayer { translationX = shift.toPx() }
}

/** Lecteur d'écran : lit l'heure normale (« 21:49 ») au lieu du texte affiché. */
private fun Modifier.readAs(spoken: String, actionLabel: String, action: () -> Unit): Modifier =
    clearAndSetSemantics {
        contentDescription = spoken
        onClick(label = actionLabel) { action(); true }
    }

/** Toucher sans effet d'ondulation, comme le reste de l'accueil. */
@Composable
private fun Modifier.noRippleClick(onClick: () -> Unit): Modifier = clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = onClick,
)

private fun HomeAlignment.boxAlignment(): Alignment = when (this) {
    HomeAlignment.START -> Alignment.CenterStart
    HomeAlignment.CENTER -> Alignment.Center
    HomeAlignment.END -> Alignment.CenterEnd
}

private fun String.capitalized(locale: Locale) = replaceFirstChar { it.titlecase(locale) }

// --- Heure en toutes lettres ---

/**
 * Heure en toutes lettres, en français, première lettre en majuscule.
 * Règles : « Minuit », « Midi », « Une heure », « Neuf heures cinq », « Vingt et une heures quarante-neuf ».
 * Les minutes sont au féminin (« une », « vingt et une ») ; 0 minute → l'heure seule, sans « pile ».
 * Midi et minuit gardent leur nom avec des minutes (« Midi trente »), sans « et demie ».
 * En 12 h : « Neuf heures quarante-neuf » pour 21:49, sans précision matin / soir ; midi et minuit restent.
 */
fun frenchTimeWords(hour: Int, minute: Int, is24h: Boolean): String {
    val (hourPart, minutePart) = frenchTimeWordsParts(hour, minute, is24h)
    return if (minutePart == null) hourPart else "$hourPart $minutePart"
}

/** Même chose en deux morceaux : (heure, minutes ou null si 0). L'heure porte la majuscule. */
fun frenchTimeWordsParts(hour: Int, minute: Int, is24h: Boolean): Pair<String, String?> {
    val h = ((hour % 24) + 24) % 24
    val m = ((minute % 60) + 60) % 60
    val hourPart = when {
        h == 0 -> "minuit"
        h == 12 -> "midi"
        else -> {
            val shown = if (is24h) h else h % 12
            if (shown == 1) "une heure" else "${frenchNumber(shown)} heures"
        }
    }.replaceFirstChar { it.uppercaseChar() }
    val minutePart = if (m == 0) null else frenchNumber(m)
    return hourPart to minutePart
}

/** Nombre de 1 à 59 en lettres, au féminin (« une », « vingt et une », « quarante-deux »). */
private fun frenchNumber(n: Int): String {
    val units = listOf(
        "", "une", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf",
        "dix", "onze", "douze", "treize", "quatorze", "quinze", "seize", "dix-sept", "dix-huit", "dix-neuf",
    )
    if (n < 20) return units[n]
    val tens = when (n / 10) {
        2 -> "vingt"
        3 -> "trente"
        4 -> "quarante"
        else -> "cinquante"
    }
    return when (val u = n % 10) {
        0 -> tens
        1 -> "$tens et une"
        else -> "$tens-${units[u]}"
    }
}

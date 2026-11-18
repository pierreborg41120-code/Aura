package com.aura.launcher

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.roundToInt

/** Grandes familles de temps, d'après le code WMO renvoyé par Open-Meteo. Chacune a son pictogramme. */
enum class WeatherKind { CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM }

/** Une heure de prévision. [epochSec] est le début de l'heure ; [hour] l'heure locale du lieu (0–23). */
data class HourForecast(
    val epochSec: Long,
    val hour: Int,
    val temp: Double,
    val code: Int,
    val isDay: Boolean,
    val precip: Int?,
)

/** Une journée de prévision. */
data class DayForecast(
    val date: LocalDate,
    val code: Int,
    val min: Double,
    val max: Double,
    val precip: Int?,
)

/** Conditions « maintenant » : affichées sous l'heure et en tête de la feuille. */
data class WeatherNow(val temp: Double, val code: Int, val isDay: Boolean)

/** Ville choisie à la main (résultat du géocodage d'Open-Meteo). */
data class WeatherPlace(val name: String, val detail: String, val lat: Double, val lon: Double)

/** Dernière réponse d'Open-Meteo, analysée. Toutes les températures sont en °C. */
data class WeatherData(
    val fetchedAt: Long,
    val utcOffsetSec: Int,
    val current: WeatherNow,
    val hours: List<HourForecast>,
    val days: List<DayForecast>,
) {
    /** Trop vieille pour être affichée (hors ligne depuis longtemps). */
    fun isStale(now: Long = System.currentTimeMillis()): Boolean = now - fetchedAt > MAX_AGE_MS

    /**
     * Conditions actuelles : la valeur « current » si elle est récente, sinon l'heure de prévision
     * qui contient maintenant (plus juste quand la réponse date de quelques heures).
     */
    fun now(now: Long = System.currentTimeMillis()): WeatherNow {
        if (now - fetchedAt < 45 * 60_000L) return current
        val h = hours.firstOrNull { now in it.epochSec * 1000 until (it.epochSec + 3600) * 1000 }
        return h?.let { WeatherNow(it.temp, it.code, it.isDay) } ?: current
    }

    /** Les [count] prochaines heures, en commençant par l'heure en cours. */
    fun upcomingHours(now: Long = System.currentTimeMillis(), count: Int = 12): List<HourForecast> =
        hours.filter { (it.epochSec + 3600) * 1000 > now }.take(count)

    /** Aujourd'hui (dans le fuseau du lieu) et les jours suivants. */
    fun upcomingDays(now: Long = System.currentTimeMillis()): List<DayForecast> {
        val today = today(now)
        return days.filter { !it.date.isBefore(today) }.take(7)
    }

    fun today(now: Long = System.currentTimeMillis()): LocalDate =
        Instant.ofEpochMilli(now).atOffset(ZoneOffset.ofTotalSeconds(utcOffsetSec)).toLocalDate()

    companion object {
        /** Au-delà de 6 h sans mise à jour, on n'affiche plus rien. */
        const val MAX_AGE_MS = 6 * 60 * 60_000L
        /** On ne redemande pas la météo plus d'une fois toutes les 30 min. */
        const val REFRESH_MS = 30 * 60_000L

        /** Analyse la réponse brute (gardée telle quelle dans Prefs). Null si le format est inattendu. */
        fun parse(raw: String, fetchedAt: Long): WeatherData? = try {
            val root = JSONObject(raw)
            val offset = root.optInt("utc_offset_seconds", 0)
            val zone = ZoneOffset.ofTotalSeconds(offset)

            val cur = root.getJSONObject("current")
            val current = WeatherNow(
                temp = cur.getDouble("temperature_2m"),
                code = cur.optInt("weather_code", 0),
                isDay = cur.optInt("is_day", 1) == 1,
            )

            val h = root.getJSONObject("hourly")
            val hTime = h.getJSONArray("time")
            val hTemp = h.getJSONArray("temperature_2m")
            val hCode = h.getJSONArray("weather_code")
            val hRain = h.optJSONArray("precipitation_probability")
            val hDay = h.optJSONArray("is_day")
            val hours = (0 until hTime.length()).mapNotNull { i ->
                if (hTemp.isNull(i)) return@mapNotNull null
                // Heures locales du lieu (timezone=auto), sans fuseau : on applique le décalage renvoyé.
                val local = LocalDateTime.parse(hTime.getString(i))
                HourForecast(
                    epochSec = local.toEpochSecond(zone),
                    hour = local.hour,
                    temp = hTemp.getDouble(i),
                    code = hCode.optIntOrNull(i) ?: 0,
                    isDay = hDay?.optIntOrNull(i)?.let { it == 1 } ?: (local.hour in 7..19),
                    precip = hRain?.optIntOrNull(i),
                )
            }

            val d = root.getJSONObject("daily")
            val dTime = d.getJSONArray("time")
            val dCode = d.getJSONArray("weather_code")
            val dMax = d.getJSONArray("temperature_2m_max")
            val dMin = d.getJSONArray("temperature_2m_min")
            val dRain = d.optJSONArray("precipitation_probability_max")
            val days = (0 until dTime.length()).mapNotNull { i ->
                if (dMax.isNull(i) || dMin.isNull(i)) return@mapNotNull null
                DayForecast(
                    date = LocalDate.parse(dTime.getString(i)),
                    code = dCode.optIntOrNull(i) ?: 0,
                    min = dMin.getDouble(i),
                    max = dMax.getDouble(i),
                    precip = dRain?.optIntOrNull(i),
                )
            }
            WeatherData(fetchedAt, offset, current, hours, days)
        } catch (e: Exception) {
            null
        }

        private fun JSONArray.optIntOrNull(i: Int): Int? =
            if (i >= length() || isNull(i)) null else optDouble(i).takeIf { !it.isNaN() }?.roundToInt()
    }
}

/** Famille de temps d'un code WMO. */
fun weatherKind(code: Int): WeatherKind = when (code) {
    0 -> WeatherKind.CLEAR
    1, 2 -> WeatherKind.PARTLY
    3 -> WeatherKind.CLOUDY
    45, 48 -> WeatherKind.FOG
    in 51..57 -> WeatherKind.DRIZZLE
    in 61..67, in 80..82 -> WeatherKind.RAIN
    in 71..77, 85, 86 -> WeatherKind.SNOW
    in 95..99 -> WeatherKind.STORM
    else -> WeatherKind.CLOUDY
}

/** Libellé en français d'un code WMO (feuille de prévisions, lecteurs d'écran). */
fun weatherLabel(code: Int): String = when (code) {
    0 -> "Ciel dégagé"
    1 -> "Plutôt dégagé"
    2 -> "Partiellement nuageux"
    3 -> "Couvert"
    45, 48 -> "Brouillard"
    56, 57 -> "Bruine verglaçante"
    in 51..55 -> "Bruine"
    66, 67 -> "Pluie verglaçante"
    in 61..65 -> "Pluie"
    in 80..82 -> "Averses"
    85, 86 -> "Averses de neige"
    in 71..77 -> "Neige"
    in 95..99 -> "Orage"
    else -> "Nuageux"
}

/** Température à afficher, arrondie, dans l'unité choisie. */
fun displayTemp(celsius: Double, fahrenheit: Boolean): Int =
    (if (fahrenheit) celsius * 9 / 5 + 32 else celsius).roundToInt()

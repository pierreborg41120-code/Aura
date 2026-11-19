package com.aura.launcher

import java.util.Calendar

/*
 * Plages horaires partagées par le mode concentration et les profils d'accueil :
 * une heure de début, une heure de fin (en minutes depuis minuit) et des jours.
 * Une plage peut passer minuit (22:00 → 07:00) : elle appartient au jour où elle commence.
 */

enum class DaySet(val label: String, val short: String) {
    ALL("Tous les jours", "Chaque jour"),
    WEEKDAYS("En semaine", "Lun – ven"),
    WEEKEND("Le week-end", "Week-end");

    /** [dayOfWeek] au format de Calendar (dimanche = 1 … samedi = 7). */
    fun includes(dayOfWeek: Int): Boolean = when (this) {
        ALL -> true
        WEEKDAYS -> dayOfWeek in Calendar.MONDAY..Calendar.FRIDAY
        WEEKEND -> dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
    }
}

data class TimeWindow(val start: Int, val end: Int, val days: DaySet) {

    /** Vrai si [now] tombe dans la plage. */
    fun contains(now: Long = System.currentTimeMillis()): Boolean {
        if (start == end) return false
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val minute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val day = cal.get(Calendar.DAY_OF_WEEK)
        val previousDay = if (day == Calendar.SUNDAY) Calendar.SATURDAY else day - 1
        return if (start < end) {
            days.includes(day) && minute >= start && minute < end
        } else {
            (days.includes(day) && minute >= start) || (days.includes(previousDay) && minute < end)
        }
    }

    /** Prochaine heure de fin après [now] (la fin de la plage en cours quand elle est active). */
    fun nextEnd(now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, end / 60)
            set(Calendar.MINUTE, end % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_MONTH, 1)
        return cal.timeInMillis
    }
}

/** « 07:30 ». */
fun formatClock(minutes: Int): String = "%02d:%02d".format(minutes / 60 % 24, minutes % 60)

/** Heure d'un instant, « 07:30 ». */
fun formatClockAt(timeMs: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = timeMs }
    return formatClock(cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE))
}

/**
 * --- Concentration --- certaines apps disparaissent de l'accueil et de la liste pendant une plage
 * horaire (ou à la demande). Elles restent trouvables par la recherche, derrière la pause de 5 s.
 */
data class FocusSettings(
    /** Clés des apps mises de côté. */
    val apps: Set<String> = emptySet(),
    val scheduled: Boolean = false,
    val window: TimeWindow = TimeWindow(22 * 60, 7 * 60, DaySet.ALL),
    /** Concentration lancée à la main jusqu'à cet instant (0 = non). */
    val manualUntil: Long = 0L,
    /** Plage horaire suspendue jusqu'à cet instant (« Arrêter » pendant la plage). */
    val snoozedUntil: Long = 0L,
) {
    fun isActive(now: Long = System.currentTimeMillis()): Boolean =
        manualUntil > now || (scheduled && now >= snoozedUntil && window.contains(now))

    /** Fin de la concentration en cours (null si elle n'est pas active). */
    fun endsAt(now: Long = System.currentTimeMillis()): Long? {
        if (!isActive(now)) return null
        val scheduleEnd = if (scheduled && now >= snoozedUntil && window.contains(now)) window.nextEnd(now) else 0L
        return maxOf(manualUntil, scheduleEnd)
    }

    /** Paquets concernés (pour le bilan de la semaine). */
    val packages: Set<String> get() = apps.map { it.substringBefore('/') }.toSet()
}

/** --- Profils d'accueil --- « Perso » et « Travail », chacun avec ses favoris. */
object HomeProfile {
    const val PERSO = 0
    const val TRAVAIL = 1

    fun label(profile: Int) = if (profile == TRAVAIL) "Travail" else "Perso"
}

data class ProfileSettings(
    val enabled: Boolean = false,
    /** Passage automatique sur « Travail » pendant [window]. */
    val auto: Boolean = false,
    val window: TimeWindow = TimeWindow(9 * 60, 18 * 60, DaySet.WEEKDAYS),
) {
    /** Profil voulu par l'horaire à [now]. */
    fun scheduled(now: Long = System.currentTimeMillis()): Int =
        if (window.contains(now)) HomeProfile.TRAVAIL else HomeProfile.PERSO
}

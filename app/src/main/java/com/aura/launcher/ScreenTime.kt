package com.aura.launcher

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * --- Limiteur --- temps passé aujourd'hui dans les apps limitées en durée.
 * Le service d'accessibilité (LockService) note l'app au premier plan (son nom de paquet
 * seulement, jamais le contenu de l'écran) ; ce compteur additionne les sessions de la journée.
 * Tout reste dans un fichier privé, rien ne quitte le téléphone.
 */
object ScreenTime {

    /** Temps de rab accordé par « Encore 5 minutes ». */
    const val GRANT_MS = 5 * 60_000L

    private var sp: SharedPreferences? = null

    /** Session en cours : paquet et heure de début (null si aucune app limitée n'est ouverte). */
    @Volatile private var sessionPkg: String? = null
    @Volatile private var sessionStart = 0L

    private val mutableTimeUp = MutableStateFlow<String?>(null)
    /** Paquet dont le temps vient d'être écoulé (l'accueil affiche alors l'écran « Temps écoulé »). */
    val timeUp: StateFlow<String?> = mutableTimeUp

    fun init(context: Context) {
        if (sp != null) return
        sp = context.applicationContext.getSharedPreferences("aura_screen_time", Context.MODE_PRIVATE)
        forgetOldDays()
    }

    /** Limites en minutes par jour : paquet → minutes (lues dans les réglages d'Aura). */
    fun limits(context: Context): Map<String, Int> = Prefs(context).appLimits

    /**
     * --- Bilan de la semaine --- paquets dont on compte le temps : apps limitées (durée ou pause)
     * et apps mises de côté par le mode concentration.
     */
    fun tracked(context: Context): Set<String> {
        val prefs = Prefs(context)
        return prefs.appLimits.keys + prefs.limitedApps.map { it.substringBefore('/') } + prefs.focus.packages
    }

    /** Temps d'un jour passé, en ms ([daysAgo] = 0 : aujourd'hui, session en cours comprise). */
    fun usedOn(pkg: String, daysAgo: Int, now: Long = System.currentTimeMillis()): Long {
        if (daysAgo == 0) return usedToday(pkg, now)
        return sp?.getLong(HISTORY_PREFIX + dayKey(now, daysAgo) + ":" + pkg, 0L) ?: 0L
    }

    /** Temps utilisé aujourd'hui (session en cours comprise), en ms. */
    fun usedToday(pkg: String, now: Long = System.currentTimeMillis()): Long {
        val stored = readToday("used:$pkg")
        val live = if (sessionPkg == pkg) (now - sessionStart).coerceAtLeast(0) else 0
        return stored + live
    }

    /** Temps restant aujourd'hui (peut être négatif), en ms. */
    fun remaining(pkg: String, limitMinutes: Int, now: Long = System.currentTimeMillis()): Long =
        limitMinutes * 60_000L + readToday("grant:$pkg") - usedToday(pkg, now)

    /** « Encore 5 minutes ». */
    fun grant(pkg: String) {
        writeToday("grant:$pkg", readToday("grant:$pkg") + GRANT_MS)
        mutableTimeUp.value = null
    }

    fun startSession(pkg: String, now: Long = System.currentTimeMillis()) {
        if (sessionPkg == pkg) return
        endSession(now)
        sessionPkg = pkg
        sessionStart = now
    }

    fun endSession(now: Long = System.currentTimeMillis()) {
        val pkg = sessionPkg ?: return
        val spent = (now - sessionStart).coerceAtLeast(0)
        writeToday("used:$pkg", readToday("used:$pkg") + spent)
        // Historique par jour, pour le bilan de la semaine.
        val historyKey = HISTORY_PREFIX + dayKey(now, 0) + ":" + pkg
        sp?.let { it.edit().putLong(historyKey, it.getLong(historyKey, 0L) + spent).apply() }
        sessionPkg = null
    }

    val currentSession: String? get() = sessionPkg

    fun signalTimeUp(pkg: String) {
        mutableTimeUp.value = pkg
    }

    fun clearTimeUp() {
        mutableTimeUp.value = null
    }

    // Valeurs stockées sous la forme "aaaammjj|ms" : remises à zéro chaque jour.
    private fun today(): String = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date())

    private const val HISTORY_PREFIX = "h:"
    private const val HISTORY_DAYS = 28

    /** « aaaammjj » du jour situé [daysAgo] jours avant [now]. */
    private fun dayKey(now: Long, daysAgo: Int): String {
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = now
            add(java.util.Calendar.DAY_OF_MONTH, -daysAgo)
        }
        return SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(cal.time)
    }

    /** On garde quatre semaines d'historique, pas plus. */
    private fun forgetOldDays() {
        val prefs = sp ?: return
        val oldest = dayKey(System.currentTimeMillis(), HISTORY_DAYS)
        val stale = prefs.all.keys.filter {
            it.startsWith(HISTORY_PREFIX) && it.removePrefix(HISTORY_PREFIX).substringBefore(':') < oldest
        }
        if (stale.isNotEmpty()) prefs.edit().apply { stale.forEach(::remove) }.apply()
    }

    private fun readToday(key: String): Long {
        val raw = sp?.getString(key, null) ?: return 0
        return if (raw.substringBefore('|') == today()) raw.substringAfter('|').toLongOrNull() ?: 0 else 0
    }

    private fun writeToday(key: String, value: Long) {
        sp?.edit()?.putString(key, "${today()}|$value")?.apply()
    }
}

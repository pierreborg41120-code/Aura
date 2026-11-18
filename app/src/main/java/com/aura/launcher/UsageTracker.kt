package com.aura.launcher

import android.content.Context
import kotlin.math.pow

/**
 * Compteur d'utilisation local : chaque lancement d'app depuis Aura ajoute 1 au score de l'app,
 * et ce score fond avec le temps (il est divisé par deux tous les [HALF_LIFE_DAYS] jours).
 * Le score est donc une somme pondérée des lancements récents.
 *
 * Aucune permission : Aura ne compte que ce qui passe par lui. Tout reste dans un fichier
 * de préférences privé, rien ne quitte le téléphone.
 *
 * Stockage : une entrée par app, clé de l'app → "score|horodatage du dernier calcul".
 */
class UsageTracker(context: Context) {
    private val sp = context.getSharedPreferences("aura_usage", Context.MODE_PRIVATE)

    /** Note un lancement de l'app [key]. */
    fun record(key: String, now: Long = System.currentTimeMillis()) {
        val current = decayed(sp.getString(key, null), now)
        sp.edit().putString(key, encode(current + 1.0, now)).apply()
    }

    /** Scores actuels (décroissance appliquée). Oublie au passage les apps qui ne comptent plus. */
    fun scores(now: Long = System.currentTimeMillis()): Map<String, Double> {
        val result = HashMap<String, Double>()
        val stale = mutableListOf<String>()
        for ((key, value) in sp.all) {
            val score = decayed(value as? String, now)
            if (score < MIN_SCORE) stale += key else result[key] = score
        }
        if (stale.isNotEmpty()) {
            val editor = sp.edit()
            for (key in stale) editor.remove(key)
            editor.apply()
        }
        return result
    }

    /** Efface tout l'historique. */
    fun clear() {
        sp.edit().clear().apply()
    }

    private fun decayed(raw: String?, now: Long): Double {
        if (raw == null) return 0.0
        val sep = raw.indexOf('|')
        if (sep < 0) return 0.0
        val score = raw.substring(0, sep).toDoubleOrNull() ?: return 0.0
        val time = raw.substring(sep + 1).toLongOrNull() ?: return 0.0
        val elapsed = (now - time).coerceAtLeast(0L).toDouble()
        return score * 0.5.pow(elapsed / HALF_LIFE_MS)
    }

    private fun encode(score: Double, time: Long) = "$score|$time"

    private companion object {
        const val HALF_LIFE_DAYS = 7
        const val HALF_LIFE_MS = HALF_LIFE_DAYS * 24.0 * 60 * 60 * 1000
        /** En dessous (environ un lancement vieux de six semaines), l'app est oubliée. */
        const val MIN_SCORE = 0.02
    }
}

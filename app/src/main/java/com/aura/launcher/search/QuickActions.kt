package com.aura.launcher.search

import com.aura.launcher.AppEntry

/**
 * --- Actions rapides --- ce que la recherche sait faire directement :
 * « minuteur 10 », « minuteur 1h30 », « alarme 7h30 », « sms Julie », « appeler Julie ».
 * Les messages et appels ont besoin d'un contact : l'interface le cherche ensuite.
 */
sealed interface QuickAction {
    /** Minuteur de [seconds] secondes. */
    data class Timer(val seconds: Int) : QuickAction
    /** Alarme à [hour]:[minute]. */
    data class Alarm(val hour: Int, val minute: Int) : QuickAction
    /** Message à un contact : [who] est le texte tapé après « sms » (nom, puis message éventuel). */
    data class Sms(val who: String) : QuickAction
    /** Appel (le composeur s'ouvre, rien n'est composé sans toi). */
    data class Call(val who: String) : QuickAction
}

object QuickActions {

    private val timerWords = setOf("minuteur", "timer", "chrono", "compte a rebours")
    private val alarmWords = setOf("alarme", "reveil", "reveille moi", "reveille-moi", "alarm")
    private val smsWords = setOf("sms", "texto", "message", "msg")
    private val callWords = setOf("appeler", "appelle", "appel", "tel", "telephoner", "call")

    /** Action reconnue dans la saisie, ou null. */
    fun parse(query: String): QuickAction? {
        val text = AppEntry.normalize(query).replace(Regex("\\s+"), " ")
        val (word, rest) = splitCommand(text) ?: return null
        return when (word) {
            in timerWords -> parseDuration(rest)?.let { QuickAction.Timer(it) }
            in alarmWords -> parseTime(rest)?.let { (h, m) -> QuickAction.Alarm(h, m) }
            // Pour un contact, on garde la saisie d'origine (majuscules, accents, texte du message).
            in smsWords -> originalRest(query, word)?.let { QuickAction.Sms(it) }
            in callWords -> originalRest(query, word)?.let { QuickAction.Call(it) }
            else -> null
        }
    }

    /** Mot de commande (une ou plusieurs mots connus) et le reste. */
    private fun splitCommand(text: String): Pair<String, String>? {
        val all = timerWords + alarmWords + smsWords + callWords
        // Les commandes de plusieurs mots d'abord (« compte a rebours »).
        val word = all.sortedByDescending { it.length }.firstOrNull { text.startsWith("$it ") } ?: return null
        val rest = text.removePrefix(word).trim()
        return if (rest.isEmpty()) null else word to rest
    }

    /** Ce qui suit la commande dans la saisie d'origine. */
    private fun originalRest(query: String, word: String): String? {
        val words = word.split(' ').size
        val parts = query.trim().split(Regex("\\s+"), limit = words + 1)
        return parts.getOrNull(words)?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** « 10 », « 10 min », « 45 s », « 1h30 », « 1 h », « 2 heures ». Un nombre seul = des minutes. */
    fun parseDuration(text: String): Int? {
        val t = text.replace(" ", "")
        Regex("^(\\d{1,2})h(\\d{1,2})?(min|m)?$").find(t)?.let { m ->
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toIntOrNull() ?: 0
            return (h * 3600 + min * 60).takeIf { it > 0 && min < 60 }
        }
        Regex("^(\\d{1,3})(heures?|h)$").find(t)?.let { return it.groupValues[1].toInt() * 3600 }
        Regex("^(\\d{1,4})(secondes?|sec|s)$").find(t)?.let { return it.groupValues[1].toInt().takeIf { s -> s > 0 } }
        Regex("^(\\d{1,3})(minutes?|mins?|mn|m)?$").find(t)?.let { return (it.groupValues[1].toInt() * 60).takeIf { s -> s > 0 } }
        return null
    }

    /** « 7h30 », « 7:30 », « 7 h », « 07 30 », « 19h ». */
    fun parseTime(text: String): Pair<Int, Int>? {
        val t = text.replace(" ", "")
        val m = Regex("^(\\d{1,2})(?:h|:|\\.)?(\\d{2})?$").find(t) ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toIntOrNull() ?: 0
        return if (h in 0..23 && min in 0..59) h to min else null
    }

    /** « 10 min », « 1 h 30 », « 45 s ». */
    fun describeDuration(seconds: Int): String {
        val h = seconds / 3600
        val m = seconds % 3600 / 60
        val s = seconds % 60
        return buildList {
            if (h > 0) add("$h h")
            if (m > 0) add(if (h > 0) "%02d".format(m) else "$m min")
            if (s > 0) add("$s s")
        }.joinToString(" ")
    }
}

package com.aura.launcher

import android.content.ComponentName
import android.os.UserHandle
import java.text.Collator
import java.text.Normalizer

/** Une application lançable, telle qu'affichée dans le launcher. */
data class AppEntry(
    /** Identifiant stable : "paquet/activité#profil". Sert à mémoriser les favoris. */
    val key: String,
    /** Nom affiché (éventuellement renommé par l'utilisateur). */
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    /** Numéro de série du profil utilisateur (0 = profil principal). */
    val userSerial: Long,
    /** Vrai si l'app vient d'un profil professionnel. */
    val isWorkProfile: Boolean,
    /** Nom d'origine de l'app, avant tout renommage. */
    val originalLabel: String = label,
) {
    val packageName: String get() = component.packageName

    /** Clé utilisée pour associer les notifications à l'app. */
    val notificationKey: String get() = notificationKey(packageName, userSerial)

    val isRenamed: Boolean get() = label != originalLabel

    /** Libellé sans accents ni majuscules, pour la recherche et le tri. */
    val searchKey: String = normalize(label)

    /** Lettre de section dans la liste alphabétique ("#" pour chiffres et symboles). */
    val letter: String = searchKey.firstOrNull()
        ?.takeIf { it in 'a'..'z' }
        ?.uppercaseChar()?.toString()
        ?: "#"

    companion object {
        private val accents = "\\p{InCombiningDiacriticalMarks}+".toRegex()

        fun normalize(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFD)
                .replace(accents, "")
                .lowercase()
                .trim()

        fun notificationKey(packageName: String, userSerial: Long) = "$packageName|$userSerial"

        fun sort(apps: List<AppEntry>): List<AppEntry> {
            val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
            return apps.sortedWith { a, b -> collator.compare(a.searchKey, b.searchKey) }
        }
    }
}

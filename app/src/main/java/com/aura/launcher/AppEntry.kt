package com.aura.launcher

import android.content.ComponentName
import android.os.UserHandle
import java.text.Normalizer

/** Une application lançable, telle qu'affichée dans le launcher. */
data class AppEntry(
    /** Identifiant stable : "paquet/activité#profil". Sert à mémoriser les favoris. */
    val key: String,
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    /** Vrai si l'app vient d'un profil professionnel. */
    val isWorkProfile: Boolean,
) {
    val packageName: String get() = component.packageName

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
    }
}

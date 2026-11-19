package com.aura.launcher

/*
 * --- Gestes --- gestes de l'accueil dont l'action se choisit dans les réglages.
 * Glisser vers le haut ouvre toujours toutes les apps et l'appui long ouvre toujours les réglages :
 * ce sont les deux portes d'entrée d'Aura, elles ne se reprogramment pas.
 */

enum class Gesture(val label: String) {
    SWIPE_DOWN("Glisser vers le bas"),
    SWIPE_LEFT("Glisser vers la gauche"),
    SWIPE_RIGHT("Glisser vers la droite"),
    DOUBLE_TAP("Double-toucher"),
}

/** Actions possibles, enregistrées sous forme de texte : un nom fixe, ou « APP:<clé de l'app> ». */
object GestureAction {
    const val NONE = "NONE"
    const val NOTIFICATIONS = "NOTIFICATIONS"
    const val LOCK = "LOCK"
    const val SEARCH = "SEARCH"
    const val DRAWER = "DRAWER"
    const val APP_PREFIX = "APP:"

    /** Actions fixes proposées, dans l'ordre d'affichage (« Ouvrir une app… » est ajouté à part). */
    val fixed = listOf(
        NONE to "Rien",
        NOTIFICATIONS to "Notifications",
        LOCK to "Verrouiller l'écran",
        SEARCH to "Recherche",
        DRAWER to "Toutes les apps",
    )

    fun app(key: String) = APP_PREFIX + key

    /** Ce que faisait Aura avant ce réglage : rien ne change tant qu'on n'y touche pas. */
    fun default(gesture: Gesture): String = when (gesture) {
        Gesture.SWIPE_DOWN -> NOTIFICATIONS
        Gesture.DOUBLE_TAP -> LOCK
        Gesture.SWIPE_LEFT, Gesture.SWIPE_RIGHT -> NONE
    }
}

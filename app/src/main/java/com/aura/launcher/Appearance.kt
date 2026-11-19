package com.aura.launcher

/*
 * --- Apparence ---
 * Réglages visuels choisis dans l'éditeur « Apparence ». Valeurs par défaut = l'apparence d'avant ce
 * lot, pour que rien ne change tant que l'utilisateur ne touche à rien.
 */

/** Style de l'horloge d'accueil. */
enum class ClockStyle {
    /** Fine : l'horloge actuelle (défaut). */
    THIN,
    /** Empilée : heures au-dessus des minutes. */
    STACKED,
    /** En toutes lettres : « Vingt et une heures quarante-neuf ». */
    WORDS,
    /** Compacte : heure · date sur une seule ligne. */
    COMPACT,
    /** Néon : chiffres anguleux, halo et crochets, petit glitch au changement de minute (thème Néon). */
    NEON,
}

/**
 * --- Thèmes --- habillage complet : couleurs des surfaces, formes, détails graphiques.
 * Choisir un thème applique aussi un préréglage (police, horloge, accent, voile), modifiable ensuite.
 */
enum class ThemeId {
    /** Le thème d'origine : nuit, lavande, coins arrondis. */
    ECLIPSE,
    /** Nuit urbaine, jaune électrique et cyan, coins coupés. */
    NEON,
}

/** Police de l'accueil et du tiroir (les réglages gardent la police du système). */
enum class FontChoice { SYSTEM, MANROPE, INSTRUMENT_SERIF, SPACE_MONO, CHAKRA_PETCH }

/** Voile posé sur le fond d'écran. LIGHT = le voile d'avant ce lot (défaut). */
enum class VeilLevel { NONE, LIGHT, MEDIUM, STRONG }

/** Alignement de l'horloge, de la date, de la ligne de statut et des favoris. */
enum class HomeAlignment { START, CENTER, END }

/** Accent : [ACCENT_AUTO] (couleur tirée du fond d'écran) ou un indice dans la palette de 7 couleurs. */
const val ACCENT_AUTO = -1
const val ACCENT_PALETTE_SIZE = 7

data class Appearance(
    val theme: ThemeId = ThemeId.ECLIPSE,
    val clock: ClockStyle = ClockStyle.THIN,
    val font: FontChoice = FontChoice.SYSTEM,
    /** [ACCENT_AUTO] ou 0 until [ACCENT_PALETTE_SIZE]. */
    val accent: Int = ACCENT_AUTO,
    val veil: VeilLevel = VeilLevel.LIGHT,
    /** Flou du fond d'écran à l'ouverture du tiroir (Android 12+). */
    val blurDrawer: Boolean = false,
    val alignment: HomeAlignment = HomeAlignment.START,
)

/** Préréglage appliqué quand on choisit un thème (les autres réglages sont conservés). */
fun Appearance.withTheme(theme: ThemeId): Appearance = when (theme) {
    ThemeId.ECLIPSE -> copy(
        theme = theme,
        clock = if (clock == ClockStyle.NEON) ClockStyle.THIN else clock,
        font = if (font == FontChoice.CHAKRA_PETCH) FontChoice.SYSTEM else font,
        accent = ACCENT_AUTO,
        veil = VeilLevel.LIGHT,
    )
    ThemeId.NEON -> copy(
        theme = theme,
        clock = ClockStyle.NEON,
        font = FontChoice.CHAKRA_PETCH,
        accent = ACCENT_AUTO,
        veil = VeilLevel.MEDIUM,
    )
}

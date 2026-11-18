package com.aura.launcher.ui

/*
 * Direction visuelle d'Aura : « l'éclipse ».
 *
 * 1. Le texte est la lumière : blanc lunaire légèrement bleuté sur la nuit (#0B0B10), en trois
 *    intensités seulement (plein, atténué, effacé). Pas de gris intermédiaires inventés.
 * 2. La lavande (#8C9BFF) est le halo de l'icône : elle signale (notification, lettre active,
 *    interrupteur, action principale), elle ne décore jamais. Un seul halo par zone.
 * 3. Motif récurrent : l'anneau. Point de notification cerclé d'un halo, étapes de l'assistant en
 *    anneau, bulle de lettre cerclée : le même geste graphique que l'icône, en tout petit.
 * 4. Une seule famille (sans-serif du système), hiérarchie par la graisse : l'horloge très fine et
 *    serrée, les noms d'apps légers, les libellés en graisse moyenne. Échelle d'environ 1,25.
 * 5. Lisible sur n'importe quel fond d'écran : ombre de texte douce sur tout ce qui est posé sur
 *    le fond (heure, date, statut, favoris), jamais de pavé opaque derrière.
 * 6. Mouvement calme : courbes qui décélèrent, 140 / 240 / 360 ms ; la seule animation spontanée
 *    est la « respiration » du chevron d'accueil. Rien ne bouge si les animations sont coupées.
 * 7. Surfaces sobres : une couleur de carte, un filet à 10 %, des rayons qui grandissent avec la
 *    taille de l'objet (petit bouton 12, champ 16, carte 28).
 */

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Couleurs d'Aura. Les noms historiques (Text, TextDim…) sont conservés : d'autres fichiers s'en servent. */
object AuraColors {
    /** Fond nuit de l'icône. */
    val Night = Color(0xFF0B0B10)
    /** Texte plein : blanc lunaire, à peine bleuté. */
    val Text = Color(0xFFF4F4FA)
    val TextDim = Color(0xB3F4F4FA)
    val TextFaint = Color(0x66F4F4FA)
    /** Halo lavande de l'icône. */
    val Accent = Color(0xFF8C9BFF)
    /** Lavande éclaircie, vers le blanc du dégradé de l'icône (texte sur fond sombre, survols). */
    val AccentLight = Color(0xFFC6CDFF)
    /** Texte posé sur la lavande. */
    val OnAccent = Night
    /** Voile des écrans plein cadre (tiroir, réglages, assistant). */
    val DrawerScrim = Color(0xF00B0B10)
    /** Voile sous la carte de l'app. */
    val SheetScrim = Color(0x8C05050A)
    /** Cartes, champs, menus. */
    val Surface = Color(0xFF16161F)
    /** Élément posé sur une carte (aperçu de widget, bouton secondaire). */
    val SurfaceRaised = Color(0xFF20202B)
    /** Filet discret (bordures, séparateurs). */
    val Hairline = Color(0x1AF4F4FA)
    /** Voile en dégradé posé sur le fond d'écran (plus dense en haut et en bas). */
    val WallpaperVeil = listOf(Color(0x73000000), Color(0x1A000000), Color(0x1A000000), Color(0x8C000000))
    /** Ombre portée du texte posé sur le fond d'écran. */
    val TextShadow = Color(0x8C000000)
}

/** Espacements (dp). */
object AuraSpace {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
    /** Marge latérale de l'accueil et du tiroir. */
    val gutter = 28.dp
    /** Hauteur minimale d'une zone de toucher. */
    val touch = 48.dp
}

/** Rayons des coins (dp) : ils grandissent avec la taille de l'objet. */
object AuraRadius {
    val s = 12.dp
    val m = 16.dp
    val l = 20.dp
    val xl = 28.dp
}

/** Durées (ms) et courbes d'animation. */
object AuraMotion {
    const val quick = 140
    const val standard = 240
    const val gentle = 360
    /** Cycle de respiration du chevron d'accueil. */
    const val breath = 2800

    /** Entrée : démarre vite et se pose doucement. */
    val enter: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    /** Sortie : part doucement et accélère. */
    val exit: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
}

/** Ombres de texte pour rester lisible sur un fond d'écran clair ou coloré. */
object AuraShadow {
    // Valeurs en pixels (Shadow ne connaît pas les dp) : réglées pour un écran ~2,6x (1080 px de large).
    /** Pour les petits textes (date, statut, favoris). */
    val soft = Shadow(color = AuraColors.TextShadow, offset = Offset(0f, 3f), blurRadius = 12f)
    /** Pour l'horloge : plus large, très diffuse. */
    val wide = Shadow(color = AuraColors.TextShadow, offset = Offset(0f, 4f), blurRadius = 28f)
}

/** Échelle typographique (sp), une seule famille : hiérarchie par la taille et la graisse. */
object AuraType {
    private val family = FontFamily.SansSerif

    val clock = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.ExtraLight,
        fontSize = 76.sp,
        lineHeight = 84.sp,
        letterSpacing = (-2).sp,
    )
    val screenTitle = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.ExtraLight,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp,
    )
    val favorite = TextStyle(fontFamily = family, fontWeight = FontWeight.Light, fontSize = 24.sp, letterSpacing = 0.sp)
    val row = TextStyle(fontFamily = family, fontWeight = FontWeight.Light, fontSize = 19.sp, letterSpacing = 0.1.sp)
    val date = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 17.sp, letterSpacing = 0.2.sp)
    val body = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp)
    val bodySmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
    val label = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.3.sp,
    )
    val caption = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 12.5.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp)
    val status = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp)
    val letter = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.5.sp)
    val letterBar = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 10.5.sp)
    val button = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 15.sp, letterSpacing = 0.2.sp)
}

/** Applique le réglage « Taille du texte » à un style (taille et interligne). */
fun TextStyle.scaled(scale: Float): TextStyle = if (scale == 1f) this else copy(
    fontSize = fontSize * scale,
    lineHeight = if (lineHeight == TextUnit.Unspecified) lineHeight else lineHeight * scale,
)

/** Ajoute l'ombre de lisibilité (texte posé directement sur le fond d'écran). */
fun TextStyle.onWallpaper(shadow: Shadow = AuraShadow.soft): TextStyle = copy(shadow = shadow)

/** Vrai si l'utilisateur a coupé les animations (Options pour les développeurs / Accessibilité). */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (e: Exception) {
            false
        }
    }
}

/** Couleurs communes des interrupteurs (réglages, assistant). */
@Composable
fun auraSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = AuraColors.Night,
    checkedTrackColor = AuraColors.Accent,
    checkedBorderColor = AuraColors.Accent,
    uncheckedThumbColor = AuraColors.TextFaint,
    uncheckedTrackColor = AuraColors.Surface,
    uncheckedBorderColor = AuraColors.Hairline,
)

/**
 * Point de notification « éclipse » : un disque lavande entouré d'un fin halo, comme l'icône.
 * [size] est le diamètre du halo ; le disque en fait un peu plus de la moitié.
 */
@Composable
fun HaloDot(size: Dp = 12.dp, color: Color = AuraColors.Accent, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        drawCircle(color.copy(alpha = 0.28f), radius = r - 0.5.dp.toPx(), style = Stroke(1.dp.toPx()))
        drawCircle(color, radius = r * 0.5f)
    }
}

/** Chevron dessiné (vers la droite par défaut) : pour les lignes qui ouvrent une autre page. */
@Composable
fun ChevronGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = 12.dp) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val stroke = 1.5.dp.toPx()
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.35f, h * 0.15f)
                lineTo(w * 0.7f, h * 0.5f)
                lineTo(w * 0.35f, h * 0.85f)
            }
            drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
    }
}

@Composable
fun AuraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AuraColors.Accent,
            onPrimary = AuraColors.OnAccent,
            surface = AuraColors.Surface,
            surfaceContainer = AuraColors.Surface,
            surfaceContainerHigh = AuraColors.Surface,
            surfaceContainerHighest = AuraColors.SurfaceRaised,
            onSurface = AuraColors.Text,
            onSurfaceVariant = AuraColors.TextDim,
            outline = AuraColors.TextFaint,
            outlineVariant = AuraColors.Hairline,
            background = Color.Transparent,
        ),
        // Menus d'appui long et boîtes de dialogue : mêmes coins arrondis que le reste d'Aura.
        shapes = Shapes(
            extraSmall = RoundedCornerShape(AuraRadius.m),
            small = RoundedCornerShape(AuraRadius.s),
            medium = RoundedCornerShape(AuraRadius.m),
            large = RoundedCornerShape(AuraRadius.l),
            extraLarge = RoundedCornerShape(AuraRadius.xl),
        ),
        content = content,
    )
}

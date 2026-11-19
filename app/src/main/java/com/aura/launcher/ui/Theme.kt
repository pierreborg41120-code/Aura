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
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import com.aura.launcher.ThemeId
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import com.aura.launcher.Appearance
import com.aura.launcher.FontChoice
import com.aura.launcher.HomeAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.core.graphics.ColorUtils
import com.aura.launcher.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Couleurs d'Aura. Les noms historiques (Text, TextDim…) sont conservés : d'autres fichiers s'en servent. */
object AuraColors {
    // --- Thèmes --- les couleurs des surfaces viennent du thème en cours (AuraLook.spec) :
    // ce sont des getters sur un état Compose, tout se redessine quand le thème change.
    /** Fond nuit. */
    val Night: Color get() = AuraLook.spec.night
    /** Texte plein. */
    val Text: Color get() = AuraLook.spec.text
    val TextDim: Color get() = AuraLook.spec.textDim
    val TextFaint: Color get() = AuraLook.spec.textFaint
    /** Halo lavande de l'icône : l'accent par défaut du thème Éclipse. */
    val Lavender = Color(0xFF8C9BFF)
    /**
     * --- Apparence --- accent en cours (thème, palette ou couleur du fond d'écran).
     * Lu depuis un état Compose : tout ce qui l'affiche se redessine quand il change.
     */
    val Accent: Color get() = AuraAccent.color
    /** Accent éclairci vers le blanc (texte sur fond sombre, survols). */
    val AccentLight: Color get() = lerp(AuraAccent.color, Color.White, 0.55f)
    /** Couleur secondaire du thème (cyan pour Néon, accent éclairci pour Éclipse). */
    val Secondary: Color get() = AuraLook.spec.secondary ?: AccentLight
    /** Texte posé sur l'accent. */
    val OnAccent: Color get() = AuraLook.spec.night
    /** Voile des écrans plein cadre (tiroir, réglages, assistant). */
    val DrawerScrim: Color get() = AuraLook.spec.drawerScrim
    /** Voile sous la carte de l'app. */
    val SheetScrim: Color get() = AuraLook.spec.sheetScrim
    /** Cartes, champs, menus. */
    val Surface: Color get() = AuraLook.spec.surface
    /** Élément posé sur une carte (aperçu de widget, bouton secondaire). */
    val SurfaceRaised: Color get() = AuraLook.spec.surfaceRaised
    /** Filet discret (bordures, séparateurs). */
    val Hairline: Color get() = AuraLook.spec.hairline
    /** Voile en dégradé posé sur le fond d'écran (plus dense en haut et en bas) : niveau « Léger », par défaut. */
    val WallpaperVeil = listOf(Color(0x73000000), Color(0x1A000000), Color(0x1A000000), Color(0x8C000000))
    /** Ombre portée du texte posé sur le fond d'écran. */
    val TextShadow: Color get() = AuraLook.spec.textShadow
}

// --- Thèmes ---

/** Tout ce qui change d'un thème à l'autre. */
data class ThemeSpec(
    val id: ThemeId,
    val night: Color,
    val text: Color,
    val textDim: Color,
    val textFaint: Color,
    val drawerScrim: Color,
    val sheetScrim: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val hairline: Color,
    val textShadow: Color,
    /** Accent quand le réglage Couleur est sur « Auto » (null = couleur du fond d'écran). */
    val themeAccent: Color?,
    /** Couleur secondaire des détails (null = accent éclairci). */
    val secondary: Color?,
    /** Coins coupés en biseau au lieu d'arrondis. */
    val cutCorners: Boolean,
    /** Fines lignes de balayage (immobiles) sur les écrans plein cadre. */
    val scanlines: Boolean,
)

/** « Éclipse » : le thème d'origine (valeurs inchangées). */
val EclipseSpec = ThemeSpec(
    id = ThemeId.ECLIPSE,
    night = Color(0xFF0B0B10),
    text = Color(0xFFF4F4FA),
    textDim = Color(0xB3F4F4FA),
    textFaint = Color(0x66F4F4FA),
    drawerScrim = Color(0xF00B0B10),
    sheetScrim = Color(0x8C05050A),
    surface = Color(0xFF16161F),
    surfaceRaised = Color(0xFF20202B),
    hairline = Color(0x1AF4F4FA),
    textShadow = Color(0x8C000000),
    themeAccent = null,
    secondary = null,
    cutCorners = false,
    scanlines = false,
)

/** Jaune électrique du thème Néon. */
val NeonYellow = Color(0xFFFCEE0A)
/** Cyan du thème Néon. */
val NeonCyan = Color(0xFF00E5FF)

/** « Néon » : nuit urbaine bleutée, jaune électrique, détails cyan, coins coupés. */
val NeonSpec = ThemeSpec(
    id = ThemeId.NEON,
    night = Color(0xFF07090F),
    text = Color(0xFFEAF7FF),
    textDim = Color(0xB3EAF7FF),
    textFaint = Color(0x66EAF7FF),
    drawerScrim = Color(0xF207090F),
    sheetScrim = Color(0x9903050A),
    surface = Color(0xFF0F1420),
    surfaceRaised = Color(0xFF172031),
    hairline = Color(0x3300E5FF),
    textShadow = Color(0x99000000),
    themeAccent = NeonYellow,
    secondary = NeonCyan,
    cutCorners = true,
    scanlines = true,
)

fun specFor(theme: ThemeId): ThemeSpec = when (theme) {
    ThemeId.ECLIPSE -> EclipseSpec
    ThemeId.NEON -> NeonSpec
}

/** Thème en cours, écrit par [AuraTheme]. */
object AuraLook {
    var spec by mutableStateOf(EclipseSpec)
}

/** Forme d'une surface : arrondie (Éclipse) ou en biseau (Néon). Le biseau est plus petit que l'arrondi. */
fun auraShape(radius: Dp): CornerBasedShape =
    if (AuraLook.spec.cutCorners) CutCornerShape(radius * 0.45f) else RoundedCornerShape(radius)

fun auraShape(topStart: Dp = 0.dp, topEnd: Dp = 0.dp): CornerBasedShape =
    if (AuraLook.spec.cutCorners) CutCornerShape(topStart = topStart * 0.45f, topEnd = topEnd * 0.45f)
    else RoundedCornerShape(topStart = topStart, topEnd = topEnd)

/**
 * Fond des écrans plein cadre (tiroir, réglages…) : le voile du thème, plus de fines lignes de
 * balayage immobiles pour le thème Néon.
 */
fun Modifier.themeBackdrop(): Modifier = this
    .background(AuraColors.DrawerScrim)
    .drawBehind {
        if (AuraLook.spec.scanlines) {
            val step = 3.dp.toPx()
            val line = NeonCyan.copy(alpha = 0.035f)
            var y = 0f
            while (y < size.height) {
                drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += step
            }
        }
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
    val soft: Shadow get() = Shadow(color = AuraColors.TextShadow, offset = Offset(0f, 3f), blurRadius = 12f)
    /** Pour l'horloge : plus large, très diffuse. */
    val wide: Shadow get() = Shadow(color = AuraColors.TextShadow, offset = Offset(0f, 4f), blurRadius = 28f)
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

/** --- Icônes --- style d'icônes en cours, d'après l'apparence et le thème. */
@Composable
fun rememberIconLook(): com.aura.launcher.IconLook {
    val a = LocalAppearance.current
    return when (a.iconStyle) {
        com.aura.launcher.IconStyle.ORIGINAL -> com.aura.launcher.IconLook.ORIGINAL
        com.aura.launcher.IconStyle.PACK ->
            if (a.iconPack.isBlank()) com.aura.launcher.IconLook.ORIGINAL
            else com.aura.launcher.IconLook(com.aura.launcher.IconStyle.PACK, pack = a.iconPack)
        com.aura.launcher.IconStyle.THEME -> com.aura.launcher.IconLook(
            style = com.aura.launcher.IconStyle.THEME,
            glyph = AuraColors.Accent.toArgb(),
            line = AuraColors.Secondary.toArgb(),
            frame = AuraColors.Surface.toArgb(),
            cutCorners = AuraLook.spec.cutCorners,
        )
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

// --- Apparence ---

/** Accent en cours, écrit par [AuraTheme]. */
object AuraAccent {
    var color by mutableStateOf(AuraColors.Lavender)
}

/**
 * Palette de l'accent : 7 couleurs, nom affiché + couleur. L'indice est celui enregistré dans Appearance.accent.
 * Toutes assez claires pour être lues sur le fond nuit et servir de halo.
 */
val AccentPalette: List<Pair<String, Color>> = listOf(
    "Lavande" to Color(0xFF8C9BFF),
    "Glacier" to Color(0xFF7CC8F0),
    "Menthe" to Color(0xFF7FD9B4),
    "Ambre" to Color(0xFFF2C46D),
    "Corail" to Color(0xFFFF8A7A),
    "Rose" to Color(0xFFF28DC4),
    "Lune" to Color(0xFFE4E4F0),
)

/** Apparence en cours (réglages de l'éditeur « Apparence »), fournie par [AuraTheme]. */
val LocalAppearance = staticCompositionLocalOf { Appearance() }

/**
 * Accent à afficher. [accent] vaut ACCENT_AUTO ou un indice de palette ; [wallpaperColor] est la
 * couleur brute du fond d'écran (null si inconnue → lavande).
 */
fun resolveAccent(accent: Int, wallpaperColor: Int?, theme: ThemeId = ThemeId.ECLIPSE): Color {
    if (accent in AccentPalette.indices) return AccentPalette[accent].second
    // --- Thèmes --- un thème qui a sa propre couleur (Néon : jaune) la garde en « Auto ».
    specFor(theme).themeAccent?.let { return it }
    return wallpaperAccent(wallpaperColor) ?: AuraColors.Lavender
}

/**
 * Couleur du fond d'écran retouchée pour servir de halo sur la nuit : on garde la teinte, on impose
 * une saturation modérée et une clarté élevée (comme la lavande), puis on vérifie le contraste.
 * Null si la couleur est inconnue ou quasi grise (la teinte n'aurait alors pas de sens).
 */
private fun wallpaperAccent(argb: Int?): Color? {
    if (argb == null) return null
    val r = android.graphics.Color.red(argb)
    val g = android.graphics.Color.green(argb)
    val b = android.graphics.Color.blue(argb)
    // Écart entre la composante la plus forte et la plus faible : quasi nul pour un gris, un noir ou un blanc.
    val chroma = (maxOf(r, g, b) - minOf(r, g, b)) / 255f
    if (chroma < 0.08f) return null

    val hsl = FloatArray(3)
    ColorUtils.RGBToHSL(r, g, b, hsl)
    // Saturation modérée : ni pastel délavé ni couleur criarde.
    hsl[1] = hsl[1].coerceIn(0.45f, 0.75f)
    // Clarté élevée, proche de celle de la lavande ; les bleus profonds sont éclaircis juste après.
    hsl[2] = 0.74f
    var color = ColorUtils.HSLToColor(hsl)
    // Contraste minimal avec le fond nuit (la lavande est vers 8:1) : on éclaircit si besoin.
    val night = AuraColors.Night.toArgb()
    while (ColorUtils.calculateContrast(color, night) < 7.0 && hsl[2] < 0.9f) {
        hsl[2] += 0.02f
        color = ColorUtils.HSLToColor(hsl)
    }
    return Color(color)
}

/** Manrope (variable) : une instance par graisse utilisée dans Aura. */
@OptIn(ExperimentalTextApi::class)
private val Manrope = FontFamily(
    listOf(FontWeight.ExtraLight, FontWeight.Light, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold).map { w ->
        Font(
            R.font.manrope_variable,
            weight = w,
            variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)),
        )
    },
)

/** Instrument Serif : une seule graisse (pas de faux gras, l'horloge fine reste élégante). */
private val InstrumentSerif = FontFamily(Font(R.font.instrument_serif_regular, FontWeight.Normal))

/** Space Mono : régulier et gras. */
private val SpaceMono = FontFamily(
    Font(R.font.space_mono_regular, FontWeight.Normal),
    Font(R.font.space_mono_bold, FontWeight.Bold),
)

/** Chakra Petch (thème Néon) : anguleuse, quatre graisses. */
private val ChakraPetch = FontFamily(
    Font(R.font.chakra_petch_light, FontWeight.Light),
    Font(R.font.chakra_petch_light, FontWeight.ExtraLight),
    Font(R.font.chakra_petch_regular, FontWeight.Normal),
    Font(R.font.chakra_petch_medium, FontWeight.Medium),
    Font(R.font.chakra_petch_semibold, FontWeight.SemiBold),
    Font(R.font.chakra_petch_semibold, FontWeight.Bold),
)

/** Famille de police de l'accueil et du tiroir (les réglages gardent celle du système). */
fun fontFamilyFor(choice: FontChoice): FontFamily = when (choice) {
    FontChoice.SYSTEM -> FontFamily.SansSerif
    FontChoice.MANROPE -> Manrope
    FontChoice.INSTRUMENT_SERIF -> InstrumentSerif
    FontChoice.SPACE_MONO -> SpaceMono
    FontChoice.CHAKRA_PETCH -> ChakraPetch
}

/**
 * Facteur de taille par police, pour que toutes paraissent à peu près de la même taille :
 * Space Mono est large (chasse fixe), Instrument Serif a un petit œil.
 */
fun fontSizeFactor(choice: FontChoice): Float = when (choice) {
    FontChoice.SYSTEM, FontChoice.MANROPE, FontChoice.CHAKRA_PETCH -> 1f
    FontChoice.INSTRUMENT_SERIF -> 1.08f
    FontChoice.SPACE_MONO -> 0.86f
}

/** Police choisie dans l'apparence, pour l'accueil et le tiroir. */
@Composable
fun auraHomeFont(): FontFamily = fontFamilyFor(LocalAppearance.current.font)

/**
 * Applique la police de l'apparence (accueil et tiroir seulement), avec son facteur de taille
 * ([fontSizeFactor] : 1 pour la police du système, donc rien ne change par défaut).
 */
@Composable
fun TextStyle.homeFont(): TextStyle {
    val choice = LocalAppearance.current.font
    return copy(fontFamily = fontFamilyFor(choice)).scaled(fontSizeFactor(choice))
}

/** Alignement horizontal correspondant (pour Column / Box). */
fun HomeAlignment.horizontal(): Alignment.Horizontal = when (this) {
    HomeAlignment.START -> Alignment.Start
    HomeAlignment.CENTER -> Alignment.CenterHorizontally
    HomeAlignment.END -> Alignment.End
}

/** Alignement du texte correspondant (textes sur plusieurs lignes). */
fun HomeAlignment.textAlign(): TextAlign = when (this) {
    HomeAlignment.START -> TextAlign.Start
    HomeAlignment.CENTER -> TextAlign.Center
    HomeAlignment.END -> TextAlign.End
}

@Composable
fun AuraTheme(
    appearance: Appearance = Appearance(),
    wallpaperColor: Int? = null,
    content: @Composable () -> Unit,
) {
    val accent = remember(appearance.accent, wallpaperColor, appearance.theme) {
        resolveAccent(appearance.accent, wallpaperColor, appearance.theme)
    }
    // Écrits pendant la composition de la racine : tout ce qui est composé en dessous voit déjà
    // le bon thème (pas d'image intermédiaire aux couleurs d'Éclipse au démarrage).
    val spec = specFor(appearance.theme)
    if (AuraLook.spec != spec) AuraLook.spec = spec
    if (AuraAccent.color != accent) AuraAccent.color = accent
    CompositionLocalProvider(LocalAppearance provides appearance) {
        AuraMaterialTheme(accent, content)
    }
}

@Composable
private fun AuraMaterialTheme(accent: Color, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = accent,
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
            extraSmall = auraShape(AuraRadius.m),
            small = auraShape(AuraRadius.s),
            medium = auraShape(AuraRadius.m),
            large = auraShape(AuraRadius.l),
            extraLarge = auraShape(AuraRadius.xl),
        ),
        content = content,
    )
}

package com.aura.launcher

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * --- Icônes --- icônes fabriquées aux couleurs du thème : une silhouette de l'app, colorée à
 * l'accent avec un léger halo, posée dans un cadre du thème (biseauté pour Néon, arrondi sinon).
 */

/** Comment dessiner les icônes. Sert aussi de clé de cache : tout changement redessine les icônes. */
data class IconLook(
    val style: IconStyle,
    /** Paquet du pack d'icônes (style PACK). */
    val pack: String = "",
    /** Couleur de la silhouette (accent du thème), ARGB. */
    val glyph: Int = 0,
    /** Couleur du filet du cadre, ARGB. */
    val line: Int = 0,
    /** Fond du cadre, ARGB. */
    val frame: Int = 0,
    /** Coins coupés (Néon) plutôt qu'arrondis. */
    val cutCorners: Boolean = false,
    /** Coins presque droits (Terminal). */
    val squareCorners: Boolean = false,
) {
    /** Jeton court pour la clé de cache. */
    val token: String get() = "$style:$pack:$glyph:$line:$frame:$cutCorners:$squareCorners"

    companion object {
        val ORIGINAL = IconLook(IconStyle.ORIGINAL)
    }
}

/** Dessine l'icône « thème » de [icon] en [size] px. */
fun renderThemedIcon(icon: Drawable, size: Int, look: IconLook): Bitmap {
    val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val s = size.toFloat()

    // Cadre : fond nuit presque opaque + filet de la couleur de détail.
    val stroke = (s * 0.035f).coerceAtLeast(1f)
    val rect = RectF(stroke, stroke, s - stroke, s - stroke)
    val frame = framePath(rect, look.cutCorners, look.squareCorners, s)
    canvas.drawPath(frame, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(look.frame, 0.92f) })
    canvas.drawPath(frame, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        color = withAlpha(look.line, 0.75f)
    })

    // Silhouette au centre (≈ 60 % du cadre), avec halo puis trait net.
    val glyphSize = (s * 0.6f).roundToInt().coerceAtLeast(8)
    val mask = silhouette(icon, glyphSize)
    val offset = (s - glyphSize) / 2f
    val tint = PorterDuffColorFilter(look.glyph, PorterDuff.Mode.SRC_IN)
    canvas.drawBitmap(mask, offset, offset, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = tint
        maskFilter = BlurMaskFilter(s * 0.07f, BlurMaskFilter.Blur.NORMAL)
        alpha = 150
    })
    canvas.drawBitmap(mask, offset, offset, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = tint
    })
    mask.recycle()
    return out
}

/** Contour du cadre : biseau aux quatre coins, ou coins arrondis. */
private fun framePath(r: RectF, cut: Boolean, square: Boolean, s: Float): Path = Path().apply {
    if (square) {
        addRoundRect(r, s * 0.04f, s * 0.04f, Path.Direction.CW)
    } else if (cut) {
        val c = s * 0.2f
        moveTo(r.left + c, r.top)
        lineTo(r.right - c, r.top)
        lineTo(r.right, r.top + c)
        lineTo(r.right, r.bottom - c)
        lineTo(r.right - c, r.bottom)
        lineTo(r.left + c, r.bottom)
        lineTo(r.left, r.bottom - c)
        lineTo(r.left, r.top + c)
        close()
    } else {
        addRoundRect(r, s * 0.28f, s * 0.28f, Path.Direction.CW)
    }
}

/**
 * Silhouette blanche (seul l'alpha compte) de l'icône, en [size] px.
 * 1. Android 13+ : la version monochrome fournie par l'app (icônes thématiques du système).
 * 2. Icône adaptative : le calque de premier plan, s'il est découpé (pas un carré plein).
 * 3. Sinon : silhouette calculée d'après le contraste avec la couleur de fond de l'icône.
 */
private fun silhouette(icon: Drawable, size: Int): Bitmap {
    if (icon is AdaptiveIconDrawable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            icon.monochrome?.let { return drawLayer(it, size) }
        }
        icon.foreground?.let { fg ->
            val layer = drawLayer(fg, size)
            if (opaqueRatio(layer) < 0.8f) return layer
            val full = drawFull(icon, size)
            layer.recycle()
            return contrastMask(full)
        }
    }
    return contrastMask(drawFull(icon, size))
}

/**
 * Dessine un calque d'icône adaptative : il fait 108 dp dont seuls les 72 du centre sont visibles,
 * on l'agrandit donc de 25 % de chaque côté pour que la partie visible remplisse [size].
 */
private fun drawLayer(layer: Drawable, size: Int): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val extra = size / 4
    layer.setBounds(-extra, -extra, size + extra, size + extra)
    layer.draw(Canvas(bmp))
    return bmp
}

private fun drawFull(icon: Drawable, size: Int): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    icon.setBounds(0, 0, size, size)
    icon.draw(Canvas(bmp))
    return bmp
}

/** Part des pixels quasi opaques (un premier plan « plein » ne fait pas une bonne silhouette). */
private fun opaqueRatio(bmp: Bitmap): Float {
    val px = IntArray(bmp.width * bmp.height)
    bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    return px.count { (it ushr 24) > 230 }.toFloat() / px.size
}

/**
 * Silhouette calculée : la couleur de fond est estimée sur le pourtour de la zone opaque, puis
 * chaque pixel devient d'autant plus visible qu'il s'en éloigne (en luminosité). Remplace [src].
 */
private fun contrastMask(src: Bitmap): Bitmap {
    val w = src.width
    val h = src.height
    val px = IntArray(w * h)
    src.getPixels(px, 0, w, 0, 0, w, h)

    // Fond : luminosité moyenne des pixels opaques sur un anneau à ~15 % du bord.
    var sum = 0f
    var n = 0
    val m = (w * 0.15f).roundToInt()
    for (i in 0 until w) {
        for (p in intArrayOf(m * w + i, (h - 1 - m) * w + i, i * w + m, i * w + (w - 1 - m))) {
            if (p in px.indices && (px[p] ushr 24) > 200) { sum += lum(px[p]); n++ }
        }
    }
    val bg = if (n > 0) sum / n else -1f

    for (i in px.indices) {
        val a = px[i] ushr 24
        val v = if (bg < 0f) a / 255f // pas de fond opaque : la forme elle-même
        else (abs(lum(px[i]) - bg) * 3f).coerceIn(0f, 1f) * (a / 255f)
        px[i] = ((v * 255).roundToInt() shl 24) or 0xFFFFFF
    }
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    out.setPixels(px, 0, w, 0, 0, w, h)
    src.recycle()
    return out
}

private fun lum(c: Int): Float =
    (0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)) / 255f

private fun withAlpha(c: Int, a: Float): Int = ((a * 255).roundToInt() shl 24) or (c and 0xFFFFFF)

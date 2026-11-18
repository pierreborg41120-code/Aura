package com.aura.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aura.launcher.WeatherKind
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pictogramme météo dessiné à la main (trait fin, comme la pile de la ligne de statut) :
 * soleil, lune, nuage, pluie, neige, orage, brouillard.
 * [onWallpaper] ajoute une ombre douce pour rester lisible sur le fond d'écran.
 */
@Composable
fun WeatherGlyph(
    kind: WeatherKind,
    isDay: Boolean,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    onWallpaper: Boolean = false,
) {
    Canvas(modifier.size(size)) {
        // Le dessin est pensé sur une grille de 24 × 24.
        val u = this.size.minDimension / 24f
        // Trait un peu plus épais en petit, pour ne pas disparaître.
        val stroke = (if (size < 20.dp) 1.25.dp else 1.5.dp).toPx()
        if (onWallpaper) {
            translate(top = 1.dp.toPx()) {
                drawWeather(kind, isDay, AuraColors.TextShadow.copy(alpha = 0.3f), u, stroke * 2f)
            }
        }
        drawWeather(kind, isDay, color, u, stroke)
    }
}

private fun DrawScope.drawWeather(kind: WeatherKind, isDay: Boolean, color: Color, u: Float, stroke: Float) {
    val line = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun p(x: Float, y: Float) = Offset(x * u, y * u)

    when (kind) {
        WeatherKind.CLEAR ->
            if (isDay) drawSun(12f, 12f, 4.5f, 7.5f, 10f, color, u, line)
            else drawPath(moonPath(12f, 12f, 7.5f, u), color, style = line)

        WeatherKind.PARTLY -> {
            val cloud = cloudPath(u, dx = 2f, dy = 2.5f)
            // L'astre passe derrière le nuage : on ne le dessine pas sous son contour.
            clipPath(cloud, ClipOp.Difference) {
                if (isDay) drawSun(8.5f, 8.5f, 3.2f, 5.2f, 6.8f, color, u, line)
                else drawPath(moonPath(8.5f, 8.5f, 5f, u), color, style = line)
            }
            drawPath(cloud, color, style = line)
        }

        WeatherKind.CLOUDY -> drawPath(cloudPath(u, dx = 0f, dy = 0f), color, style = line)

        WeatherKind.FOG -> {
            drawLine(color, p(4f, 8f), p(20f, 8f), stroke, StrokeCap.Round)
            drawLine(color, p(3f, 12f), p(17f, 12f), stroke, StrokeCap.Round)
            drawLine(color, p(6f, 16f), p(21f, 16f), stroke, StrokeCap.Round)
            drawLine(color, p(5f, 20f), p(15f, 20f), stroke, StrokeCap.Round)
        }

        WeatherKind.DRIZZLE -> {
            drawPath(cloudPath(u, dx = 0f, dy = -4f), color, style = line)
            listOf(8f, 12f, 16f).forEach { x ->
                drawLine(color, p(x, 18.5f), p(x - 0.5f, 20f), stroke, StrokeCap.Round)
            }
        }

        WeatherKind.RAIN -> {
            drawPath(cloudPath(u, dx = 0f, dy = -4f), color, style = line)
            listOf(8f, 12f, 16f).forEach { x ->
                drawLine(color, p(x, 18f), p(x - 1.2f, 21.5f), stroke, StrokeCap.Round)
            }
        }

        WeatherKind.SNOW -> {
            drawPath(cloudPath(u, dx = 0f, dy = -4f), color, style = line)
            val r = 1.1f * u
            drawCircle(color, r, p(8f, 19f))
            drawCircle(color, r, p(12f, 21.5f))
            drawCircle(color, r, p(16f, 19f))
        }

        WeatherKind.STORM -> {
            drawPath(cloudPath(u, dx = 0f, dy = -4f), color, style = line)
            val bolt = Path().apply {
                moveTo(13f * u, 16.5f * u)
                lineTo(10.5f * u, 20f * u)
                lineTo(13.5f * u, 20f * u)
                lineTo(11f * u, 23.5f * u)
            }
            drawPath(bolt, color, style = line)
        }
    }
}

/** Soleil : disque cerclé et 8 rayons. */
private fun DrawScope.drawSun(
    cx: Float, cy: Float, r: Float, rayFrom: Float, rayTo: Float,
    color: Color, u: Float, line: Stroke,
) {
    drawCircle(color, r * u, Offset(cx * u, cy * u), style = line)
    for (i in 0 until 8) {
        val a = Math.PI / 4 * i
        val c = cos(a).toFloat()
        val s = sin(a).toFloat()
        drawLine(
            color,
            Offset((cx + c * rayFrom) * u, (cy + s * rayFrom) * u),
            Offset((cx + c * rayTo) * u, (cy + s * rayTo) * u),
            line.width,
            StrokeCap.Round,
        )
    }
}

/** Croissant de lune : un disque dont on retire un disque décalé vers le haut à droite. */
private fun moonPath(cx: Float, cy: Float, r: Float, u: Float): Path {
    val full = Path().apply { addOval(Rect(Offset(cx * u, cy * u), r * u)) }
    val bite = Path().apply {
        addOval(Rect(Offset((cx + r * 0.55f) * u, (cy - r * 0.4f) * u), r * 0.85f * u))
    }
    return Path().apply { op(full, bite, PathOperation.Difference) }
}

/** Nuage : union de trois bosses et d'une base plate, décalée de ([dx], [dy]) sur la grille. */
private fun cloudPath(u: Float, dx: Float, dy: Float): Path {
    fun circle(x: Float, y: Float, r: Float) =
        Path().apply { addOval(Rect(Offset((x + dx) * u, (y + dy) * u), r * u)) }

    val base = Path().apply {
        addRect(Rect((8f + dx) * u, (15f + dy) * u, (17f + dx) * u, (19f + dy) * u))
    }
    var shape = circle(8f, 15f, 4f)
    listOf(circle(12.5f, 11.5f, 5f), circle(17f, 15f, 4f), base).forEach { part ->
        shape = Path().apply { op(shape, part, PathOperation.Union) }
    }
    return shape
}

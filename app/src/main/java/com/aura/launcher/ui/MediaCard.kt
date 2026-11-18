package com.aura.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.launcher.LauncherActions
import com.aura.launcher.MediaInfo

/** Mini-lecteur affiché sur l'accueil quand de la musique est en cours. */
@Composable
fun MediaCard(media: MediaInfo, actions: LauncherActions, textScale: Float) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Column(
            Modifier
                .weight(1f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { actions.mediaOpen() },
        ) {
            Text(
                text = media.title,
                color = AuraColors.Text,
                fontSize = (17 * textScale).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            media.artist?.let {
                Text(
                    text = it,
                    color = AuraColors.TextDim,
                    fontSize = (14 * textScale).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        MediaButton(onClick = actions::mediaPrevious) { drawPrevious(it) }
        MediaButton(onClick = actions::mediaPlayPause) {
            if (media.isPlaying) drawPause(it) else drawPlay(it)
        }
        MediaButton(onClick = actions::mediaNext) { drawNext(it) }
    }
}

@Composable
private fun MediaButton(onClick: () -> Unit, draw: DrawScope.(Color) -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        Canvas(Modifier.size(18.dp)) { draw(AuraColors.Text) }
    }
}

// Petites icônes dessinées à la main (pas besoin de bibliothèque d'icônes).

private fun DrawScope.triangle(left: Float, right: Float, pointsRight: Boolean, color: Color) {
    val h = size.height
    val path = Path().apply {
        if (pointsRight) {
            moveTo(left, 0f); lineTo(right, h / 2); lineTo(left, h)
        } else {
            moveTo(right, 0f); lineTo(left, h / 2); lineTo(right, h)
        }
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.bar(left: Float, width: Float, color: Color) {
    drawRoundRect(color, Offset(left, 0f), Size(width, size.height), CornerRadius(width / 3))
}

private fun DrawScope.drawPlay(color: Color) = triangle(size.width * 0.12f, size.width * 0.95f, true, color)

private fun DrawScope.drawPause(color: Color) {
    val w = size.width * 0.28f
    bar(size.width * 0.12f, w, color)
    bar(size.width * 0.60f, w, color)
}

private fun DrawScope.drawNext(color: Color) {
    triangle(0f, size.width * 0.78f, true, color)
    bar(size.width * 0.80f, size.width * 0.16f, color)
}

private fun DrawScope.drawPrevious(color: Color) {
    bar(size.width * 0.04f, size.width * 0.16f, color)
    triangle(size.width * 0.22f, size.width, false, color)
}

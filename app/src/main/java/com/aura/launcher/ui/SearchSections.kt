package com.aura.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aura.launcher.AppEntry
import com.aura.launcher.LauncherActions
import com.aura.launcher.search.ContactResult
import com.aura.launcher.search.SettingsShortcut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

/*
 * Sections de la recherche universelle du tiroir.
 *
 * Hiérarchie : le résultat d'un calcul (réponse directe) en tête, puis les apps (le cœur du tiroir,
 * sans titre), puis des sections secondaires introduites par un petit titre atténué suivi d'un filet :
 * Contacts, Réglages Android, Web. La lavande reste réservée au résultat du calcul et à l'invitation
 * à activer les contacts : les titres de section restent en texte atténué pour ne pas concurrencer
 * les lettres de la liste alphabétique.
 */

/** Titre de section discret : libellé atténué et filet qui s'efface vers la droite. */
@Composable
fun SearchSectionHeader(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AuraSpace.xl, bottom = AuraSpace.xs),
    ) {
        Text(text, color = AuraColors.TextDim, style = AuraType.letter.homeFont())
        Spacer(Modifier.width(AuraSpace.m))
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(Brush.horizontalGradient(listOf(AuraColors.Hairline, Color.Transparent))),
        )
    }
}

/** Ligne de résultat générique : vignette à gauche (même largeur que les icônes d'apps), texte, action à droite. */
@Composable
private fun ResultRow(
    onClick: () -> Unit,
    onClickLabel: String,
    showIcon: Boolean,
    iconSize: Dp,
    leading: @Composable () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AuraSpace.touch)
            .clip(RoundedCornerShape(AuraRadius.s))
            .clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
            .padding(vertical = AuraSpace.s + AuraSpace.xxs),
    ) {
        if (showIcon) {
            Box(Modifier.size(iconSize), contentAlignment = Alignment.Center) { leading() }
            Spacer(Modifier.width(AuraSpace.l))
        }
        Box(Modifier.weight(1f)) { content() }
        if (trailing != null) trailing()
    }
}

/** Résultat d'un calcul : « = 87 » en lavande claire ; toucher copie le nombre. */
@Composable
fun CalcResultRow(result: String, showIcon: Boolean, iconSize: Dp, textStyle: TextStyle, onCopy: () -> Unit) {
    ResultRow(
        onClick = onCopy,
        onClickLabel = "Copier le résultat",
        showIcon = showIcon,
        iconSize = iconSize,
        leading = { RingGlyph(AuraColors.Accent, iconSize) { EqualsMark(AuraColors.AccentLight) } },
    ) {
        Column {
            Text(
                "= $result",
                style = textStyle,
                color = AuraColors.AccentLight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Toucher pour copier",
                style = AuraType.caption,
                color = AuraColors.TextDim,
                modifier = Modifier.padding(top = AuraSpace.xxs),
            )
        }
    }
}

/** Contact : photo ou initiale, nom ; toucher ouvre la fiche, le combiné à droite ouvre le composeur. */
@Composable
fun ContactRow(
    contact: ContactResult,
    actions: LauncherActions,
    showIcon: Boolean,
    iconSize: Dp,
    textStyle: TextStyle,
) {
    ResultRow(
        onClick = { actions.openContact(contact) },
        onClickLabel = "Ouvrir la fiche de ${contact.name}",
        showIcon = showIcon,
        iconSize = iconSize,
        leading = { ContactAvatar(contact, actions, iconSize) },
        trailing = {
            if (contact.phone != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(AuraSpace.touch)
                        .clip(CircleShape)
                        .clickable(onClickLabel = "Appeler ${contact.name}", role = Role.Button) {
                            actions.dialContact(contact)
                        },
                ) {
                    PhoneGlyph(AuraColors.TextDim)
                }
            }
        },
    ) {
        Text(
            contact.name,
            style = textStyle,
            color = AuraColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ContactAvatar(contact: ContactResult, actions: LauncherActions, size: Dp) {
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val photo by produceState<ImageBitmap?>(initialValue = null, contact.key, contact.photoUri, sizePx) {
        value = if (contact.photoUri == null) null else withContext(Dispatchers.IO) {
            actions.loadContactPhoto(contact, sizePx)
        }
    }
    val current = photo
    if (current != null) {
        Image(
            bitmap = current,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape),
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .background(AuraColors.SurfaceRaised, CircleShape)
                .border(1.dp, AuraColors.Hairline, CircleShape),
        ) {
            Text(
                contact.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                style = AuraType.label,
                color = AuraColors.TextDim,
            )
        }
    }
}

/** Invitation à activer la recherche dans les contacts (demande la permission). */
@Composable
fun ContactsInviteRow(showIcon: Boolean, iconSize: Dp, onClick: () -> Unit) {
    ResultRow(
        onClick = onClick,
        onClickLabel = "Autoriser la recherche dans les contacts",
        showIcon = showIcon,
        iconSize = iconSize,
        leading = { RingGlyph(AuraColors.Hairline, iconSize) { PersonGlyph(AuraColors.TextDim) } },
        trailing = { ChevronGlyph(AuraColors.Accent, Modifier.padding(horizontal = AuraSpace.m)) },
    ) {
        Column {
            Text("Autoriser la recherche dans les contacts", style = AuraType.body, color = AuraColors.Text)
            Text(
                "Lus sur le téléphone seulement, au moment où tu tapes.",
                style = AuraType.caption,
                color = AuraColors.TextDim,
                modifier = Modifier.padding(top = AuraSpace.xxs),
            )
        }
    }
}

/** Raccourci vers une page des réglages d'Android. */
@Composable
fun SettingsShortcutRow(
    shortcut: SettingsShortcut,
    actions: LauncherActions,
    showIcon: Boolean,
    iconSize: Dp,
    textStyle: TextStyle,
) {
    ResultRow(
        onClick = { actions.openSystemSetting(shortcut) },
        onClickLabel = "Ouvrir ${shortcut.label}",
        showIcon = showIcon,
        iconSize = iconSize,
        leading = { RingGlyph(AuraColors.Hairline, iconSize) { GearGlyph(AuraColors.TextDim) } },
        trailing = { ChevronGlyph(AuraColors.TextFaint, Modifier.padding(horizontal = AuraSpace.m)) },
    ) {
        Text(shortcut.label, style = textStyle, color = AuraColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Dernière ligne : recherche web de la saisie. */
@Composable
fun WebSearchRow(query: String, actions: LauncherActions, showIcon: Boolean, iconSize: Dp) {
    ResultRow(
        onClick = { actions.webSearch(query) },
        onClickLabel = "Rechercher sur le web",
        showIcon = showIcon,
        iconSize = iconSize,
        leading = { RingGlyph(AuraColors.Hairline, iconSize) { GlobeGlyph(AuraColors.TextDim) } },
    ) {
        Text(
            "Rechercher « ${query.trim()} » sur le web",
            style = AuraType.body,
            color = AuraColors.TextDim,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Apps suggérées : une rangée de 4 à 5 icônes (les plus utilisées hors favoris), en haut du tiroir.
 * Cinq cases de même largeur : avec quatre apps, la rangée reste alignée à gauche.
 */
@Composable
fun SuggestedApps(
    apps: List<AppEntry>,
    actions: LauncherActions,
    showIcon: Boolean,
    textScale: Float,
) {
    Column(Modifier.fillMaxWidth()) {
        SearchSectionHeader("Souvent ouvertes")
        Row(
            horizontalArrangement = Arrangement.spacedBy(AuraSpace.xs),
            modifier = Modifier.fillMaxWidth().padding(top = AuraSpace.xs),
        ) {
            for (index in 0 until SUGGESTION_SLOTS) {
                val app = apps.getOrNull(index)
                if (app == null) {
                    Spacer(Modifier.weight(1f))
                    continue
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(AuraRadius.s))
                        .clickable(onClickLabel = "Ouvrir ${app.label}", role = Role.Button) { actions.launch(app) }
                        .padding(vertical = AuraSpace.s),
                ) {
                    if (showIcon) {
                        AppIcon(app, actions, (44 * textScale).dp)
                        Spacer(Modifier.height(AuraSpace.xs + AuraSpace.xxs))
                    }
                    Text(
                        app.label,
                        style = (if (showIcon) AuraType.caption.scaled(textScale) else AuraType.bodySmall.scaled(textScale)).homeFont(),
                        color = if (showIcon) AuraColors.TextDim else AuraColors.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = AuraSpace.xxs),
                    )
                }
            }
        }
    }
}

const val SUGGESTION_SLOTS = 5

// --- Glyphes dessinés (pas de bibliothèque d'icônes) ---

/** Anneau fin autour d'un petit glyphe : le motif « éclipse » d'Aura. */
@Composable
private fun RingGlyph(ring: Color, size: Dp, content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size).border(1.dp, ring, CircleShape),
    ) { content() }
}

/** Signe égal. */
@Composable
private fun EqualsMark(color: Color) {
    Canvas(Modifier.size(14.dp)) {
        val stroke = 1.8.dp.toPx()
        val w = size.width
        val h = size.height
        drawLine(color, Offset(w * 0.12f, h * 0.36f), Offset(w * 0.88f, h * 0.36f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.12f, h * 0.64f), Offset(w * 0.88f, h * 0.64f), stroke, StrokeCap.Round)
    }
}

/** Roue dentée : un anneau et huit dents courtes. */
@Composable
private fun GearGlyph(color: Color) {
    Canvas(Modifier.size(16.dp)) {
        val stroke = 1.6.dp.toPx()
        val c = center
        val r = size.minDimension / 2f
        drawCircle(color, radius = r * 0.42f, center = c, style = Stroke(stroke))
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            val dx = cos(a).toFloat()
            val dy = sin(a).toFloat()
            drawLine(
                color,
                Offset(c.x + dx * r * 0.62f, c.y + dy * r * 0.62f),
                Offset(c.x + dx * r * 0.92f, c.y + dy * r * 0.92f),
                stroke,
                StrokeCap.Round,
            )
        }
    }
}

/** Globe : un cercle, un méridien en ellipse et l'équateur. */
@Composable
private fun GlobeGlyph(color: Color) {
    Canvas(Modifier.size(16.dp)) {
        val stroke = 1.4.dp.toPx()
        val r = size.minDimension / 2f - stroke
        drawCircle(color, radius = r, center = center, style = Stroke(stroke))
        drawOval(
            color,
            topLeft = Offset(center.x - r * 0.45f, center.y - r),
            size = Size(r * 0.9f, r * 2f),
            style = Stroke(stroke),
        )
        drawLine(color, Offset(center.x - r, center.y), Offset(center.x + r, center.y), stroke)
    }
}

/** Silhouette : une tête et des épaules. */
@Composable
private fun PersonGlyph(color: Color) {
    Canvas(Modifier.size(16.dp)) {
        val stroke = 1.5.dp.toPx()
        val w = size.width
        val h = size.height
        drawCircle(color, radius = w * 0.2f, center = Offset(w * 0.5f, h * 0.32f), style = Stroke(stroke))
        drawArc(
            color,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(w * 0.14f, h * 0.62f),
            size = Size(w * 0.72f, h * 0.6f),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

/** Combiné de téléphone : un arc et deux écouteurs épais tournés vers l'intérieur. */
@Composable
private fun PhoneGlyph(color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val w = size.width
        val c = Offset(w * 0.8f, w * 0.2f)
        val r = w * 0.58f
        val thin = 2.dp.toPx()
        val thick = 3.6.dp.toPx()
        drawArc(
            color,
            startAngle = 95f,
            sweepAngle = 80f,
            useCenter = false,
            topLeft = Offset(c.x - r, c.y - r),
            size = Size(r * 2, r * 2),
            style = Stroke(thin, cap = StrokeCap.Round),
        )
        for (angle in listOf(95.0, 175.0)) {
            val a = Math.toRadians(angle)
            val end = Offset(c.x + cos(a).toFloat() * r, c.y + sin(a).toFloat() * r)
            val inner = Offset(c.x + cos(a).toFloat() * r * 0.66f, c.y + sin(a).toFloat() * r * 0.66f)
            drawLine(color, end, inner, thick, StrokeCap.Round)
        }
    }
}

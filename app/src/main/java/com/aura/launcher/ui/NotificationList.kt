package com.aura.launcher.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.aura.launcher.AppEntry
import com.aura.launcher.ChatMessage
import com.aura.launcher.LauncherActions
import com.aura.launcher.NotificationInfo
import com.aura.launcher.NotificationItem
import com.aura.launcher.NotificationStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Notifications de l'app dans sa carte : une entrée par notification (expéditeur, derniers messages,
 * heure relative). Toucher ouvre la conversation ; « Répondre » ouvre un champ de saisie dans la carte ;
 * glisser de côté ou la petite croix efface la notification.
 *
 * [canReply] reflète le réglage « Répondre depuis l'accueil » : sans lui, ni réponse ni effacement.
 */
@Composable
fun NotificationList(
    app: AppEntry,
    info: NotificationInfo,
    actions: LauncherActions,
    canReply: Boolean,
    textScale: Float,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    // Notification dont le champ de réponse est ouvert (une seule à la fois).
    var replyingKey by remember(app.key) { mutableStateOf<String?>(null) }
    // Réponses envoyées depuis la carte, affichées tout de suite (clé de notification → textes).
    val sent = remember(app.key) { mutableStateMapOf<String, List<String>>() }
    // Effacées : masquées aussitôt, sans attendre que le service relise les notifications.
    val dismissed = remember(app.key) { mutableStateListOf<String>() }

    BackHandler(enabled = replyingKey != null) { replyingKey = null }

    // L'heure relative se met à jour chaque minute.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000 - now % 60_000)
        }
    }

    val items = info.items.filter { it.key !in dismissed }
    if (items.isEmpty()) return

    Column(Modifier.fillMaxWidth().padding(bottom = AuraSpace.xs)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = AuraSpace.xl, end = AuraSpace.xl, top = AuraSpace.xs, bottom = AuraSpace.xxs),
        ) {
            HaloDot(size = 10.dp)
            Spacer(Modifier.width(AuraSpace.s))
            Text(
                text = if (items.size > 1) "${items.size} notifications" else "1 notification",
                color = AuraColors.TextDim,
                style = AuraType.caption,
            )
        }
        items.forEach { item ->
            key(item.key) {
                NotificationEntry(
                    item = item,
                    now = now,
                    textScale = textScale,
                    canReply = canReply,
                    replying = replyingKey == item.key,
                    sent = sent[item.key].orEmpty(),
                    onOpen = {
                        if (!NotificationStore.open(context, item)) actions.launch(app)
                        onClose()
                    },
                    onStartReply = { replyingKey = item.key },
                    onCancelReply = { replyingKey = null },
                    onSend = { text ->
                        val ok = NotificationStore.sendReply(context, item, text)
                        if (ok) {
                            sent[item.key] = sent[item.key].orEmpty() + text
                            replyingKey = null
                        }
                        ok
                    },
                    onDismiss = {
                        dismissed += item.key
                        if (replyingKey == item.key) replyingKey = null
                        NotificationStore.dismiss(item.key)
                    },
                )
            }
        }
    }
}

@Composable
private fun NotificationEntry(
    item: NotificationItem,
    now: Long,
    textScale: Float,
    canReply: Boolean,
    replying: Boolean,
    sent: List<String>,
    onOpen: () -> Unit,
    onStartReply: () -> Unit,
    onCancelReply: () -> Unit,
    onSend: (String) -> Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var width by remember { mutableStateOf(1f) }
    val reducedMotion = rememberReducedMotion()

    // Glisser de côté pour effacer (seulement si le réglage le permet et pendant qu'on n'écrit pas).
    val swipe = if (canReply && !replying) {
        Modifier.pointerInput(item.key) {
            width = size.width.toFloat().coerceAtLeast(1f)
            detectHorizontalDragGestures(
                onDragEnd = {
                    scope.launch {
                        if (abs(offset.value) > width * 0.35f) {
                            val target = if (offset.value > 0) width else -width
                            if (!reducedMotion) offset.animateTo(target, tween(AuraMotion.quick, easing = AuraMotion.exit))
                            onDismiss()
                        } else {
                            offset.animateTo(0f, tween(AuraMotion.standard, easing = AuraMotion.enter))
                        }
                    }
                },
                onDragCancel = { scope.launch { offset.animateTo(0f) } },
                onHorizontalDrag = { change, amount ->
                    change.consume()
                    scope.launch { offset.snapTo(offset.value + amount) }
                },
            )
        }
    } else {
        Modifier
    }

    Column(
        Modifier
            .fillMaxWidth()
            .then(swipe)
            .graphicsLayer {
                translationX = offset.value
                alpha = 1f - (abs(offset.value) / width).coerceIn(0f, 1f) * 0.7f
            },
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = "Ouvrir la conversation", onClick = onOpen)
                .padding(start = AuraSpace.xl, end = if (canReply) AuraSpace.xs else AuraSpace.xl, top = AuraSpace.s, bottom = AuraSpace.xs),
        ) {
            Column(Modifier.weight(1f).padding(top = AuraSpace.xs)) {
                NotificationContent(item, now, textScale, sent)
            }
            if (canReply) {
                DismissButton(onDismiss)
            }
        }

        // Boutons : Répondre (accent), puis les autres actions de l'app (2 au plus), en discret.
        val showReply = canReply && item.reply != null && !replying
        if (showReply || item.actions.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(AuraSpace.xxs),
                modifier = Modifier.padding(start = AuraSpace.s + AuraSpace.xxs),
            ) {
                if (showReply) {
                    SmallTextButton("Répondre", AuraColors.AccentLight, onStartReply)
                }
                item.actions.forEach { action ->
                    SmallTextButton(action.label, AuraColors.TextDim) {
                        NotificationStore.runAction(context, action)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = replying,
            enter = expandVertically(tween(AuraMotion.standard, easing = AuraMotion.enter)) + fadeIn(tween(AuraMotion.standard)),
            exit = shrinkVertically(tween(AuraMotion.quick, easing = AuraMotion.exit)) + fadeOut(tween(AuraMotion.quick)),
        ) {
            ReplyField(
                to = item.title,
                onCancel = onCancelReply,
                onSend = { text ->
                    val ok = onSend(text)
                    if (ok) {
                        view.performHapticFeedback(
                            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
                            else HapticFeedbackConstants.KEYBOARD_TAP,
                        )
                    }
                    ok
                },
            )
        }
        Spacer(Modifier.height(AuraSpace.xs))
    }
}

/** Titre en gras + heure, puis les derniers messages (ou le texte de la notification). */
@Composable
private fun NotificationContent(item: NotificationItem, now: Long, textScale: Float, sent: List<String>) {
    val title = item.title?.takeIf { it.isNotBlank() }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title ?: "",
            color = AuraColors.Text,
            style = AuraType.body.scaled(textScale).copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(AuraSpace.s))
        Text(
            text = relativeTime(item.postTime, now),
            color = AuraColors.TextFaint,
            style = AuraType.caption,
            maxLines = 1,
        )
    }

    val bodyStyle = AuraType.bodySmall.scaled(textScale)
    // Réponses déjà reprises par l'app dans la conversation : inutile de les afficher deux fois.
    val mine = item.messages.filter { it.sender == null }.map { it.text }.toSet()
    val pending = sent.filter { it !in mine }

    if (item.messages.isEmpty()) {
        item.text?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it.replace('\n', ' '),
                color = AuraColors.TextDim,
                style = bodyStyle,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = AuraSpace.xxs),
            )
        }
    } else {
        // Le nom n'est répété devant chaque message que dans les groupes (expéditeur ≠ titre).
        item.messages.forEach { message -> MessageLine(message, title, bodyStyle) }
    }
    pending.forEach { MessageLine(ChatMessage(null, it, now), title, bodyStyle) }
}

@Composable
private fun MessageLine(message: ChatMessage, title: String?, style: androidx.compose.ui.text.TextStyle) {
    val sender = message.sender
    val text = buildAnnotatedString {
        if (sender == null) {
            withStyle(SpanStyle(color = AuraColors.TextFaint)) { append("Toi : ") }
        } else if (sender != title) {
            withStyle(SpanStyle(color = AuraColors.Text, fontWeight = FontWeight.SemiBold)) {
                append(sender)
                append(" : ")
            }
        }
        append(message.text.replace('\n', ' '))
    }
    Text(
        text = text,
        color = if (sender == null) AuraColors.TextFaint else AuraColors.TextDim,
        style = style,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = AuraSpace.xxs),
    )
}

/**
 * Champ de réponse : libellé « Répondre à … », zone de saisie arrondie et bouton d'envoi rond.
 * Le clavier s'ouvre tout seul ; la touche Entrée du clavier envoie. En cas d'échec, un message
 * s'affiche juste sous le champ et le texte est conservé.
 */
@Composable
private fun ReplyField(to: String?, onCancel: () -> Unit, onSend: (String) -> Boolean) {
    var text by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val canSend = text.isNotBlank()

    fun send() {
        if (!canSend) return
        val ok = onSend(text.trim())
        failed = !ok
        if (ok) {
            keyboard?.hide()
            text = ""
        }
    }

    LaunchedEffect(Unit) {
        // Laisse l'animation d'ouverture démarrer avant de demander le clavier.
        delay(AuraMotion.quick.toLong())
        try {
            focus.requestFocus()
            keyboard?.show()
        } catch (e: IllegalStateException) {
            // Champ déjà retiré (fermé très vite) : rien à faire.
        }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = AuraSpace.l, vertical = AuraSpace.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = AuraSpace.s)) {
            Text(
                text = if (to.isNullOrBlank()) "Ta réponse" else "Répondre à $to",
                color = AuraColors.TextDim,
                style = AuraType.caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            SmallTextButton("Annuler", AuraColors.TextFaint) {
                keyboard?.hide()
                onCancel()
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            val fieldShape = auraShape(AuraRadius.l)
            BasicTextField(
                value = text,
                onValueChange = {
                    text = it
                    failed = false
                },
                textStyle = AuraType.body.copy(color = AuraColors.Text),
                cursorBrush = SolidColor(AuraColors.Accent),
                maxLines = 5,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(onSend = { send() }),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = AuraSpace.touch)
                    .clip(fieldShape)
                    .background(AuraColors.SurfaceRaised)
                    .border(1.dp, if (failed) AuraColors.TextDim else AuraColors.Hairline, fieldShape)
                    .focusRequester(focus)
                    .semantics { contentDescription = if (to.isNullOrBlank()) "Réponse" else "Réponse à $to" },
                decorationBox = { inner ->
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier.padding(horizontal = AuraSpace.l, vertical = AuraSpace.m),
                    ) {
                        if (text.isEmpty()) {
                            Text("Message", color = AuraColors.TextFaint, style = AuraType.body)
                        }
                        inner()
                    }
                },
            )
            Spacer(Modifier.width(AuraSpace.s))
            SendButton(enabled = canSend, onClick = ::send)
        }
        if (failed) {
            Text(
                "Envoi impossible : l'app n'accepte plus cette réponse. Touche la notification pour l'ouvrir.",
                color = AuraColors.TextDim,
                style = AuraType.caption,
                modifier = Modifier.padding(start = AuraSpace.s, top = AuraSpace.xs),
            )
        }
    }
}

/** Bouton d'envoi rond : flèche vers le haut dessinée, lavande quand il y a du texte. */
@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    val glyph = if (enabled) AuraColors.OnAccent else AuraColors.TextFaint
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(AuraSpace.touch)
            .clip(CircleShape)
            .background(if (enabled) AuraColors.Accent else AuraColors.SurfaceRaised)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Envoyer", onClick = onClick)
            .semantics { contentDescription = "Envoyer" },
    ) {
        Canvas(Modifier.size(16.dp)) {
            val stroke = 1.8.dp.toPx()
            val cx = size.width / 2f
            drawLine(glyph, Offset(cx, size.height * 0.92f), Offset(cx, size.height * 0.1f), stroke, StrokeCap.Round)
            drawLine(glyph, Offset(cx, size.height * 0.1f), Offset(size.width * 0.18f, size.height * 0.45f), stroke, StrokeCap.Round)
            drawLine(glyph, Offset(cx, size.height * 0.1f), Offset(size.width * 0.82f, size.height * 0.45f), stroke, StrokeCap.Round)
        }
    }
}

/** Petite croix « Effacer » (zone de toucher de 48 dp). */
@Composable
private fun DismissButton(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(AuraSpace.touch)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = "Effacer la notification", onClick = onClick)
            .semantics { contentDescription = "Effacer la notification" },
    ) {
        Canvas(Modifier.size(10.dp)) {
            val stroke = 1.5.dp.toPx()
            drawLine(AuraColors.TextFaint, Offset.Zero, Offset(size.width, size.height), stroke, StrokeCap.Round)
            drawLine(AuraColors.TextFaint, Offset(size.width, 0f), Offset(0f, size.height), stroke, StrokeCap.Round)
        }
    }
}

/** Bouton texte compact (zone de toucher de 48 dp de haut). */
@Composable
private fun SmallTextButton(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .heightIn(min = AuraSpace.touch)
            .clip(auraShape(AuraRadius.s))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = AuraSpace.m),
    ) {
        Text(text = label, color = color, style = AuraType.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Heure relative courte : « à l'instant », « 5 min », « 2 h », « hier », puis la date. */
private fun relativeTime(time: Long, now: Long): String {
    val minutes = (now - time).coerceAtLeast(0) / 60_000
    return when {
        minutes < 1 -> "à l'instant"
        minutes < 60 -> "$minutes min"
        minutes < 24 * 60 -> "${minutes / 60} h"
        minutes < 48 * 60 -> "hier"
        else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(time))
    }
}

package com.aura.launcher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.abs
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.aura.launcher.AppEntry
import com.aura.launcher.LauncherActions
import com.aura.launcher.LauncherState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val ALPHABET = ('A'..'Z').map { it.toString() } + "#"

/** Élément de la liste : un en-tête de lettre ou une app. */
private sealed interface DrawerItem {
    val id: String
    data class Header(val letter: String) : DrawerItem { override val id = "h:$letter" }
    data class App(val app: AppEntry) : DrawerItem { override val id = app.key }
}

@Composable
fun AppDrawer(state: LauncherState, actions: LauncherActions) {
    val apps = state.visibleApps
    val query = state.query
    val focusManager = LocalFocusManager.current

    val results = remember(apps, query) { search(apps, query) }

    // Lancement automatique quand la recherche ne laisse qu'une seule app.
    LaunchedEffect(results, query) {
        if (state.autoLaunch && query.length >= 2 && results.size == 1) {
            focusManager.clearFocus()
            actions.launch(results.first())
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(AuraColors.DrawerScrim)
            .systemBarsPadding()
            .imePadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            SearchField(
                value = query,
                onValueChange = { state.query = it },
                onGo = { results.firstOrNull()?.let(actions::launch) },
                textScale = state.textScale,
            )
            if (query.isBlank()) {
                AlphabeticalList(state, actions, apps)
            } else {
                SearchResults(state, actions, results)
            }
        }
    }
}

/** Les apps dont le nom commence par la recherche passent avant celles qui la contiennent. */
private fun search(apps: List<AppEntry>, query: String): List<AppEntry> {
    val q = AppEntry.normalize(query)
    if (q.isEmpty()) return apps
    val starts = mutableListOf<AppEntry>()
    val wordStarts = mutableListOf<AppEntry>()
    val contains = mutableListOf<AppEntry>()
    for (app in apps) {
        val key = app.searchKey
        when {
            key.startsWith(q) -> starts += app
            key.split(' ', '-', '.').any { it.startsWith(q) } -> wordStarts += app
            key.contains(q) || app.packageName.contains(q) -> contains += app
        }
    }
    return starts + wordStarts + contains
}

/**
 * Champ de recherche : une carte arrondie avec une loupe dessinée, et une croix pour effacer
 * dès qu'on a tapé quelque chose. Le contour s'éclaire en lavande quand le champ a le focus.
 */
@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, onGo: () -> Unit, textScale: Float) {
    var focused by remember { mutableStateOf(false) }
    val border by animateColorAsState(
        if (focused) AuraColors.Accent.copy(alpha = 0.55f) else AuraColors.Hairline,
        tween(AuraMotion.standard),
        label = "contour",
    )
    val shape = RoundedCornerShape(AuraRadius.m)
    val style = AuraType.row.scaled(textScale)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = style.copy(color = AuraColors.Text),
        cursorBrush = SolidColor(AuraColors.Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = AuraSpace.l, end = AuraSpace.l, top = AuraSpace.m, bottom = AuraSpace.xs)
            .onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(AuraColors.Surface)
                    .border(1.dp, border, shape)
                    .padding(start = AuraSpace.l, end = AuraSpace.xs)
                    .heightIn(min = 52.dp),
            ) {
                SearchGlyph(if (focused) AuraColors.AccentLight else AuraColors.TextFaint)
                Spacer(Modifier.width(AuraSpace.m))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text("Rechercher une app", color = AuraColors.TextFaint, style = style)
                    }
                    inner()
                }
                if (value.isNotEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(AuraSpace.touch)
                            .clip(CircleShape)
                            .clickable(onClickLabel = "Effacer la recherche") { onValueChange("") },
                    ) {
                        ClearGlyph(AuraColors.TextDim)
                    }
                } else {
                    Spacer(Modifier.width(AuraSpace.m))
                }
            }
        },
    )
}

/** Loupe : un cercle et un manche. */
@Composable
private fun SearchGlyph(color: Color) {
    Canvas(Modifier.size(18.dp)) {
        val stroke = 1.6.dp.toPx()
        val r = size.minDimension * 0.34f
        val c = Offset(size.width * 0.42f, size.height * 0.42f)
        drawCircle(color, radius = r, center = c, style = Stroke(stroke))
        val d = r * 0.72f
        drawLine(
            color,
            Offset(c.x + d, c.y + d),
            Offset(size.width * 0.92f, size.height * 0.92f),
            stroke,
            StrokeCap.Round,
        )
    }
}

/** Croix d'effacement. */
@Composable
private fun ClearGlyph(color: Color) {
    Canvas(Modifier.size(12.dp)) {
        val stroke = 1.6.dp.toPx()
        drawLine(color, Offset.Zero, Offset(size.width, size.height), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), stroke, StrokeCap.Round)
    }
}

@Composable
private fun SearchResults(state: LauncherState, actions: LauncherActions, results: List<AppEntry>) {
    if (results.isEmpty()) {
        Text(
            "Aucune app ne correspond. Vérifie l'orthographe ou affiche les apps masquées dans les réglages.",
            color = AuraColors.TextDim,
            style = AuraType.body,
            modifier = Modifier.padding(horizontal = AuraSpace.gutter, vertical = AuraSpace.l),
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(
            start = AuraSpace.gutter,
            end = AuraSpace.gutter,
            top = AuraSpace.s,
            bottom = AuraSpace.xl,
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(results, key = { it.key }) { app ->
            AppRow(
                app = app,
                actions = actions,
                showIcon = state.showIcons,
                iconSize = (34 * state.textScale).dp,
                textStyle = AuraType.row.scaled(state.textScale),
                badge = state.notificationsFor(app) != null,
                menu = drawerMenu(state, actions, app),
            )
        }
    }
}

private fun drawerMenu(state: LauncherState, actions: LauncherActions, app: AppEntry) = listOf(
    MenuAction(if (app.key in state.favorites) "Retirer de l'accueil" else "Épingler à l'accueil") {
        actions.toggleFavorite(app)
    },
    MenuAction("Renommer") { state.renaming = app },
    MenuAction("Masquer") { actions.setHidden(app.key, true) },
    MenuAction("Infos de l'app") { actions.openAppInfo(app) },
    MenuAction("Désinstaller") { actions.uninstall(app) },
)

@Composable
private fun AlphabeticalList(state: LauncherState, actions: LauncherActions, apps: List<AppEntry>) {
    val entries = remember(apps) {
        buildList {
            var current: String? = null
            for (app in apps) {
                if (app.letter != current) {
                    current = app.letter
                    add(DrawerItem.Header(app.letter))
                }
                add(DrawerItem.App(app))
            }
        }
    }
    val headerIndex = remember(entries) {
        entries.withIndex()
            .filter { it.value is DrawerItem.Header }
            .associate { (it.value as DrawerItem.Header).letter to it.index }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Tirer vers le bas alors que la liste est déjà en haut : retour à l'accueil.
    val closeThreshold = with(LocalDensity.current) { 110.dp.toPx() }
    val pullToClose = remember(state) {
        object : NestedScrollConnection {
            var pulled = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (available.y > 0f) {
                    pulled += available.y
                    if (pulled > closeThreshold) {
                        pulled = 0f
                        state.drawerOpen = false
                    }
                    return Offset(0f, available.y)
                }
                pulled = 0f
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                pulled = 0f
                return Velocity.Zero
            }
        }
    }

    Row(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = AuraSpace.gutter, end = AuraSpace.s, bottom = AuraSpace.xl),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .nestedScroll(pullToClose),
        ) {
            items(entries, key = { it.id }) { item ->
                when (item) {
                    is DrawerItem.Header -> LetterHeader(item.letter)
                    is DrawerItem.App -> AppRow(
                        app = item.app,
                        actions = actions,
                        showIcon = state.showIcons,
                        iconSize = (34 * state.textScale).dp,
                        textStyle = AuraType.row.scaled(state.textScale),
                        badge = state.notificationsFor(item.app) != null,
                        menu = drawerMenu(state, actions, item.app),
                    )
                }
            }
        }

        LetterBar(
            available = headerIndex.keys,
            onLetter = { letter ->
                // Lettre sans app : on saute à la suivante qui en a.
                val target = ALPHABET.drop(ALPHABET.indexOf(letter))
                    .firstNotNullOfOrNull { headerIndex[it] }
                    ?: headerIndex.values.maxOrNull()
                if (target != null) scope.launch { listState.scrollToItem(target) }
            },
        )
    }
}

/** En-tête de lettre : la lettre en lavande, suivie d'un filet qui s'efface vers la droite. */
@Composable
private fun LetterHeader(letter: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AuraSpace.l + AuraSpace.xs, bottom = AuraSpace.xs),
    ) {
        Text(text = letter, color = AuraColors.Accent, style = AuraType.letter)
        Spacer(Modifier.width(AuraSpace.m))
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(Brush.horizontalGradient(listOf(AuraColors.Hairline, Color.Transparent))),
        )
    }
}

/**
 * Barre de lettres sur le bord droit : on y glisse le pouce pour naviguer.
 * Pendant le glissement, les lettres proches du doigt grossissent et se décalent vers la gauche
 * (effet loupe), et une bulle cerclée de lavande montre la lettre en grand à côté du pouce.
 */
@Composable
private fun LetterBar(available: Set<String>, onLetter: (String) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val currentOnLetter by rememberUpdatedState(onLetter)
    var barHeight by remember { mutableIntStateOf(1) }
    var selected by remember { mutableStateOf<String?>(null) }
    var shownLetter by remember { mutableStateOf("A") }
    var touchY by remember { mutableFloatStateOf(0f) }
    val touching = selected != null
    // 0 → 1 à l'appui, 1 → 0 au relâchement : pilote la loupe et la bulle.
    val active by animateFloatAsState(
        if (touching) 1f else 0f,
        tween(if (touching) AuraMotion.quick else AuraMotion.standard, easing = AuraMotion.enter),
        label = "barre",
    )

    fun select(y: Float) {
        touchY = y.coerceIn(0f, barHeight.toFloat())
        val index = ((touchY / barHeight) * ALPHABET.size).toInt().coerceIn(0, ALPHABET.lastIndex)
        val letter = ALPHABET[index]
        if (letter != selected) {
            selected = letter
            shownLetter = letter
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            currentOnLetter(letter)
        }
    }

    val pad = AuraSpace.s
    val padPx = with(density) { pad.toPx() }
    val shiftPx = with(density) { 14.dp.toPx() }

    Box(Modifier.fillMaxHeight().width(36.dp)) {
        Column(
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = pad)
                .onSizeChanged { barHeight = it.height.coerceAtLeast(1) }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        select(down.position.y)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            change.consume()
                            select(change.position.y)
                        }
                        selected = null
                    }
                },
        ) {
            val slot = barHeight.toFloat() / ALPHABET.size
            ALPHABET.forEachIndexed { index, letter ->
                // Distance au doigt, en nombre de lettres : la loupe agit sur ~3 lettres de chaque côté.
                val center = slot * (index + 0.5f)
                val proximity = (1f - abs(center - touchY) / (slot * 3.5f)).coerceIn(0f, 1f) * active
                Text(
                    text = letter,
                    style = AuraType.letterBar,
                    color = when {
                        letter == selected -> AuraColors.Accent
                        letter in available -> AuraColors.TextDim
                        else -> AuraColors.TextFaint.copy(alpha = 0.3f)
                    },
                    modifier = Modifier.graphicsLayer {
                        val scale = 1f + 0.8f * proximity * proximity
                        scaleX = scale
                        scaleY = scale
                        translationX = -shiftPx * proximity * proximity
                    },
                )
            }
        }
    }

    // Bulle qui affiche la lettre en grand pendant le glissement : anneau lavande, fond nuit.
    if (active > 0.01f) {
        val bubble = 64.dp
        val bubblePx = with(density) { bubble.toPx() }
        Box(Modifier.width(0.dp).fillMaxHeight()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            -(bubblePx * 1.7f).roundToInt(),
                            (touchY + padPx - bubblePx / 2).roundToInt(),
                        )
                    }
                    .graphicsLayer {
                        alpha = active
                        val scale = 0.7f + 0.3f * active
                        scaleX = scale
                        scaleY = scale
                    }
                    .size(bubble)
                    .background(AuraColors.Surface, CircleShape)
                    .border(1.5.dp, AuraColors.Accent, CircleShape),
            ) {
                Text(
                    shownLetter,
                    color = AuraColors.AccentLight,
                    style = AuraType.screenTitle.copy(fontWeight = FontWeight.Light),
                )
            }
        }
    }
}

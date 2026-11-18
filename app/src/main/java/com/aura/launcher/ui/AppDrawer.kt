package com.aura.launcher.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, onGo: () -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = AuraColors.Text, fontSize = 18.sp),
        cursorBrush = SolidColor(AuraColors.Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(AuraColors.Surface, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (value.isEmpty()) {
                    Text("Rechercher", color = AuraColors.TextFaint, fontSize = 18.sp)
                }
                inner()
            }
        },
    )
}

@Composable
private fun SearchResults(state: LauncherState, actions: LauncherActions, results: List<AppEntry>) {
    if (results.isEmpty()) {
        Text(
            "Aucune app trouvée",
            color = AuraColors.TextDim,
            fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 28.dp, end = 28.dp, bottom = 24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(results, key = { it.key }) { app ->
            AppRow(
                app = app,
                actions = actions,
                showIcon = state.showIcons,
                iconSize = 34.dp,
                fontSize = 19.sp,
                menu = drawerMenu(state, actions, app),
            )
        }
    }
}

private fun drawerMenu(state: LauncherState, actions: LauncherActions, app: AppEntry) = listOf(
    MenuAction(if (app.key in state.favorites) "Retirer de l'accueil" else "Épingler à l'accueil") {
        actions.toggleFavorite(app)
    },
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
            contentPadding = PaddingValues(start = 28.dp, end = 8.dp, bottom = 24.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .nestedScroll(pullToClose),
        ) {
            items(entries, key = { it.id }) { item ->
                when (item) {
                    is DrawerItem.Header -> Text(
                        text = item.letter,
                        color = AuraColors.Accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
                    )
                    is DrawerItem.App -> AppRow(
                        app = item.app,
                        actions = actions,
                        showIcon = state.showIcons,
                        iconSize = 34.dp,
                        fontSize = 19.sp,
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

/** Barre de lettres sur le bord droit : on y glisse le pouce pour naviguer. */
@Composable
private fun LetterBar(available: Set<String>, onLetter: (String) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val currentOnLetter by rememberUpdatedState(onLetter)
    var barHeight by remember { mutableIntStateOf(1) }
    var selected by remember { mutableStateOf<String?>(null) }
    var touchY by remember { mutableFloatStateOf(0f) }

    fun select(y: Float) {
        touchY = y.coerceIn(0f, barHeight.toFloat())
        val index = ((touchY / barHeight) * ALPHABET.size).toInt().coerceIn(0, ALPHABET.lastIndex)
        val letter = ALPHABET[index]
        if (letter != selected) {
            selected = letter
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            currentOnLetter(letter)
        }
    }

    Box(Modifier.fillMaxHeight().width(36.dp)) {
        Column(
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp)
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
            ALPHABET.forEach { letter ->
                Text(
                    text = letter,
                    fontSize = 11.sp,
                    fontWeight = if (letter == selected) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        letter == selected -> AuraColors.Accent
                        letter in available -> AuraColors.TextDim
                        else -> AuraColors.TextFaint.copy(alpha = 0.25f)
                    },
                )
            }
        }
    }

    // Bulle qui affiche la lettre en grand pendant le glissement.
    selected?.let { letter ->
        val bubble = 56.dp
        val bubblePx = with(LocalDensity.current) { bubble.toPx() }
        Box(Modifier.width(0.dp).fillMaxHeight()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset { IntOffset(-(bubblePx * 1.6f).roundToInt(), (touchY - bubblePx / 2).roundToInt()) }
                    .size(bubble)
                    .background(AuraColors.Accent, CircleShape),
            ) {
                Text(letter, color = AuraColors.DrawerScrim, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

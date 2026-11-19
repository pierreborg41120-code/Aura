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
import com.aura.launcher.search.Calculator
import com.aura.launcher.search.ContactResult
import com.aura.launcher.search.SettingsShortcut
import com.aura.launcher.search.SettingsShortcuts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val ALPHABET = ('A'..'Z').map { it.toString() } + "#"

/** Élément de la liste : un en-tête de lettre, une app, ou la rangée d'apps suggérées. */
private sealed interface DrawerItem {
    val id: String
    data class Header(val letter: String) : DrawerItem { override val id = "h:$letter" }
    data class App(val app: AppEntry) : DrawerItem { override val id = app.key }
    data class Suggestions(val apps: List<AppEntry>) : DrawerItem { override val id = "suggestions" }
}

/** Contacts trouvés pour une saisie donnée (pour savoir si la réponse correspond à ce qui est tapé). */
private data class ContactHits(val query: String, val list: List<ContactResult>)

/** Pause après la dernière lettre tapée avant d'interroger les contacts. */
private const val CONTACTS_DEBOUNCE_MS = 120L

/** Avec la recherche de contacts : lettres minimales et pause avant le lancement automatique d'une app. */
private const val AUTO_LAUNCH_MIN_WITH_CONTACTS = 3
private const val AUTO_LAUNCH_PAUSE_WITH_CONTACTS_MS = 700L

/** Score minimal pour qu'une app soit suggérée (environ un lancement récent). */
private const val SUGGESTION_MIN_SCORE = 0.5

/** En dessous de ce nombre d'apps assez utilisées, la rangée de suggestions ne s'affiche pas. */
private const val SUGGESTION_MIN_COUNT = 4

@Composable
fun AppDrawer(state: LauncherState, actions: LauncherActions) {
    val apps = state.visibleApps
    val query = state.query
    val focusManager = LocalFocusManager.current
    val scores = if (state.sortByUsage) state.usageScores else emptyMap()

    val results = remember(apps, query, scores) { search(apps, query, scores) }
    val calc = remember(query) { Calculator.evaluate(query) }
    val settingMatches = remember(query) { SettingsShortcuts.match(query) }

    // Contacts : interrogés hors du thread principal, peu après la dernière lettre tapée.
    val contactsOn = state.searchContacts && state.hasContactsAccess
    val contactQuery = query.trim()
    val wantsContacts = contactsOn && calc == null && AppEntry.normalize(contactQuery).length >= 2
    var contactHits by remember { mutableStateOf(ContactHits("", emptyList())) }
    LaunchedEffect(contactQuery, wantsContacts) {
        if (!wantsContacts) return@LaunchedEffect
        delay(CONTACTS_DEBOUNCE_MS)
        val found = withContext(Dispatchers.IO) { actions.searchContacts(contactQuery) }
        contactHits = ContactHits(contactQuery, found)
    }
    val contactsReady = !wantsContacts || contactHits.query == contactQuery
    // Pendant la frappe, on garde les contacts précédents à l'écran (pas de clignotement).
    val contacts = if (wantsContacts) contactHits.list else emptyList()

    // Lancement automatique quand la recherche ne laisse qu'une seule app, comme avant. On attend la
    // réponse des contacts ; si des contacts (ou un calcul) s'affichent aussi, on ne lance rien :
    // l'utilisateur choisit (la touche Entrée du clavier ouvre toujours la première app).
    val othersShown = contacts.isNotEmpty() || calc != null
    LaunchedEffect(results, query, contactsReady, othersShown) {
        if (state.autoLaunch && query.length >= 2 && results.size == 1 && contactsReady && !othersShown) {
            if (contactsOn) {
                // Recherche de contacts active : on laisse le temps de taper un nom. Au moins 3 lettres,
                // puis une courte pause sans frappe (l'effet est annulé à chaque nouvelle lettre).
                if (AppEntry.normalize(contactQuery).length < AUTO_LAUNCH_MIN_WITH_CONTACTS) return@LaunchedEffect
                delay(AUTO_LAUNCH_PAUSE_WITH_CONTACTS_MS)
            }
            focusManager.clearFocus()
            actions.launch(results.first())
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .themeBackdrop()
            .systemBarsPadding()
            .imePadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            SearchField(
                value = query,
                onValueChange = { state.query = it },
                onGo = {
                    // Entrée : la première app, sinon copier le calcul, sinon chercher sur le web.
                    val first = results.firstOrNull()
                    when {
                        first != null -> actions.launch(first)
                        calc != null -> actions.copyToClipboard(calc)
                        query.isNotBlank() -> actions.webSearch(query)
                    }
                },
                textScale = state.textScale,
            )
            if (query.isBlank()) {
                AlphabeticalList(state, actions, apps)
            } else {
                SearchResults(
                    state = state,
                    actions = actions,
                    query = query,
                    results = results,
                    calc = calc,
                    contacts = contacts,
                    // Invitation seulement quand aucune app ne correspond : c'est là qu'on cherche une personne.
                    inviteContacts = !contactsOn && results.isEmpty() && calc == null &&
                        AppEntry.normalize(contactQuery).length >= 2,
                    settings = settingMatches,
                )
            }
        }
    }
}

/**
 * Les apps dont le nom commence par la recherche passent avant celles qui la contiennent.
 * Dans chaque groupe, les plus utilisées d'abord ([scores] vide = ordre alphabétique).
 */
private fun search(apps: List<AppEntry>, query: String, scores: Map<String, Double>): List<AppEntry> {
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
    if (scores.isEmpty()) return starts + wordStarts + contains
    // Tri stable : à score égal, l'ordre alphabétique est conservé.
    val byUsage = compareByDescending<AppEntry> { scores[it.key] ?: 0.0 }
    return starts.sortedWith(byUsage) + wordStarts.sortedWith(byUsage) + contains.sortedWith(byUsage)
}

/** Dans chaque lettre (suite d'apps de même lettre), les plus utilisées d'abord. */
private fun sortWithinLetters(apps: List<AppEntry>, scores: Map<String, Double>): List<AppEntry> {
    if (scores.isEmpty()) return apps
    val byUsage = compareByDescending<AppEntry> { scores[it.key] ?: 0.0 }
    val result = ArrayList<AppEntry>(apps.size)
    var start = 0
    while (start < apps.size) {
        var end = start
        while (end < apps.size && apps[end].letter == apps[start].letter) end++
        result += apps.subList(start, end).sortedWith(byUsage)
        start = end
    }
    return result
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
    val shape = auraShape(AuraRadius.m)
    val style = AuraType.row.scaled(textScale).homeFont()
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
                        Text("Rechercher une app, un calcul…", color = AuraColors.TextFaint, style = style)
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

/**
 * Résultats de la recherche universelle : calcul (réponse directe), apps, puis sections discrètes
 * Contacts, Réglages Android et, en dernier, la recherche web.
 */
@Composable
private fun SearchResults(
    state: LauncherState,
    actions: LauncherActions,
    query: String,
    results: List<AppEntry>,
    calc: String?,
    contacts: List<ContactResult>,
    inviteContacts: Boolean,
    settings: List<SettingsShortcut>,
) {
    val iconSize = (34 * state.textScale).dp
    val rowStyle = AuraType.row.scaled(state.textScale).homeFont()
    LazyColumn(
        contentPadding = PaddingValues(
            start = AuraSpace.gutter,
            end = AuraSpace.gutter,
            top = AuraSpace.s,
            bottom = AuraSpace.xl,
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (calc != null) {
            item(key = "calc") {
                CalcResultRow(calc, state.showIcons, iconSize, rowStyle) { actions.copyToClipboard(calc) }
            }
        }
        items(results, key = { it.key }) { app ->
            AppRow(
                app = app,
                actions = actions,
                showIcon = state.showIcons,
                iconSize = iconSize,
                textStyle = rowStyle,
                badge = state.notificationsFor(app) != null,
                menu = drawerMenu(state, actions, app),
            )
        }
        if (results.isEmpty() && calc == null) {
            item(key = "no-app") {
                Text(
                    "Aucune app ne correspond (les apps masquées n'apparaissent pas).",
                    color = AuraColors.TextDim,
                    style = AuraType.bodySmall,
                    modifier = Modifier.padding(vertical = AuraSpace.s),
                )
            }
        }
        if (contacts.isNotEmpty()) {
            item(key = "h:contacts") { SearchSectionHeader("Contacts") }
            items(contacts, key = { it.key }) { contact ->
                ContactRow(contact, actions, state.showIcons, iconSize, rowStyle)
            }
        } else if (inviteContacts) {
            item(key = "h:contacts") { SearchSectionHeader("Contacts") }
            item(key = "invite-contacts") {
                ContactsInviteRow(state.showIcons, iconSize) { actions.setSearchContacts(true) }
            }
        }
        if (settings.isNotEmpty()) {
            item(key = "h:settings") { SearchSectionHeader("Réglages Android") }
            items(settings, key = { "set:" + it.id }) { shortcut ->
                SettingsShortcutRow(shortcut, actions, state.showIcons, iconSize, rowStyle)
            }
        }
        if (query.isNotBlank()) {
            item(key = "h:web") { SearchSectionHeader("Web") }
            item(key = "web") { WebSearchRow(query, actions, state.showIcons, iconSize) }
        }
    }
}

private fun drawerMenu(state: LauncherState, actions: LauncherActions, app: AppEntry) = listOf(
    MenuAction(if (app.key in state.favorites) "Retirer de l'accueil" else "Épingler à l'accueil") {
        actions.toggleFavorite(app)
    },
    // --- Dossiers ---
    MenuAction("Ajouter à un dossier…") { state.folderPicking = app },
    MenuAction("Renommer") { state.renaming = app },
    MenuAction("Masquer") { actions.setHidden(app.key, true) },
    MenuAction("Infos de l'app") { actions.openAppInfo(app) },
    MenuAction("Désinstaller") { actions.uninstall(app) },
)

@Composable
private fun AlphabeticalList(state: LauncherState, actions: LauncherActions, apps: List<AppEntry>) {
    val scores = state.usageScores
    val sortByUsage = state.sortByUsage
    val showSuggestions = state.showSuggestions
    val favorites = state.favorites
    val entries = remember(apps, scores, sortByUsage, showSuggestions, favorites) {
        // Apps suggérées : les plus utilisées qui ne sont pas déjà sur l'accueil.
        val suggestions = if (!showSuggestions) {
            emptyList()
        } else {
            val favoriteSet = favorites.toSet()
            apps.filter { it.key !in favoriteSet && (scores[it.key] ?: 0.0) >= SUGGESTION_MIN_SCORE }
                .sortedByDescending { scores[it.key] ?: 0.0 }
                .take(SUGGESTION_SLOTS)
                .takeIf { it.size >= SUGGESTION_MIN_COUNT }
                .orEmpty()
        }
        val ordered = if (sortByUsage) sortWithinLetters(apps, scores) else apps
        buildList<DrawerItem> {
            if (suggestions.isNotEmpty()) add(DrawerItem.Suggestions(suggestions))
            var current: String? = null
            for (app in ordered) {
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
                    is DrawerItem.Suggestions -> SuggestedApps(item.apps, actions, state.showIcons, state.textScale)
                    is DrawerItem.Header -> LetterHeader(item.letter)
                    is DrawerItem.App -> AppRow(
                        app = item.app,
                        actions = actions,
                        showIcon = state.showIcons,
                        iconSize = (34 * state.textScale).dp,
                        textStyle = AuraType.row.scaled(state.textScale).homeFont(),
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
        Text(text = letter, color = AuraColors.Accent, style = AuraType.letter.homeFont())
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
            haptics.auraHaptic(HapticFeedbackType.TextHandleMove)
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
                    style = AuraType.letterBar.homeFont(),
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
                    style = AuraType.screenTitle.copy(fontWeight = FontWeight.Light).homeFont(),
                )
            }
        }
    }
}

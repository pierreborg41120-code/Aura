package com.aura.launcher.ui

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import com.aura.launcher.ThemeId
import com.aura.launcher.withTheme
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.launcher.ACCENT_AUTO
import com.aura.launcher.Appearance
import com.aura.launcher.ClockStyle
import com.aura.launcher.FontChoice
import com.aura.launcher.HomeAlignment
import com.aura.launcher.LauncherActions
import com.aura.launcher.LauncherState
import com.aura.launcher.VeilLevel

/**
 * --- Apparence --- panneau à onglets affiché en bas de l'écran pendant l'édition
 * (l'accueil, en aperçu, occupe le reste de l'écran au-dessus).
 * Chaque choix est appliqué tout de suite (previewAppearance) ; « Terminé » enregistre ;
 * Retour propose d'annuler si quelque chose a changé.
 * Le panneau garde la police du système : seuls les aperçus de l'onglet Police changent de famille.
 */
@Composable
fun AppearanceEditor(state: LauncherState, actions: LauncherActions) {
    // Apparence enregistrée au moment de l'ouverture : sert à savoir s'il y a des changements à annuler.
    val initial = remember { state.appearance }
    var confirmCancel by remember { mutableStateOf(false) }
    val reduced = rememberReducedMotion()

    // Prioritaire sur celui de LauncherScreen (composé après lui).
    // Désactivé pendant l'animation de sortie (l'éditeur est déjà fermé).
    BackHandler(enabled = state.appearanceEditing) {
        if (state.appearance != initial) confirmCancel = true else actions.cancelAppearance()
    }

    // L'entrée et la sortie du panneau sont animées par LauncherScreen.
    EditorPanel(
        state = state,
        reduced = reduced,
        onChange = { actions.previewAppearance(it) },
        onDone = { actions.saveAppearance() },
        onCancel = { actions.cancelAppearance() },
    )

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            containerColor = AuraColors.Surface,
            title = { Text("Annuler les changements ?", color = AuraColors.Text) },
            text = {
                Text(
                    "L'apparence reviendra comme avant l'ouverture de l'éditeur.",
                    color = AuraColors.TextDim,
                    style = AuraType.body,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    actions.cancelAppearance()
                }) { Text("Annuler les changements", color = AuraColors.Accent, style = AuraType.label) }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) {
                    Text("Continuer", color = AuraColors.TextDim, style = AuraType.label)
                }
            },
        )
    }
}

/** Onglets de l'éditeur, dans l'ordre d'affichage. */
private enum class EditorTab(val label: String) {
    THEME("Thème"),
    CLOCK("Horloge"),
    FONT("Police"),
    COLOR("Couleur"),
    BACKGROUND("Fond"),
    ALIGNMENT("Alignement"),
}

@Composable
private fun EditorPanel(
    state: LauncherState,
    reduced: Boolean,
    onChange: (Appearance) -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(EditorTab.THEME) }
    val appearance = state.appearance
    // Hauteur fixe (environ 40 % de l'écran) : l'accueil au-dessus ne saute pas d'un onglet à l'autre.
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val panelHeight = (screenHeight * 0.40f).coerceIn(280f, 380f).dp
    val shape = auraShape(topStart = AuraRadius.xl, topEnd = AuraRadius.xl)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AuraColors.Surface)
            .border(1.dp, AuraColors.Hairline, shape)
            // Le panneau avale les touchers qui tombent entre deux contrôles.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .navigationBarsPadding()
            .height(panelHeight),
    ) {
        // En-tête : titre, « Annuler » discret, « Terminé » à l'accent.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = AuraSpace.xl, end = AuraSpace.m, top = AuraSpace.s),
        ) {
            Text(
                "Apparence",
                color = AuraColors.Text,
                style = AuraType.body.copy(fontSize = 18.sp, fontWeight = FontWeight.Medium),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = AuraSpace.touch)) {
                Text("Annuler", color = AuraColors.TextDim, style = AuraType.label)
            }
            Spacer(Modifier.width(AuraSpace.xs))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .heightIn(min = AuraSpace.touch)
                    .padding(vertical = AuraSpace.xs)
                    .clip(auraShape(AuraRadius.s))
                    .background(AuraColors.Accent)
                    .clickable(role = Role.Button, onClick = onDone)
                    .padding(horizontal = AuraSpace.l, vertical = AuraSpace.s),
            ) {
                Text("Terminé", color = AuraColors.OnAccent, style = AuraType.button)
            }
        }

        // Rangée d'onglets, défilable si l'écran est étroit ; l'onglet actif est souligné à l'accent.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = AuraSpace.m)
                .selectableGroup(),
        ) {
            EditorTab.entries.forEach { t ->
                TabItem(t.label, selected = t == tab, reduced = reduced) { tab = t }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(AuraColors.Hairline))

        Crossfade(
            targetState = tab,
            animationSpec = if (reduced) snap() else tween(AuraMotion.quick),
            label = "onglet",
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { current ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = AuraSpace.l),
            ) {
                when (current) {
                    EditorTab.THEME -> ThemeTab(appearance, reduced) { onChange(appearance.withTheme(it)) }
                    EditorTab.CLOCK -> ClockTab(appearance, reduced) { onChange(appearance.copy(clock = it)) }
                    EditorTab.FONT -> FontTab(appearance, reduced) { onChange(appearance.copy(font = it)) }
                    EditorTab.COLOR -> ColorTab(appearance, state.wallpaperColor) { onChange(appearance.copy(accent = it)) }
                    EditorTab.BACKGROUND -> BackgroundTab(
                        appearance = appearance,
                        blurSupported = state.blurSupported,
                        reduced = reduced,
                        onVeil = { onChange(appearance.copy(veil = it)) },
                        onBlur = { onChange(appearance.copy(blurDrawer = it)) },
                    )
                    EditorTab.ALIGNMENT -> AlignmentTab(appearance, reduced) { onChange(appearance.copy(alignment = it)) }
                }
            }
        }
    }
}

/** Spécification d'animation des couleurs : coupée si les animations le sont. */
private fun colorSpec(reduced: Boolean): AnimationSpec<Color> =
    if (reduced) snap() else tween(AuraMotion.quick)

@Composable
private fun TabItem(label: String, selected: Boolean, reduced: Boolean, onClick: () -> Unit) {
    val textColor by animateColorAsState(
        if (selected) AuraColors.Text else AuraColors.TextFaint,
        colorSpec(reduced),
        label = "texteOnglet",
    )
    val lineColor by animateColorAsState(
        if (selected) AuraColors.Accent else Color.Transparent,
        colorSpec(reduced),
        label = "soulignement",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .heightIn(min = AuraSpace.touch)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = AuraSpace.m)
            .drawBehind {
                // Soulignement arrondi sous le libellé.
                val stroke = 2.dp.toPx()
                drawLine(
                    lineColor,
                    start = Offset(0f, size.height - stroke),
                    end = Offset(size.width, size.height - stroke),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            },
    ) {
        Text(label, color = textColor, style = AuraType.label)
    }
}

/** Carte d'option sélectionnable : fond de carte, filet, cerclée à l'accent si choisie. */
@Composable
private fun OptionCard(
    selected: Boolean,
    reduced: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = auraShape(AuraRadius.m)
    val border by animateColorAsState(
        if (selected) AuraColors.Accent else AuraColors.Hairline,
        colorSpec(reduced),
        label = "bordure",
    )
    val bg by animateColorAsState(
        if (selected) AuraColors.Accent.copy(alpha = 0.14f) else AuraColors.SurfaceRaised,
        colorSpec(reduced),
        label = "fondCarte",
    )
    Box(
        modifier = modifier
            .heightIn(min = AuraSpace.touch)
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(AuraSpace.m),
    ) {
        content()
    }
}

/** Rangée défilable horizontalement, avec la marge du panneau. */
@Composable
private fun OptionsRow(content: @Composable RowScope.() -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(AuraSpace.s),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = AuraSpace.xl)
            .selectableGroup(),
        content = content,
    )
}

@Composable
private fun OptionName(name: String, selected: Boolean) {
    Text(
        name,
        color = if (selected) AuraColors.AccentLight else AuraColors.TextDim,
        style = AuraType.label,
        maxLines = 1,
    )
}

// --- Onglet Horloge ---

/**
 * --- Thèmes --- choix de l'habillage. Chaque carte montre les couleurs du thème (fond, accent,
 * détail) et ses formes ; la choisir applique aussi son préréglage (police, horloge, couleur, voile).
 */
@Composable
private fun ThemeTab(appearance: Appearance, reduced: Boolean, onSelect: (ThemeId) -> Unit) {
    val options = listOf(
        ThemeId.ECLIPSE to "Éclipse",
        ThemeId.NEON to "Néon",
    )
    OptionsRow {
        options.forEach { (id, name) ->
            val selected = appearance.theme == id
            val spec = specFor(id)
            val accent = spec.themeAccent ?: AuraColors.Lavender
            val detail = spec.secondary ?: lerp(accent, Color.White, 0.55f)
            OptionCard(selected, reduced, onClick = { onSelect(id) }, modifier = Modifier.width(150.dp)) {
                Column {
                    val previewShape = if (spec.cutCorners) CutCornerShape(8.dp) else RoundedCornerShape(14.dp)
                    Box(
                        Modifier
                            .height(72.dp)
                            .fillMaxWidth()
                            .clip(previewShape)
                            .background(spec.night)
                            .border(1.dp, spec.hairline, previewShape)
                            .padding(AuraSpace.s),
                    ) {
                        Column {
                            Text(
                                "21:49",
                                color = spec.text,
                                style = AuraType.body.copy(
                                    fontFamily = if (id == ThemeId.NEON) fontFamilyFor(FontChoice.CHAKRA_PETCH) else FontFamily.SansSerif,
                                    fontWeight = if (id == ThemeId.NEON) FontWeight.Medium else FontWeight.ExtraLight,
                                    fontSize = 24.sp,
                                    lineHeight = 28.sp,
                                    shadow = if (id == ThemeId.NEON) androidx.compose.ui.graphics.Shadow(accent.copy(alpha = 0.6f), androidx.compose.ui.geometry.Offset.Zero, 14f) else null,
                                ),
                            )
                            Spacer(Modifier.height(AuraSpace.xs))
                            Row(horizontalArrangement = Arrangement.spacedBy(AuraSpace.xs)) {
                                Box(Modifier.size(width = 28.dp, height = 6.dp).clip(previewShape).background(accent))
                                Box(Modifier.size(width = 14.dp, height = 6.dp).clip(previewShape).background(detail))
                            }
                        }
                    }
                    Spacer(Modifier.height(AuraSpace.s))
                    OptionName(name, selected)
                }
            }
        }
    }
    Text(
        "Choisir un thème règle aussi la police, l'horloge, la couleur et le voile. Tu peux les changer ensuite dans les autres onglets.",
        color = AuraColors.TextFaint,
        style = AuraType.caption,
        modifier = Modifier.padding(horizontal = AuraSpace.xl, vertical = AuraSpace.m),
    )
}

@Composable
private fun ClockTab(appearance: Appearance, reduced: Boolean, onSelect: (ClockStyle) -> Unit) {
    val options = listOf(
        ClockStyle.THIN to "Fine",
        ClockStyle.STACKED to "Empilée",
        ClockStyle.WORDS to "En toutes lettres",
        ClockStyle.COMPACT to "Compacte",
        ClockStyle.NEON to "Néon",
    )
    OptionsRow {
        options.forEach { (style, name) ->
            val selected = appearance.clock == style
            OptionCard(selected, reduced, onClick = { onSelect(style) }, modifier = Modifier.width(132.dp)) {
                Column {
                    Box(Modifier.height(72.dp).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        ClockSample(style)
                    }
                    Spacer(Modifier.height(AuraSpace.s))
                    OptionName(name, selected)
                }
            }
        }
    }
}

/** Mini aperçu typographique de chaque style d'horloge. */
@Composable
private fun ClockSample(style: ClockStyle) {
    val thin = AuraType.clock.copy(fontSize = 34.sp, lineHeight = 36.sp, letterSpacing = (-1).sp)
    when (style) {
        ClockStyle.THIN -> Text("21:49", color = AuraColors.Text, style = thin)
        ClockStyle.STACKED -> Text(
            "21\n49",
            color = AuraColors.Text,
            style = thin.copy(fontSize = 28.sp, lineHeight = 28.sp),
        )
        ClockStyle.WORDS -> Text(
            "Vingt et une\nheures…",
            color = AuraColors.Text,
            style = AuraType.body.copy(fontWeight = FontWeight.Light, fontSize = 15.sp, lineHeight = 19.sp),
        )
        ClockStyle.NEON -> Text(
            "21:49",
            color = AuraColors.Text,
            style = thin.copy(
                fontFamily = fontFamilyFor(FontChoice.CHAKRA_PETCH),
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.sp,
                shadow = androidx.compose.ui.graphics.Shadow(AuraColors.Accent.copy(alpha = 0.6f), androidx.compose.ui.geometry.Offset.Zero, 16f),
            ),
        )
        ClockStyle.COMPACT -> Text(
            "21:49 · jeu. 19",
            color = AuraColors.Text,
            style = AuraType.body.copy(fontWeight = FontWeight.Light, fontSize = 15.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// --- Onglet Police ---

@Composable
private fun FontTab(appearance: Appearance, reduced: Boolean, onSelect: (FontChoice) -> Unit) {
    val options = listOf(
        FontChoice.SYSTEM to "Système",
        FontChoice.MANROPE to "Manrope",
        FontChoice.INSTRUMENT_SERIF to "Instrument Serif",
        FontChoice.SPACE_MONO to "Space Mono",
        FontChoice.CHAKRA_PETCH to "Chakra Petch",
    )
    OptionsRow {
        options.forEach { (choice, name) ->
            val selected = appearance.font == choice
            val family = fontFamilyFor(choice)
            OptionCard(selected, reduced, onClick = { onSelect(choice) }, modifier = Modifier.width(132.dp)) {
                Column {
                    Text("Aa", color = AuraColors.Text, style = AuraType.body.copy(fontFamily = family, fontSize = 32.sp, lineHeight = 38.sp))
                    Text("21:49", color = AuraColors.TextDim, style = AuraType.body.copy(fontFamily = family, fontSize = 16.sp))
                    Spacer(Modifier.height(AuraSpace.s))
                    // Le nom aussi dans sa police : on choisit en voyant.
                    Text(
                        name,
                        color = if (selected) AuraColors.AccentLight else AuraColors.TextDim,
                        style = AuraType.label.copy(fontFamily = family),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// --- Onglet Couleur ---

@Composable
private fun ColorTab(appearance: Appearance, wallpaperColor: Int?, onSelect: (Int) -> Unit) {
    val autoColor = resolveAccent(ACCENT_AUTO, wallpaperColor, appearance.theme)
    // --- Thèmes --- un thème qui a sa propre couleur la garde en « Auto ».
    val themeColor = specFor(appearance.theme).themeAccent != null
    OptionsRow {
        Swatch(
            color = autoColor,
            auto = true,
            name = "Auto",
            selected = appearance.accent == ACCENT_AUTO,
            onClick = { onSelect(ACCENT_AUTO) },
        )
        AccentPalette.forEachIndexed { index, (name, color) ->
            Swatch(
                color = color,
                auto = false,
                name = name,
                selected = appearance.accent == index,
                onClick = { onSelect(index) },
            )
        }
    }
    val chosen = AccentPalette.getOrNull(appearance.accent)?.first
    Text(
        when {
            chosen != null -> chosen
            themeColor -> "Auto · couleur du thème"
            else -> "Auto · d'après ton fond d'écran"
        },
        color = AuraColors.TextDim,
        style = AuraType.body,
        modifier = Modifier.padding(horizontal = AuraSpace.xl, vertical = AuraSpace.m),
    )
}

/**
 * Pastille de couleur. Choisie : cerclée d'un anneau de sa couleur (le motif « anneau » d'Aura).
 * « Auto » est une éclipse : la couleur du fond d'écran à moitié couverte par un disque nuit.
 */
@Composable
private fun Swatch(color: Color, auto: Boolean, name: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(auraShape(AuraRadius.s))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = name }
            .padding(AuraSpace.xs),
    ) {
        Canvas(Modifier.size(AuraSpace.touch)) {
            val r = size.minDimension / 2f
            val disc = r * 0.68f
            drawCircle(color, radius = disc)
            if (auto) {
                // Disque nuit décalé : l'éclipse de l'icône, en tout petit.
                drawCircle(
                    AuraColors.Night,
                    radius = disc * 0.82f,
                    center = Offset(center.x + disc * 0.42f, center.y - disc * 0.22f),
                )
            }
            if (selected) {
                drawCircle(color, radius = r - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
            }
        }
        if (auto) {
            Text("Auto", color = if (selected) AuraColors.AccentLight else AuraColors.TextFaint, style = AuraType.caption)
        } else {
            Spacer(Modifier.height(AuraType.caption.lineHeight.value.dp))
        }
    }
}

// --- Onglet Fond ---

@Composable
private fun BackgroundTab(
    appearance: Appearance,
    blurSupported: Boolean,
    reduced: Boolean,
    onVeil: (VeilLevel) -> Unit,
    onBlur: (Boolean) -> Unit,
) {
    Text(
        "Voile",
        color = AuraColors.Text,
        style = AuraType.body,
        modifier = Modifier.padding(horizontal = AuraSpace.xl),
    )
    Spacer(Modifier.height(AuraSpace.s))
    val veils = listOf(
        VeilLevel.NONE to "Aucun",
        VeilLevel.LIGHT to "Léger",
        VeilLevel.MEDIUM to "Moyen",
        VeilLevel.STRONG to "Fort",
    )
    OptionsRow {
        veils.forEach { (level, name) ->
            val selected = appearance.veil == level
            OptionCard(
                selected,
                reduced,
                onClick = { onVeil(level) },
            ) {
                Box(Modifier.padding(horizontal = AuraSpace.xs), contentAlignment = Alignment.Center) {
                    OptionName(name, selected)
                }
            }
        }
    }
    Spacer(Modifier.height(AuraSpace.l))

    // Flou du fond à l'ouverture du tiroir : désactivé (et expliqué) si le téléphone ne sait pas flouter.
    val checked = appearance.blurDrawer && blurSupported
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = blurSupported, role = Role.Switch, onValueChange = onBlur)
            .padding(horizontal = AuraSpace.xl, vertical = AuraSpace.s),
    ) {
        Text(
            "Flouter le fond à l'ouverture du tiroir",
            color = if (blurSupported) AuraColors.Text else AuraColors.TextFaint,
            style = AuraType.body,
            modifier = Modifier.weight(1f).padding(end = AuraSpace.l),
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = blurSupported,
            colors = auraSwitchColors(),
        )
    }
    if (!blurSupported) {
        Text(
            "Ton téléphone ne permet pas le flou (Android 12 ou plus, et flou activé dans le système).",
            color = AuraColors.TextFaint,
            style = AuraType.caption,
            modifier = Modifier.padding(horizontal = AuraSpace.xl),
        )
    }
}

// --- Onglet Alignement ---

@Composable
private fun AlignmentTab(appearance: Appearance, reduced: Boolean, onSelect: (HomeAlignment) -> Unit) {
    val options = listOf(
        Triple(HomeAlignment.START, "Gauche", null),
        Triple(HomeAlignment.CENTER, "Centré", "Les icônes sont masquées sur l'accueil"),
        Triple(HomeAlignment.END, "Droite", "Les icônes passent à droite du nom"),
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(AuraSpace.s),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpace.xl)
            .selectableGroup(),
    ) {
        options.forEach { (alignment, name, note) ->
            val selected = appearance.alignment == alignment
            OptionCard(selected, reduced, onClick = { onSelect(alignment) }, modifier = Modifier.weight(1f)) {
                Column {
                    AlignmentSchema(alignment, if (selected) AuraColors.Accent else AuraColors.TextDim)
                    Spacer(Modifier.height(AuraSpace.s))
                    OptionName(name, selected)
                    if (note != null) {
                        Text(
                            note,
                            color = AuraColors.TextFaint,
                            style = AuraType.caption,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.padding(top = AuraSpace.xxs),
                        )
                    }
                }
            }
        }
    }
}

/** Mini schéma : une ligne épaisse (l'heure) et trois plus fines, alignées comme l'accueil. */
@Composable
private fun AlignmentSchema(alignment: HomeAlignment, color: Color) {
    Canvas(Modifier.fillMaxWidth().height(44.dp)) {
        val widths = listOf(0.7f, 0.45f, 0.6f, 0.35f)
        val strokes = listOf(4.dp.toPx(), 2.dp.toPx(), 2.dp.toPx(), 2.dp.toPx())
        val gap = size.height / widths.size
        widths.forEachIndexed { i, fraction ->
            val w = size.width * fraction
            val x = when (alignment) {
                HomeAlignment.START -> 0f
                HomeAlignment.CENTER -> (size.width - w) / 2f
                HomeAlignment.END -> size.width - w
            }
            val y = gap * (i + 0.5f)
            val stroke = strokes[i]
            // Lignes arrondies : on raccourcit des deux bouts pour que l'arrondi reste dans la carte.
            drawLine(
                color.copy(alpha = if (i == 0) 1f else 0.7f),
                start = Offset(x + stroke / 2f, y),
                end = Offset(x + w - stroke / 2f, y),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

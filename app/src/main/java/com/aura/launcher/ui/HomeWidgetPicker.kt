package com.aura.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aura.launcher.AppEntry
import com.aura.launcher.LauncherActions
import com.aura.launcher.LauncherState
import com.aura.launcher.WidgetGroup
import com.aura.launcher.WidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Au-delà de ce nombre de widgets, le sélecteur propose un champ de recherche. */
private const val SEARCH_THRESHOLD = 8

/**
 * Sélecteur du widget d'accueil, en plein écran : tous les widgets installés, groupés par app
 * (nom de l'app, puis aperçu, nom et taille de chaque widget). Recherche si la liste est longue.
 */
@Composable
fun HomeWidgetPicker(state: LauncherState, actions: LauncherActions) {
    BackHandler { state.homeWidgetPicking = false }

    val groups by produceState<List<WidgetGroup>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { actions.loadAllWidgetGroups() }
    }
    var query by remember { mutableStateOf("") }
    val total = groups?.sumOf { it.providers.size } ?: 0
    val filtered = remember(groups, query) {
        val all = groups.orEmpty()
        val q = AppEntry.normalize(query)
        if (q.isEmpty()) {
            all
        } else {
            all.mapNotNull { group ->
                // Le nom de l'app suffit à garder tout le groupe ; sinon on filtre par nom de widget.
                if (AppEntry.normalize(group.appLabel).contains(q)) {
                    group
                } else {
                    group.providers
                        .filter { AppEntry.normalize(it.label).contains(q) }
                        .takeIf { it.isNotEmpty() }
                        ?.let { group.copy(providers = it) }
                }
            }
        }
    }
    val previewMaxPx = with(LocalDensity.current) { 280.dp.roundToPx() }

    Column(
        Modifier
            .fillMaxSize()
            .themeBackdrop()
            // Absorbe les touchers : rien ne doit atteindre l'accueil en dessous.
            .pointerInput(Unit) { detectTapGestures { } }
            .systemBarsPadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = AuraSpace.gutter, end = AuraSpace.s, top = AuraSpace.xl),
        ) {
            Text(
                "Choisir un widget",
                color = AuraColors.Text,
                style = AuraType.screenTitle,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            CloseButton { state.homeWidgetPicking = false }
        }

        if (total > SEARCH_THRESHOLD) {
            PickerSearchField(query) { query = it }
        }

        when {
            groups == null -> PickerMessage("Recherche des widgets installés…")
            total == 0 -> PickerMessage("Aucune app installée ne propose de widget.")
            filtered.isEmpty() -> PickerMessage("Aucun widget pour « ${query.trim()} ». Essaie le nom de l'app.")
            else -> LazyColumn(
                contentPadding = PaddingValues(bottom = AuraSpace.xxl),
                modifier = Modifier.fillMaxSize(),
            ) {
                filtered.forEach { group ->
                    item(key = "app:" + group.key) {
                        Text(
                            group.appLabel,
                            color = AuraColors.TextDim,
                            style = AuraType.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { heading() }
                                .padding(start = AuraSpace.gutter, end = AuraSpace.gutter, top = AuraSpace.xl, bottom = AuraSpace.xs),
                        )
                    }
                    items(group.providers, key = { "w:" + group.key + "/" + it.key }) { provider ->
                        PickerItem(provider, actions, previewMaxPx) { actions.addHomeWidget(provider) }
                    }
                }
            }
        }
    }
}

/** Un widget : aperçu sur une petite surface, puis son nom et sa taille en cases. */
@Composable
private fun PickerItem(provider: WidgetProvider, actions: LauncherActions, previewMaxPx: Int, onPick: () -> Unit) {
    val preview by produceState<ImageBitmap?>(initialValue = null, provider.key) {
        value = withContext(Dispatchers.IO) { actions.loadWidgetPreview(provider, previewMaxPx) }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpace.l)
            .clip(auraShape(AuraRadius.l))
            .clickable(role = Role.Button, onClickLabel = "Ajouter à l'accueil", onClick = onPick)
            .padding(horizontal = AuraSpace.m, vertical = AuraSpace.m),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp)
                .clip(auraShape(AuraRadius.m))
                .background(AuraColors.Surface)
                .border(1.dp, AuraColors.Hairline, auraShape(AuraRadius.m))
                .padding(AuraSpace.m),
        ) {
            preview?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.heightIn(max = 140.dp),
                )
            }
        }
        Spacer(Modifier.height(AuraSpace.s))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                provider.label,
                color = AuraColors.Text,
                style = AuraType.row,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            provider.cells?.let {
                Spacer(Modifier.width(AuraSpace.m))
                Text(it, color = AuraColors.TextDim, style = AuraType.caption)
            }
        }
    }
}

@Composable
private fun PickerMessage(text: String) {
    Text(
        text,
        color = AuraColors.TextDim,
        style = AuraType.body,
        modifier = Modifier.padding(horizontal = AuraSpace.gutter, vertical = AuraSpace.xl),
    )
}

/** Champ de recherche, même dessin que celui du tiroir (surface, filet, lavande au focus). */
@Composable
private fun PickerSearchField(value: String, onValueChange: (String) -> Unit) {
    val shape = auraShape(AuraRadius.m)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = AuraType.body.copy(color = AuraColors.Text),
        cursorBrush = SolidColor(AuraColors.Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = AuraSpace.l, end = AuraSpace.l, top = AuraSpace.l),
        decorationBox = { inner ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(AuraColors.Surface)
                    .border(1.dp, if (value.isEmpty()) AuraColors.Hairline else AuraColors.Accent.copy(alpha = 0.5f), shape)
                    .padding(horizontal = AuraSpace.l)
                    .heightIn(min = 52.dp),
            ) {
                Canvas(Modifier.size(16.dp)) {
                    // Loupe : un cercle et un manche.
                    val stroke = 1.5.dp.toPx()
                    val r = size.minDimension * 0.34f
                    val c = Offset(r + stroke, r + stroke)
                    drawCircle(AuraColors.TextFaint, radius = r, center = c, style = Stroke(stroke))
                    drawLine(AuraColors.TextFaint, Offset(c.x + r * 0.72f, c.y + r * 0.72f), Offset(size.width, size.height), stroke, StrokeCap.Round)
                }
                Spacer(Modifier.width(AuraSpace.m))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text("Rechercher une app ou un widget", color = AuraColors.TextFaint, style = AuraType.body)
                    inner()
                }
            }
        },
    )
}

/** Croix de fermeture dessinée (zone de toucher de 48 dp). */
@Composable
private fun CloseButton(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(AuraSpace.touch)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Fermer" },
    ) {
        Canvas(Modifier.size(14.dp)) {
            val stroke = 1.6.dp.toPx()
            drawLine(AuraColors.TextDim, Offset.Zero, Offset(size.width, size.height), stroke, StrokeCap.Round)
            drawLine(AuraColors.TextDim, Offset(size.width, 0f), Offset(0f, size.height), stroke, StrokeCap.Round)
        }
    }
}

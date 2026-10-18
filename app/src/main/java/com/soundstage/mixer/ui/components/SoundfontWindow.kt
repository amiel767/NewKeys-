@file:Suppress("unused")
@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.soundstage.mixer.ui.components

/*
 * SoundfontWindow.kt — nouvelle fenêtre Soundfonts (Æther Keys)
 *
 * Remplace SoundfontDialog.kt. Autonome : state immuable + actions, aucun
 * moteur touché. Le câblage ViewModel se fait dans MixerScreen.kt.
 *
 * Fonctionnalités :
 *  - 15 catégories (Favorites en premier, Other en dernier).
 *  - Classifieur regex par nom + overrides manuels persistants.
 *  - Favoris globaux (clé = fileId|bank|presetId).
 *  - Catégories vides masquées SAUF si toutes vides (sidebar jamais vide).
 *  - Onglets Presets/All dans la colonne gauche, en haut.
 *  - Recherche avec highlight.
 *  - Swipe gauche sur FICHIER uniquement → trash animé → delete au relâchement.
 *  - Undo 5 s avec barre de progression.
 *  - Variants Track / Pad / Drum.
 */

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.soundstage.mixer.model.SoundfontPreset
import com.soundstage.mixer.ui.theme.AppColors
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// ════════════════════════════════════════════════════════════════════════════════════════
// 1. MODELS
// ════════════════════════════════════════════════════════════════════════════════════════

enum class SoundfontVariant { Track, Pad, Drum }

enum class SfCategory(val id: String, val label: String) {
    Favorite("fav", "Favorites"),
    Piano("piano", "Piano"),
    Keys("keys", "Keys"),
    Organ("organ", "Organ"),
    Guitar("guitar", "Guitar"),
    Bass("bass", "Bass"),
    Strings("strings", "Strings"),
    Brass("brass", "Brass"),
    Wind("wind", "Woodwind"),
    Synth("synth", "Synth"),
    Pads("pads", "Pads"),
    Mallets("mallets", "Mallets"),
    Drums("drums", "Drums & Perc"),
    Fx("fx", "FX"),
    Other("other", "Other");

    companion object {
        fun fromId(id: String?): SfCategory? = values().firstOrNull { it.id == id }
    }
}

sealed interface SfRow {
    val key: String
    val name: String

    data class Preset(
        override val key: String,
        override val name: String,
        val presetId: Int,
        val bankNumber: Int,
        val bankLabel: String,
        val fileId: String,
    ) : SfRow

    data class File(
        override val key: String,
        override val name: String,
        val sizeBytes: Long,
        val formattedSize: String,
        val isLoaded: Boolean,
        val presetCount: Int,
    ) : SfRow
}

data class SoundfontWindowState(
    val isOpen: Boolean,
    val variant: SoundfontVariant,
    val loadedFileId: String? = null,
    val loadedFileName: String = "",
    val loadedPresets: List<SoundfontPreset> = emptyList(),
    val loadedBank: Int = 0,
    val loadedPresetId: Int = 0,
    val loadedBanks: List<Int> = emptyList(),
    val files: List<SfRow.File> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val overrides: Map<String, String> = emptyMap(),
    val parsedPresets: Map<String, List<SoundfontPreset>> = emptyMap(),
    val parsingFiles: Set<String> = emptySet(),
    val loadingFileId: String? = null,
    val loadingProgress: Float? = null,
    val importingName: String? = null,
    val importingProgress: Float? = null,
)

interface SfWindowActions {
    fun onClose() {}
    fun onSelectPreset(row: SfRow.Preset) {}
    fun onLoadFile(fileId: String) {}
    fun onCancelLoad() {}
    fun onToggleFavorite(key: String) {}
    fun onMoveToCategory(key: String, categoryId: String) {}
    fun onImportFile() {}
    fun onDeleteFile(fileId: String) {}
    fun onRequestParse(fileId: String) {}
}

object NoOpSfWindowActions : SfWindowActions

// ════════════════════════════════════════════════════════════════════════════════════════
// 2. CLASSIFIER
// ════════════════════════════════════════════════════════════════════════════════════════

private object SfClassifier {
    private val rules: List<Pair<SfCategory, Regex>> = listOf(
        SfCategory.Organ to Regex("organ|b3|hammond|leslie|harmonium", RegexOption.IGNORE_CASE),
        SfCategory.Drums to Regex("drum|kit|perc|snare|kick|hi-?hat|cymbal|conga|bongo|tambour|timpani", RegexOption.IGNORE_CASE),
        SfCategory.Bass to Regex("bass", RegexOption.IGNORE_CASE),
        SfCategory.Keys to Regex("rhodes|wurl|clav|tine|e\\.?piano|electric piano|harpsichord|accordion|acordeon|celesta", RegexOption.IGNORE_CASE),
        SfCategory.Piano to Regex("piano|grand|honky", RegexOption.IGNORE_CASE),
        SfCategory.Guitar to Regex("guitar|nylon|banjo|ukulele|mandolin|sitar", RegexOption.IGNORE_CASE),
        SfCategory.Mallets to Regex("vib|marimba|xylo|glock|bell|chime", RegexOption.IGNORE_CASE),
        SfCategory.Strings to Regex("string|violin|viola|cello|harp|pizzicato|orchestra", RegexOption.IGNORE_CASE),
        SfCategory.Brass to Regex("brass|trumpet|trombone|tuba|horn|cornet|flugel", RegexOption.IGNORE_CASE),
        SfCategory.Wind to Regex("flute|sax|oboe|clarinet|bassoon|piccolo|recorder|whistle", RegexOption.IGNORE_CASE),
        SfCategory.Pads to Regex("pad|choir|voice|aah|ooh|halo|sweep|atmos", RegexOption.IGNORE_CASE),
        SfCategory.Synth to Regex("lead|synth|saw|square|arp|pluck", RegexOption.IGNORE_CASE),
        SfCategory.Fx to Regex("fx|rain|sci-?fi|noise|echo|sound ?track|crystal|goblin", RegexOption.IGNORE_CASE),
    )

    fun classify(name: String): SfCategory =
        rules.firstOrNull { it.second.containsMatchIn(name) }?.first ?: SfCategory.Other
}

// ════════════════════════════════════════════════════════════════════════════════════════
// 3. STYLE
// ════════════════════════════════════════════════════════════════════════════════════════

private object SfColors {
    val PanelTop = AppColors.PanelTop
    val PanelBottom = AppColors.PanelBottom
    val Ink = AppColors.Ink
    val Mute = AppColors.Mute
    val Accent = AppColors.Accent
    val AccentDeep = AppColors.AccentDeep
    val AccentText = AppColors.AccentText
    val Label = Color(0xFF7B859A)
    val Star = Color(0xFFE6BC73)
    val StarIdle = Color(0xFF566075)
    val Backdrop = Color(0x9E060910)
    val BackdropSheet = Color(0xB3060914)
    val Title = Color(0xFFF4F6FB)
    val Rose = Color(0xFFC2587A)
    val RoseDeep = Color(0xFF8A3B56)
}

private fun white(a: Float) = Color.White.copy(alpha = a)

private fun sfText(
    size: TextUnit,
    weight: FontWeight = FontWeight.Normal,
    color: Color = SfColors.Ink
): TextStyle = TextStyle(fontSize = size, fontWeight = weight, color = color, fontFeatureSettings = "tnum")

@Composable
private fun Modifier.sfTap(scaleDown: Float = 0.96f, onTap: () -> Unit): Modifier {
    var pressed by remember { mutableStateOf(false) }
    val s by animateFloatAsState(
        if (pressed) scaleDown else 1f,
        spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "sfTap"
    )
    val tap by rememberUpdatedState(onTap)
    return this
        .graphicsLayer { scaleX = s; scaleY = s }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    tryAwaitRelease()
                    pressed = false
                },
                onTap = { tap() }
            )
        }
}

private fun Modifier.topHairline(alpha: Float = 0.10f): Modifier = this.drawBehind {
    drawLine(
        color = Color.White.copy(alpha = alpha),
        start = Offset(6.dp.toPx(), 1f),
        end = Offset(size.width - 6.dp.toPx(), 1f),
        strokeWidth = 1.dp.toPx(),
        cap = StrokeCap.Round
    )
}

private val PanelShape = RoundedCornerShape(24.dp)
private val RowShape = RoundedCornerShape(12.dp)
private val CatRowShape = RoundedCornerShape(10.dp)

// ════════════════════════════════════════════════════════════════════════════════════════
// 4. ENTRY POINT
// ════════════════════════════════════════════════════════════════════════════════════════

@Composable
fun SoundfontWindow(
    state: SoundfontWindowState,
    actions: SfWindowActions = NoOpSfWindowActions,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(SfTab.All) }
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<SfCategory?>(null) }
    var selectedBank by remember { mutableIntStateOf(state.loadedBank) }

    var contextMenu by remember { mutableStateOf<ContextMenu?>(null) }
    var moveSheet by remember { mutableStateOf<SfRow?>(null) }
    var pendingDelete by remember { mutableStateOf<SfRow.File?>(null) }
    var pendingDeleteKey by remember { mutableStateOf<Long?>(null) }

    val hiddenFileKeys = remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(state.isOpen, state.variant) {
        if (state.isOpen) {
            tab = SfTab.All
            query = ""
            selectedCategory = when (state.variant) {
                SoundfontVariant.Pad -> SfCategory.Pads
                SoundfontVariant.Drum -> SfCategory.Drums
                SoundfontVariant.Track -> null
            }
            selectedBank = state.loadedBank
            hiddenFileKeys.value = emptySet()
        }
    }

    LaunchedEffect(state.loadedBank, state.loadedFileId) {
        if (state.loadedBanks.isNotEmpty() && selectedBank !in state.loadedBanks) {
            selectedBank = state.loadedBanks.first()
        } else if (state.loadedBanks.isNotEmpty()) {
            selectedBank = state.loadedBank
        }
    }

    val searching = query.trim().isNotEmpty()

    val presetsAll: List<SfRow.Preset> = remember(state.loadedFileId, state.loadedPresets) {
        val fileId = state.loadedFileId ?: ""
        state.loadedPresets.map { p ->
            SfRow.Preset(
                key = "$fileId|${p.bankNumber}|${p.id}",
                name = p.name,
                presetId = p.id,
                bankNumber = p.bankNumber,
                bankLabel = "Bank ${p.bankNumber}",
                fileId = fileId,
            )
        }
    }

    val currentRows: List<SfRow> = remember(
        tab, presetsAll, state.files, searching, selectedBank, selectedCategory,
        query, state.overrides, state.favorites, hiddenFileKeys.value
    ) {
        when (tab) {
            SfTab.All -> {
                var list = state.files.filter { it.key !in hiddenFileKeys.value }
                val cat = selectedCategory
                list = if (searching) {
                    list.filter { it.name.contains(query.trim(), ignoreCase = true) }
                } else if (cat == SfCategory.Favorite) {
                    list.filter { state.favorites.contains(it.key) }
                } else if (cat != null) {
                    list.filter { row ->
                        val eff = state.overrides[row.key] ?: SfClassifier.classify(row.name).id
                        eff == cat.id
                    }
                } else list
                list.map { it as SfRow }
            }
            SfTab.Presets -> {
                var list = presetsAll
                if (!searching && state.loadedBanks.size > 1) {
                    list = list.filter { it.bankNumber == selectedBank }
                }
                val cat = selectedCategory
                list = list.filter { row ->
                    if (searching) {
                        row.name.contains(query.trim(), ignoreCase = true)
                    } else if (cat == null) {
                        true
                    } else if (cat == SfCategory.Favorite) {
                        state.favorites.contains(row.key)
                    } else {
                        val eff = state.overrides[row.key] ?: SfClassifier.classify(row.name).id
                        eff == cat.id
                    }
                }
                list as List<SfRow>
            }
        }
    }

    val countsByCategory: Map<SfCategory, Int> = remember(
        tab, presetsAll, state.files, selectedBank, state.overrides, state.favorites, hiddenFileKeys.value
    ) {
        val result = mutableMapOf<SfCategory, Int>()
        SfCategory.values().forEach { result[it] = 0 }
        when (tab) {
            SfTab.All -> {
                state.files.filter { it.key !in hiddenFileKeys.value }.forEach {
                    val c = SfCategory.fromId(state.overrides[it.key]) ?: SfClassifier.classify(it.name)
                    result[c] = (result[c] ?: 0) + 1
                    if (state.favorites.contains(it.key)) {
                        result[SfCategory.Favorite] = (result[SfCategory.Favorite] ?: 0) + 1
                    }
                }
            }
            SfTab.Presets -> {
                val list = if (state.loadedBanks.size > 1) presetsAll.filter { it.bankNumber == selectedBank } else presetsAll
                list.forEach {
                    val c = SfCategory.fromId(state.overrides[it.key]) ?: SfClassifier.classify(it.name)
                    result[c] = (result[c] ?: 0) + 1
                    if (state.favorites.contains(it.key)) {
                        result[SfCategory.Favorite] = (result[SfCategory.Favorite] ?: 0) + 1
                    }
                }
            }
        }
        result
    }

    val allZero = countsByCategory.values.all { it == 0 }
    val visibleCats: List<SfCategory> = if (allZero) {
        SfCategory.values().toList()
    } else {
        SfCategory.values().filter { (countsByCategory[it] ?: 0) > 0 }
    }

    Box(modifier = modifier.fillMaxSize().zIndex(50f)) {
        AnimatedVisibility(
            visible = state.isOpen,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(180)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(SfColors.Backdrop)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { actions.onClose() }
            )
        }

        AnimatedVisibility(
            visible = state.isOpen,
            enter = scaleIn(
                initialScale = 0.55f,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 380f)
            ) + fadeIn(tween(220)),
            exit = scaleOut(targetScale = 0.75f, animationSpec = tween(180)) + fadeOut(tween(160)),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .width(660.dp)
                        .height(372.dp)
                        .shadow(28.dp, PanelShape, ambientColor = Color.Black, spotColor = Color(0xAA000000))
                        .clip(PanelShape)
                        .background(Brush.verticalGradient(listOf(SfColors.PanelTop, SfColors.PanelBottom)))
                        .border(1.dp, white(0.10f), PanelShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { /* absorb */ }
                ) {
                    SfHeader(
                        query = query,
                        onQueryChange = { query = it },
                        tab = tab,
                        onImport = { actions.onImportFile() },
                        onClose = { actions.onClose() },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 14.dp, end = 14.dp, top = 14.dp)
                            .fillMaxWidth()
                            .height(44.dp)
                    )

                    SfLeftColumn(
                        tab = tab,
                        onTabChange = { t ->
                            tab = t
                            selectedCategory = when (state.variant) {
                                SoundfontVariant.Pad -> SfCategory.Pads
                                SoundfontVariant.Drum -> SfCategory.Drums
                                SoundfontVariant.Track -> null
                            }
                            query = ""
                        },
                        categories = visibleCats,
                        counts = countsByCategory,
                        selected = selectedCategory,
                        onSelect = { c -> selectedCategory = if (selectedCategory == c) null else c },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 14.dp, top = 68.dp, bottom = 14.dp)
                            .width(154.dp)
                            .fillMaxHeight()
                    )

                    Column(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 182.dp, end = 14.dp, top = 68.dp, bottom = 14.dp)
                            .fillMaxSize()
                    ) {
                        if (tab == SfTab.Presets && state.loadedBanks.size > 1) {
                            SfBankBar(
                                banks = state.loadedBanks,
                                selected = selectedBank,
                                onSelect = { b -> selectedBank = b },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(30.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                        }

                        if (selectedCategory != null || searching) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(24.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (selectedCategory != null) {
                                    Row(
                                        Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(white(0.08f))
                                            .sfTap(0.95f) { selectedCategory = null }
                                            .padding(start = 10.dp, end = 6.dp, top = 3.dp, bottom = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    ) {
                                        Text(
                                            selectedCategory!!.label,
                                            style = sfText(11.5.sp, FontWeight.Bold, Color(0xFFC7CFDD))
                                        )
                                        SfIcon(SfIconKind.X, Color(0xFFC7CFDD), Modifier.size(11.dp), stroke = 2.6.dp)
                                    }
                                }
                                if (searching) {
                                    Text(
                                        "${currentRows.size} ${if (currentRows.size == 1) "result" else "results"}",
                                        style = sfText(11.5.sp, color = SfColors.Label)
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        Box(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            SfList(
                                rows = currentRows,
                                tab = tab,
                                query = query,
                                loadedFileId = state.loadedFileId,
                                loadedBank = selectedBank,
                                loadedPresetId = state.loadedPresetId,
                                favorites = state.favorites,
                                loadingFileId = state.loadingFileId,
                                loadingProgress = state.loadingProgress,
                                importingName = state.importingName,
                                importingProgress = state.importingProgress,
                                onSelectPreset = { row -> actions.onSelectPreset(row) },
                                onLoadFile = { id -> actions.onLoadFile(id) },
                                onCancelLoad = { actions.onCancelLoad() },
                                onToggleFavorite = { key -> actions.onToggleFavorite(key) },
                                onOpenMenu = { ctx -> contextMenu = ctx },
                                onRequestDeleteFile = { row ->
                                    hiddenFileKeys.value = hiddenFileKeys.value + row.key
                                    pendingDelete = row
                                    pendingDeleteKey = System.nanoTime()
                                },
                            )
                        }
                    }
                }
            }
        }

        contextMenu?.let { ctx ->
            SfContextMenu(
                ctx = ctx,
                isFavorite = state.favorites.contains(ctx.row.key),
                onDismiss = { contextMenu = null },
                onToggleFavorite = {
                    actions.onToggleFavorite(ctx.row.key)
                    contextMenu = null
                },
                onMoveTo = {
                    moveSheet = ctx.row
                    contextMenu = null
                },
                onDeleteFile = if (ctx.row is SfRow.File) {
                    {
                        val f = ctx.row as SfRow.File
                        hiddenFileKeys.value = hiddenFileKeys.value + f.key
                        pendingDelete = f
                        pendingDeleteKey = System.nanoTime()
                        contextMenu = null
                    }
                } else null
            )
        }

        moveSheet?.let { row ->
            SfMoveSheet(
                row = row,
                currentCategory = SfCategory.fromId(state.overrides[row.key]) ?: SfClassifier.classify(row.name),
                onDismiss = { moveSheet = null },
                onPick = { cat ->
                    actions.onMoveToCategory(row.key, cat.id)
                    moveSheet = null
                }
            )
        }

        pendingDelete?.let { file ->
            val k = pendingDeleteKey ?: System.nanoTime()
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 26.dp)
                    .zIndex(90f)
            ) {
                SfUndoSnackbar(
                    keyId = k,
                    text = "\"${file.name}\" deleted",
                    onUndo = {
                        hiddenFileKeys.value = hiddenFileKeys.value - file.key
                        pendingDelete = null
                        pendingDeleteKey = null
                    },
                    onExpire = {
                        actions.onDeleteFile(file.key)
                        pendingDelete = null
                        pendingDeleteKey = null
                    }
                )
            }
        }
    }
}

private enum class SfTab { Presets, All }
private data class ContextMenu(val row: SfRow, val x: Float, val y: Float)

// ════════════════════════════════════════════════════════════════════════════════════════
// 5. HEADER
// ════════════════════════════════════════════════════════════════════════════════════════

@Composable
private fun SfHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    tab: SfTab,
    onImport: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier
) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val shape = RoundedCornerShape(14.dp)
        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(shape)
                .background(Color(0xB30F141D))
                .border(1.dp, white(0.10f), shape)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SfIcon(SfIconKind.Search, SfColors.Mute, Modifier.size(17.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        if (tab == SfTab.All) "Search soundfonts" else "Search presets",
                        style = sfText(14.5.sp, color = SfColors.Mute),
                        maxLines = 1
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = sfText(14.5.sp, color = SfColors.Ink),
                    cursorBrush = SolidColor(SfColors.Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {}),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                Box(
                    Modifier
                        .size(20.dp)
                        .sfTap(0.85f) { onQueryChange("") },
                    contentAlignment = Alignment.Center
                ) {
                    SfIcon(SfIconKind.X, SfColors.Mute, Modifier.size(13.dp))
                }
            }
        }

        Box(
            Modifier
                .size(42.dp)
                .sfTap(0.94f, onImport)
                .shadow(4.dp, CircleShape, ambientColor = Color.Black, spotColor = Color(0x99000000))
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFF9DBDEB), SfColors.AccentDeep)))
                .border(1.dp, white(0.20f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            SfIcon(SfIconKind.Plus, SfColors.AccentText, Modifier.size(19.dp), stroke = 2.6.dp)
        }

        Box(
            Modifier
                .size(42.dp)
                .sfTap(0.94f, onClose)
                .clip(CircleShape)
                .background(white(0.07f))
                .border(1.dp, white(0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            SfIcon(SfIconKind.X, SfColors.Ink, Modifier.size(17.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════════════
// 6. LEFT COLUMN — Tabs + Categories
// ════════════════════════════════════════════════════════════════════════════════════════

@Composable
private fun SfLeftColumn(
    tab: SfTab,
    onTabChange: (SfTab) -> Unit,
    categories: List<SfCategory>,
    counts: Map<SfCategory, Int>,
    selected: SfCategory?,
    onSelect: (SfCategory) -> Unit,
    modifier: Modifier
) {
    val listState = rememberLazyListState()
    Column(modifier) {
        SfTabStrip(
            tab = tab,
            onChange = onTabChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
        )
        Spacer(Modifier.height(10.dp))

        Text(
            "Category",
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.3.sp,
                color = SfColors.Label
            ),
            modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            items(categories.size, key = { categories[it].id }) { i ->
                val c = categories[i]
                val on = selected == c
                val count = counts[c] ?: 0
                val isFav = c == SfCategory.Favorite

                val rowBg by animateColorAsState(
                    if (on) white(0.11f) else Color.Transparent,
                    tween(220),
                    label = "catBg"
                )
                val fgColor by animateColorAsState(
                    when {
                        on -> SfColors.Ink
                        isFav -> SfColors.Star
                        else -> Color(0xFF9AA5BA)
                    },
                    tween(200),
                    label = "catFg"
                )

                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .clip(CatRowShape)
                        .background(rowBg)
                        .sfTap(0.97f) { onSelect(c) }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        c.label,
                        style = sfText(13.sp, FontWeight.Bold, fgColor),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "$count",
                        style = sfText(11.sp, FontWeight.Bold, if (on) Color(0xFFC7CFDD) else Color(0xFF6F7A90)),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun SfTabStrip(
    tab: SfTab,
    onChange: (SfTab) -> Unit,
    modifier: Modifier
) {
    val idx = tab.ordinal
    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xCC0F141D))
            .border(1.dp, white(0.08f), RoundedCornerShape(12.dp))
            .padding(3.dp)
    ) {
        val segW = maxWidth / 2
        val thumbOffset by androidx.compose.animation.core.animateDpAsState(
            segW * idx,
            spring(dampingRatio = 0.72f, stiffness = 500f),
            label = "sfTabThumb"
        )
        Box(
            Modifier
                .offset(x = thumbOffset)
                .width(segW)
                .fillMaxHeight()
                .clip(RoundedCornerShape(9.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF38445E), Color(0xFF2B3650))))
        )
        Row(Modifier.fillMaxSize()) {
            SfTab.entries.forEach { t ->
                val on = t == tab
                val c by animateColorAsState(
                    if (on) Color(0xFFF1F4FA) else SfColors.Mute,
                    tween(200),
                    label = "sfTabColor"
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .sfTap(1f) { onChange(t) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (t == SfTab.Presets) "Presets" else "All",
                        style = sfText(12.sp, FontWeight.Bold, c),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════════════
// 7. BANK BAR
// ════════════════════════════════════════════════════════════════════════════════════════

@Composable
private fun SfBankBar(
    banks: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier
) {
    LazyRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(banks.size, key = { banks[it] }) { i ->
            val b = banks[i]
            val on = b == selected
            val label = when (b) {
                0 -> "Bank 0 (Main)"
                128 -> "Bank 128 (Drums)"
                else -> "Bank $b"
            }
            val border by animateColorAsState(
                if (on) white(0.26f) else white(0.09f),
                tween(200),
                label = "bankBorder"
            )
            val bg by animateColorAsState(
                if (on) white(0.13f) else Color.Transparent,
                tween(200),
                label = "bankBg"
            )
            Box(
                Modifier
                    .height(30.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(bg)
                    .border(1.dp, border, RoundedCornerShape(15.dp))
                    .sfTap(0.95f) { onSelect(b) }
                    .padding(horizontal = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = sfText(12.sp, FontWeight.Bold, if (on) Color(0xFFF1F4FA) else SfColors.Mute),
                    maxLines = 1
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════════════
// 8. LIST
// ════════════════════════════════════════════════════════════════════════════════════════

@Composable
private fun SfList(
    rows: List<SfRow>,
    tab: SfTab,
    query: String,
    loadedFileId: String?,
    loadedBank: Int,
    loadedPresetId: Int,
    favorites: Set<String>,
    loadingFileId: String?,
    loadingProgress: Float?,
    importingName: String?,
    importingProgress: Float?,
    onSelectPreset: (SfRow.Preset) -> Unit,
    onLoadFile: (String) -> Unit,
    onCancelLoad: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onOpenMenu: (ContextMenu) -> Unit,
    onRequestDeleteFile: (SfRow.File) -> Unit,
) {
    val listState = rememberLazyListState()

    var letter by remember { mutableStateOf<String?>(null) }
    var letterAlpha by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, tab) {
        if (tab != SfTab.All || query.isNotBlank()) {
            letter = null
            letterAlpha = 0f
            return@LaunchedEffect
        }
        val idx = listState.firstVisibleItemIndex
        val first = rows.getOrNull(idx)
        if (first != null && first.name.isNotEmpty()) {
            val c = first.name.first()
            letter = if (c.isLetter()) c.uppercase() else "#"
            letterAlpha = 1f
            kotlinx.coroutines.delay(900)
            letterAlpha = 0f
            kotlinx.coroutines.delay(220)
            letter = null
        }
    }

    if (rows.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                when {
                    importingName != null -> "Importing $importingName…"
                    tab == SfTab.All -> "No soundfonts found."
                    query.isNotBlank() -> "Nothing matches \"$query\"."
                    else -> "Nothing here yet."
                },
                style = sfText(13.5.sp, color = SfColors.Label)
            )
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(top = 2.dp, bottom = 12.dp)
        ) {
            if (importingName != null && tab == SfTab.All) {
                item(key = "importing") {
                    SfImportRow(name = importingName, progress = importingProgress ?: 0f)
                }
            }

            items(rows.size, key = { rows[it].key }) { i ->
                val row = rows[i]
                when (row) {
                    is SfRow.Preset -> {
                        val active = row.presetId == loadedPresetId &&
                            row.bankNumber == loadedBank &&
                            row.fileId == loadedFileId
                        SfPresetRow(
                            row = row,
                            index = i,
                            active = active,
                            query = query,
                            isFavorite = favorites.contains(row.key),
                            onTap = { onSelectPreset(row) },
                            onToggleFavorite = { onToggleFavorite(row.key) },
                            onOpenMenu = { onOpenMenu(ContextMenu(row, 0f, 0f)) },
                        )
                    }
                    is SfRow.File -> {
                        val isLoadingThis = row.key == loadingFileId
                        SfFileRow(
                            row = row,
                            index = i,
                            query = query,
                            isFavorite = favorites.contains(row.key),
                            isLoading = isLoadingThis,
                            loadingProgress = if (isLoadingThis) (loadingProgress ?: 0f) else 0f,
                            onTap = { if (!isLoadingThis) onLoadFile(row.key) },
                            onCancelLoad = onCancelLoad,
                            onToggleFavorite = { onToggleFavorite(row.key) },
                            onOpenMenu = { onOpenMenu(ContextMenu(row, 0f, 0f)) },
                            onDeleteCommit = { onRequestDeleteFile(row) },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = letter != null && letterAlpha > 0.01f,
            enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.6f, animationSpec = tween(180)),
            exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.8f, animationSpec = tween(180)),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 6.dp)
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .graphicsLayer { alpha = letterAlpha }
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xF22B3650))
                    .border(1.dp, white(0.16f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    letter ?: "",
                    style = sfText(18.sp, FontWeight.ExtraBold, SfColors.Title)
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════════════
// 9. PRESET ROW (no swipe)
// ════════════════════════════════════════════════════════════════════════════════════════

@Composable
private fun SfPresetRow(
    row: SfRow.Preset,
    index: Int,
    active: Boolean,
    query: String,
    isFavorite: Boolean,
    onTap: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenMenu: (Offset) -> Unit
) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(row.key) {
        kotlinx.coroutines.delay((index.coerceAtMost(8) * 16).toLong())
        enter.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
    }

    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .graphicsLayer {
                alpha = enter.value
                translationY = (1f - enter.value) * 6.dp.toPx()
            }
            .sfTap(0.985f, onTap)
            .shadow(3.dp, RowShape, ambientColor = Color.Black, spotColor = Color(0x99000000))
            .clip(RowShape)
            .background(Brush.linearGradient(listOf(Color(0xFF232C3E), Color(0xFF1A2230))))
            .border(1.dp, if (active) white(0.17f) else white(0.06f), RowShape)
            .topHairline(if (active) 0.14f else 0.07f)
            .padding(start = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            row.presetId.toString(),
            style = sfText(11.sp, FontWeight.Bold, Color(0xFF6F7A90)),
            maxLines = 1,
            modifier = Modifier.width(24.dp)
        )

        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HighlightedText(
                text = row.name,
                query = query,
                style = sfText(14.5.sp, FontWeight.Bold, SfColors.Ink),
                modifier = Modifier.weight(1f, fill = false)
            )
            if (query.isNotBlank()) {
                Text(
                    "${row.bankLabel} · #${row.presetId}",
                    style = sfText(10.5.sp, color = Color(0xFF6F7A90)),
                    maxLines = 1
                )
            }
        }

        if (active) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(white(0.10f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SfIcon(SfIconKind.Check, SfColors.Accent, Modifier.size(11.dp), stroke = 3.dp)
                Text(
                    "ACTIVE",
                    style = TextStyle(
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.1.sp,
                        color = Color(0xFFC7CFDD)
                    )
                )
            }
        }

        Box(
            Modifier
                .size(28.dp)
                .sfTap(0.90f, onToggleFavorite),
            contentAlignment = Alignment.Center
        ) {
            SfIcon(
                SfIconKind.Star,
                if (isFavorite) SfColors.Star else SfColors.StarIdle,
                Modifier.size(15.dp),
                stroke = 1.8.dp,
                fill = isFavorite
            )
        }

        Box(
            Modifier
                .size(26.dp)
                .sfTap(0.90f) { onOpenMenu(Offset.Zero) },
            contentAlignment = Alignment.Center
        ) {
            SfIcon(SfIconKind.Dots, Color(0xFF6F7A90), Modifier.size(15.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════════════
// 10. FILE ROW (swipe → trash, delete on release after threshold)
// ════════════════════════════════════════════════════════════════════════════════════════

@Composable
private fun SfFileRow(
    row: SfRow.File,
    index: Int,
    query: String,
    isFavorite: Boolean,
    isLoading: Boolean,
    loadingProgress: Float,
    onTap: () -> Unit,
    onCancelLoad: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenMenu: (Offset) -> Unit,
    onDeleteCommit: () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val enter = remember { Animatable(0f) }
    val offset = remember { Animatable(0f) }
    var armed by remember { mutableStateOf(false) }
    var widthPx by remember { mutableIntStateOf(0) }
    val gapPx = with(density) { 8.dp.toPx() }
    val revealMax = with(density) { 76.dp.toPx() }

    LaunchedEffect(row.key) {
        kotlinx.coroutines.delay((index.coerceAtMost(8) * 16).toLong())
        enter.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
    }

    val pillWidthPx = (-offset.value - gapPx).coerceAtLeast(0f)
    val revealFraction = (pillWidthPx / revealMax).coerceIn(0f, 1f)

    val armedScale by animateFloatAsState(
        if (armed) 1.15f else 1f,
        spring(dampingRatio = 0.45f, stiffness = 520f),
        label = "trashScale"
    )
    val armedRotation by animateFloatAsState(
        if (armed) -14f else 0f,
        spring(dampingRatio = 0.5f, stiffness = 500f),
        label = "trashRot"
    )

    Box(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .graphicsLayer {
                alpha = enter.value
                translationY = (1f - enter.value) * 6.dp.toPx()
            }
            .onSizeChanged { widthPx = it.width }
    ) {
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(with(density) { pillWidthPx.toDp() })
                .clip(RowShape)
                .background(Brush.verticalGradient(listOf(SfColors.Rose, SfColors.RoseDeep))),
            contentAlignment = Alignment.Center
        ) {
            if (pillWidthPx > with(density) { 28.dp.toPx() }) {
                Box(
                    Modifier.graphicsLayer {
                        val s = revealFraction * armedScale
                        scaleX = s
                        scaleY = s
                        rotationZ = armedRotation
                        alpha = revealFraction
                    }
                ) {
                    SfIcon(
                        SfIconKind.Trash,
                        Color.White,
                        Modifier.size(20.dp),
                        stroke = 2.dp
                    )
                }
            }
        }

        Row(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .fillMaxSize()
                .pointerInput(Unit) {
                    var total = 0f
                    val resist = 24.dp.toPx()
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            scope.launch {
                                if (armed) {
                                    offset.animateTo(-widthPx.toFloat(), tween(180))
                                    armed = false
                                    onDeleteCommit()
                                } else {
                                    offset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 400f))
                                    armed = false
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                offset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 400f))
                            }
                            armed = false
                        },
                        onHorizontalDrag = { change, dx ->
                            change.consume()
                            total += dx
                            val d = min(0f, total)
                            val eff = if (d > -resist) d else -resist + (d + resist) * 0.75f
                            scope.launch { offset.snapTo(eff) }
                            val threshold = min(widthPx * 0.4f, 200.dp.toPx())
                            val a = eff <= -threshold
                            if (a != armed) armed = a
                        }
                    )
                }
                .sfTap(0.985f, onTap)
                .shadow(3.dp, RowShape, ambientColor = Color.Black, spotColor = Color(0x99000000))
                .clip(RowShape)
                .background(Brush.linearGradient(listOf(Color(0xFF232C3E), Color(0xFF1A2230))))
                .border(1.dp, if (row.isLoaded) white(0.17f) else white(0.06f), RowShape)
                .topHairline(if (row.isLoaded) 0.14f else 0.07f)
                .padding(start = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                SfIcon(SfIconKind.File, Color(0xFF7B859A), Modifier.size(17.dp))
            }

            HighlightedText(
                text = row.name,
                query = query,
                style = sfText(14.5.sp, FontWeight.Bold, SfColors.Ink),
                modifier = Modifier.weight(1f)
            )

            Text(
                row.formattedSize,
                style = sfText(12.sp, FontWeight.SemiBold, Color(0xFF7B859A)),
                maxLines = 1,
                modifier = Modifier.widthIn(min = 58.dp)
            )

            if (isLoading) {
                SfProgressRing(progress = loadingProgress, onCancel = onCancelLoad)
            } else if (row.isLoaded) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(white(0.10f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SfIcon(SfIconKind.Check, SfColors.Accent, Modifier.size(11.dp), stroke = 3.dp)
                    Text(
                        "LOADED",
                        style = TextStyle(
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.1.sp,
                            color = Color(0xFFC7CFDD)
                        )
                    )
                }
            }

            Box(
                Modifier
                    .size(28.dp)
                    .sfTap(0.90f, onToggleFavorite),
                contentAlignment = Alignment.Center
            ) {
                SfIcon(
                    SfIconKind.Star,
                    if (isFavorite) SfColors.Star else SfColors.StarIdle,
                    Modifier.size(15.dp),
                    stroke = 1.8.dp,
                    fill = isFavorite
                )
            }

            Box(
                Modifier
                    .size(26.dp)
                    .sfTap(0.90f) { onOpenMenu(Offset.Zero) },
                contentAlignment = Alignment.Center
            ) {
                SfIcon(SfIconKind.Dots, Color(0xFF6F7A90), Modifier.size(15.dp))
            }
        }
    }
}

@Composable
private fun SfImportRow(name: String, progress: Float) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(name) { enter.animateTo(1f, tween(240)) }

    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .graphicsLayer {
                alpha = enter.value
                translationY = (1f - enter.value) * 6.dp.toPx()
            }
            .clip(RowShape)
            .background(white(0.07f))
            .border(1.dp, SfColors.Accent.copy(alpha = 0.4f), RowShape)
            .padding(start = 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SfIcon(SfIconKind.File, SfColors.Accent, Modifier.size(17.dp))
        Text(
            name,
            style = sfText(14.sp, FontWeight.Bold, SfColors.Ink),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "${(progress * 100).roundToInt()}%",
            style = sfText(11.5.sp, FontWeight.Bold, SfColors.Accent)
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════════════════
// 11. PROGRESS RING
// ════════════════════════════════════════════════════════════════════════════════════════

@Composable
private fun SfProgressRing(progress: Float, onCancel: () -> Unit) {
    val p = progress.coerceIn(0f, 1f)
    Box(
        Modifier
            .size(24.dp)
            .sfTap(0.85f, onCancel),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 2.5.dp.toPx()
            val r = (size.minDimension - stroke) / 2f
            val cx = size.width / 2f
            val cy = size.height / 2f
            drawCircle(
                color = Color.White.copy(alpha = 0.12f),
                radius = r,
                center = Offset(cx, cy),
                style = Stroke(width = stroke)
            )
            drawArc(
                color = SfColors.Accent,
                startAngle = -90f,
                sweepAngle = 360f * p,
                useCenter = false,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        SfIcon(SfIconKind.X, Color(0xFFC7CFDD), Modifier.size(9.dp), stroke = 3.dp)
    }
}

// ════════════════════════════════════════════════════════════════════════════════════════
// 12. CONTEXT MENU
// ════════════════════════════════════════════════════════════════════════════════════════

@Composable
private fun SfContextMenu(
    ctx: ContextMenu,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMoveTo: () -> Unit,
    onDeleteFile: (() -> Unit)?
) {
    Box(
        Modifier
            .fillMaxSize()
            .zIndex(70f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onDismiss() }
    ) {
        Column(
            Modifier
                .align(Alignment.Center)
                .offset(x = 200.dp, y = -100.dp)
                .width(190.dp)
                .shadow(14.dp, RoundedCornerShape(14.dp), ambientColor = Color.Black, spotColor = Color(0xAA000000))
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF2A3447), Color(0xFF1E2736))))
                .border(1.dp, white(0.12f), RoundedCornerShape(14.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { /* absorb */ }
                .padding(5.dp)
        ) {
            SfMenuItem(
                label = if (isFavorite) "Remove favorite" else "Add to favorites",
                onClick = onToggleFavorite
            )
            SfMenuItem(
                label = "Move to…",
                onClick = onMoveTo
            )
            if (onDeleteFile != null) {
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(white(0.07f))
                )
                SfMenuItem(
                    label = "Delete file",
                    color = Color(0xFFE8708F),
                    onClick = onDeleteFile
                )
            }
        }
    }
}

@Composable
private fun SfMenuItem(label: String, color: Color = Color(0xFFDCE2EE), onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .sfTap(0.97f, onClick)
            .clip(RoundedCornerShape(9.dp))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(label, style = sfText(13.sp, FontWeight.Bold, color))
    }
}

// ════════════════════════════════════════════════════════════════════════════════════════
// 13. MOVE SHEET
// ════════════════════════════════════════════════════════════════════════════════════════

@Composable
private fun SfMoveSheet(
    row: SfRow,
    currentCategory: SfCategory,
    onDismiss: () -> Unit,
    onPick: (SfCategory) -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .zIndex(80f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(Modifier.fillMaxSize().background(SfColors.BackdropSheet))

        Column(
            Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF2A3447), Color(0xFF1E2736))))
                .border(1.dp, white(0.12f), RoundedCornerShape(20.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { /* absorb */ }
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Move to…", style = sfText(15.sp, FontWeight.ExtraBold, SfColors.Title))
                    Text(
                        "${row.name} · now in ${currentCategory.label}",
                        style = sfText(11.5.sp, color = SfColors.Label),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    Modifier
                        .size(28.dp)
                        .sfTap(0.9f, onDismiss)
                        .clip(CircleShape)
                        .background(white(0.06f)),
                    contentAlignment = Alignment.Center
                ) {
                    SfIcon(SfIconKind.X, Color(0xFF9AA5BA), Modifier.size(14.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                SfCategory.values().forEach { c ->
                    if (c == SfCategory.Favorite) return@forEach
                    val on = c == currentCategory
                    Box(
                        Modifier
                            .height(32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (on) SfColors.Accent.copy(alpha = 0.14f) else white(0.04f))
                            .border(
                                1.dp,
                                if (on) SfColors.Accent.copy(alpha = 0.6f) else white(0.10f),
                                RoundedCornerShape(16.dp)
                            )
                            .sfTap(0.95f) { onPick(c) }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            c.label,
                            style = sfText(
                                12.5.sp,
                                FontWeight.Bold,
                                if (on) SfColors.Accent else Color(0xFFC7CFDD)
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════════════
// 14. UNDO SNACKBAR (5s with progress bar)
// ════════════════════════════════════════════════════════════════════════════════════════

@Composable
private fun SfUndoSnackbar(
    keyId: Long,
    text: String,
    onUndo: () -> Unit,
    onExpire: () -> Unit,
) {
    val progress = remember(keyId) { Animatable(1f) }
    LaunchedEffect(keyId) {
        progress.animateTo(0f, tween(5000, easing = LinearEasing))
        onExpire()
    }
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .shadow(12.dp, shape, ambientColor = Color.Black, spotColor = Color(0xAA000000))
            .clip(shape)
            .background(Color(0xF2222C40))
            .border(1.dp, white(0.10f), shape),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text, style = sfText(13.5.sp, FontWeight.SemiBold, SfColors.Ink))
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .sfTap(0.9f, onUndo)
                    .padding(4.dp),
            ) {
                Text(
                    "UNDO",
                    style = TextStyle(
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = SfColors.Accent,
                    )
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .drawBehind {
                    val p = progress.value
                    drawRect(
                        color = SfColors.Accent,
                        size = Size(size.width * p, size.height),
                    )
                }
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════════════════
// 15. HIGHLIGHTED TEXT
// ════════════════════════════════════════════════════════════════════════════════════════

@Composable
private fun HighlightedText(
    text: String,
    query: String,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    val q = query.trim()
    if (q.isEmpty()) {
        Text(text, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
        return
    }
    val idx = text.indexOf(q, ignoreCase = true)
    if (idx < 0) {
        Text(text, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
        return
    }
    val annotated = buildAnnotatedString {
        append(text.substring(0, idx))
        withStyle(SpanStyle(color = SfColors.Accent, background = SfColors.Accent.copy(alpha = 0.14f))) {
            append(text.substring(idx, idx + q.length))
        }
        append(text.substring(idx + q.length))
    }
    Text(
        annotated,
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

// ════════════════════════════════════════════════════════════════════════════════════════
// 16. ICONS
// ════════════════════════════════════════════════════════════════════════════════════════

private enum class SfIconKind { Search, Plus, X, Check, Star, Dots, File, Trash }

@Composable
private fun SfIcon(
    kind: SfIconKind,
    color: Color,
    modifier: Modifier,
    stroke: androidx.compose.ui.unit.Dp = 2.dp,
    fill: Boolean = false
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = stroke.toPx()
        val st = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, Offset(w * x1, h * y1), Offset(w * x2, h * y2), sw, StrokeCap.Round)

        when (kind) {
            SfIconKind.Search -> {
                drawCircle(color, w * 0.30f, Offset(w * 0.45f, h * 0.45f), style = st)
                line(0.68f, 0.68f, 0.92f, 0.92f)
            }
            SfIconKind.Plus -> {
                line(0.5f, 0.1f, 0.5f, 0.9f)
                line(0.1f, 0.5f, 0.9f, 0.5f)
            }
            SfIconKind.X -> {
                line(0.15f, 0.15f, 0.85f, 0.85f)
                line(0.85f, 0.15f, 0.15f, 0.85f)
            }
            SfIconKind.Check -> {
                line(0.2f, 0.52f, 0.42f, 0.74f)
                line(0.42f, 0.74f, 0.82f, 0.28f)
            }
            SfIconKind.Dots -> {
                val r = w * 0.10f
                drawCircle(color, r, Offset(w / 2f, h * 0.20f))
                drawCircle(color, r, Offset(w / 2f, h * 0.50f))
                drawCircle(color, r, Offset(w / 2f, h * 0.80f))
            }
            SfIconKind.Star -> {
                val path = Path()
                val cx = w / 2f
                val cy = h / 2f
                val outer = w * 0.45f
                val inner = w * 0.20f
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) outer else inner
                    val a = -Math.PI / 2.0 + i * Math.PI / 5.0
                    val x = cx + (r * kotlin.math.cos(a)).toFloat()
                    val y = cy + (r * kotlin.math.sin(a)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                if (fill) drawPath(path, color) else drawPath(path, color, style = st)
            }
            SfIconKind.File -> {
                line(0.25f, 0.13f, 0.62f, 0.13f)
                line(0.62f, 0.13f, 0.78f, 0.30f)
                line(0.78f, 0.30f, 0.78f, 0.87f)
                line(0.78f, 0.87f, 0.25f, 0.87f)
                line(0.25f, 0.87f, 0.25f, 0.13f)
                line(0.62f, 0.13f, 0.62f, 0.30f)
                line(0.62f, 0.30f, 0.78f, 0.30f)
            }
            SfIconKind.Trash -> {
                line(0.20f, 0.25f, 0.80f, 0.25f)
                line(0.40f, 0.25f, 0.40f, 0.13f)
                line(0.40f, 0.13f, 0.60f, 0.13f)
                line(0.60f, 0.13f, 0.60f, 0.25f)
                val bodyPath = Path().apply {
                    moveTo(w * 0.28f, h * 0.30f)
                    lineTo(w * 0.32f, h * 0.87f)
                    lineTo(w * 0.68f, h * 0.87f)
                    lineTo(w * 0.72f, h * 0.30f)
                }
                drawPath(bodyPath, color, style = st)
                line(0.42f, 0.42f, 0.42f, 0.74f)
                line(0.58f, 0.42f, 0.58f, 0.74f)
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════════════
// 17. PREVIEW
// ════════════════════════════════════════════════════════════════════════════════════════

@Preview(widthDp = 891, heightDp = 411, showBackground = true, backgroundColor = 0xFF141B27)
@Composable
private fun PreviewSoundfontWindow() {
    val fakePresets = listOf(
        SoundfontPreset(0, "Grand Piano", 0),
        SoundfontPreset(4, "Electric Piano", 0),
        SoundfontPreset(6, "Harpsichord", 0),
        SoundfontPreset(16, "Hammond Organ", 0),
        SoundfontPreset(19, "Reed Organ", 0),
        SoundfontPreset(24, "Nylon Guitar", 0),
        SoundfontPreset(32, "Finger Bass", 0),
        SoundfontPreset(40, "Violin", 0),
        SoundfontPreset(48, "String Ensemble", 0),
        SoundfontPreset(52, "Choir Aahs", 0),
        SoundfontPreset(56, "Trumpet", 0),
        SoundfontPreset(73, "Flute", 0),
        SoundfontPreset(80, "Square Lead", 0),
        SoundfontPreset(88, "New Age Pad", 0),
    )

    val fakeFiles = listOf(
        SfRow.File("gu", "GeneralUser GS.sf2", 30_000_000L, "30,6 MB", false, 14),
        SfRow.File("b3", "B3 Nord 1 Leslie.sf2", 12_800_000L, "12,8 MB", false, 3),
        SfRow.File("rh", "Rhodes Mark I.sf2", 14_300_000L, "14,3 MB", true, 3),
        SfRow.File("pi", "Grand Piano Steinway.sf2", 210_400_000L, "210,4 MB", false, 2),
        SfRow.File("fs", "Flute & Sax.sf2", 9_100_000L, "9,1 MB", false, 3),
        SfRow.File("ze", "Zebra Unknown.sf2", 3_200_000L, "3,2 MB", false, 2),
        SfRow.File("sw", "Sine Waves Collection.sf2", 1_100_000L, "1,1 MB", false, 2),
    )

    var state by remember {
        mutableStateOf(
            SoundfontWindowState(
                isOpen = true,
                variant = SoundfontVariant.Track,
                loadedFileId = "rh",
                loadedFileName = "Rhodes Mark I.sf2",
                loadedPresets = fakePresets,
                loadedBank = 0,
                loadedPresetId = 4,
                loadedBanks = listOf(0),
                files = fakeFiles,
                favorites = setOf("rh|0|4", "rh|0|16"),
                overrides = emptyMap(),
                parsedPresets = emptyMap(),
                parsingFiles = emptySet(),
            )
        )
    }

    val actions = remember {
        object : SfWindowActions {
            override fun onClose() { state = state.copy(isOpen = false) }
            override fun onSelectPreset(row: SfRow.Preset) {
                state = state.copy(loadedPresetId = row.presetId, loadedBank = row.bankNumber)
            }
            override fun onLoadFile(fileId: String) {}
            override fun onToggleFavorite(key: String) {
                state = state.copy(
                    favorites = if (state.favorites.contains(key)) state.favorites - key else state.favorites + key
                )
            }
            override fun onMoveToCategory(key: String, categoryId: String) {
                state = state.copy(overrides = state.overrides + (key to categoryId))
            }
            override fun onImportFile() {}
            override fun onDeleteFile(fileId: String) {
                state = state.copy(files = state.files.filterNot { it.key == fileId })
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF141B27), Color(0xFF0F141D))))
    ) {
        SoundfontWindow(state = state, actions = actions)
    }
}

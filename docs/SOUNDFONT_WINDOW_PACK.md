# Fenêtre Soundfonts — paquet d'implémentation complet

Document autonome : **tout le code** de la fenêtre soundfonts et de ce qui la
relie au moteur, sans troncature. Objectif annoncé : **migrer les instruments
Organ et Synth dedans**.

- **Dépôt :** `amiel767/NewKeys-` (public)
- **Fichiers intégraux :** `SoundfontWindow.kt` (80 Ko), `SF2Parser.kt`,
  `SoundfontsPreview.jsx` (la référence visuelle), `FxSfWarning.kt`
- **Extraits bornés :** les fonctions soundfont du ViewModel (il fait 320 Ko, seul
  le sous-ensemble pertinent est reproduit — chaque extrait donne ses lignes)

---

## 0. Comment lire ce document

| Étape | Section | Pourquoi |
|---|---|---|
| 1 | §2 `SoundfontsPreview.jsx` | **La source de vérité visuelle.** |
| 2 | §1 `SoundfontWindow.kt` | Tout l'écran : modèles, classifieur, styles, rendu, catégories |
| 3 | §3 le câblage `MixerScreen` | Comment l'état et les actions sont fournis |
| 4 | §4 les fonctions du ViewModel | Chargement, presets, favoris, catégories, import |
| 5 | §5 le moteur | `SF2Parser`, pont natif, persistance |

### Vocabulaire, à ne pas confondre

| Terme | Sens réel dans ce code |
|---|---|
| **Variant** | `SoundfontVariant.Track` / `.Pad` / `.Drum` — la *forme* de la fenêtre selon le slot |
| **Slot** | 0–7 = pistes du mixer, **8 = Drum**, **9 = Pad** |
| **Catégorie** | Onglet de gauche : `SfCategory` (Favorites, Piano, Organ, Guitar…) |
| **Preset** | Un programme d'un `.sf2` (id + nom + banque) |
| **Fichier** | Un `.sf2` sur le disque, avec ses presets parsés à la demande |

### Pièges vérifiés

1. **`tracks` n'a que 8 entrées (0..7).** `tracks.getOrNull(8)` et `getOrNull(9)`
   renvoient `null`. Le nom fiable d'un soundfont pour **tous** les slots est
   `audioSlots[slot].soundFontPath` — c'est ce que fait `DrumPadScreen`, et ce que
   fait le câblage en §3.
2. **La catégorie Organ existe déjà** (`SfCategory.Organ`, regex
   `organ|b3|hammond|leslie|harmonium`). Si la migration d'Organ doit ajouter une
   sous-catégorie ou un filtre, c'est **là** qu'il faut toucher, pas dans le
   classifieur général.
3. **Le classifieur est ordonné** : la **première** règle qui matche gagne. `Organ`
   est en tête, avant `Keys` et `Piano`. Insérer une règle au mauvais endroit
   reclassera des presets existants.
4. **`sfParsedPresets` / `sfParsingFiles` sont un cache runtime**, jamais persisté
   (ils vivent dans `MixerUiState`). Seuls `sfFavorites` et
   `sfCategoryOverrides` sont persistés (`SharedPreferences`).
5. **La sélection d'un preset ne recharge pas le fichier** : `selectSf2Preset`
   envoie un program change au moteur. Le rechargement, c'est
   `loadSoundFontForSlot`.
6. **`SoundfontWindow` est pilotée par `state`/`actions`** : aucune logique métier
   dedans. Migrer Organ/Synth signifie alimenter `SoundfontWindowState` et
   implémenter `SfWindowActions` — pas réécrire l'écran.

---

## 1. L'écran — `SoundfontWindow.kt` (fichier intégral)

Structure interne du fichier, dans l'ordre :

| Bloc | Contenu |
|---|---|
| 1. Models | `SoundfontVariant`, `SfCategory`, `SfRow` (sealed : `Preset` \| `File`), `SoundfontWindowState`, `SfWindowActions` |
| 2. Classifier | `SfClassifier` — règles regex → catégorie |
| 3. Style | `SfColors`, `sfText()`, `Modifier.sfTap`, `Modifier.topHairline`, formes |
| 4. Entry point | `SoundfontWindow(state, actions)` |
| — | Sous-composables : lignes, en-tête, catégories, barre d'action, import |
| — | `PreviewSoundfontWindow()` — l'aperçu `@Preview` |

### `SoundfontWindow.kt`

- **Chemin :** `app/src/main/java/com/soundstage/mixer/ui/components/SoundfontWindow.kt`
- **Taille :** 80549 octets · **lignes :** 1932 · **sha256 (12) :** `1591b1d0c5ba`
- **Tout l'écran.** Point d'entrée : .

````kotlin
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
    var tab by remember { mutableStateOf(if (state.loadedFileId != null) SfTab.Presets else SfTab.All) }
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<SfCategory?>(null) }
    var selectedBank by remember { mutableIntStateOf(state.loadedBank) }

    var contextMenu by remember { mutableStateOf<ContextMenu?>(null) }
    var moveSheet by remember { mutableStateOf<SfRow?>(null) }
    var pendingDelete by remember { mutableStateOf<SfRow.File?>(null) }
    var pendingDeleteKey by remember { mutableStateOf<Long?>(null) }

    val hiddenFileKeys = remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(state.isOpen, state.variant, state.loadedFileId) {
        if (state.isOpen) {
            // JSX: on atterrit sur Presets uniquement si un soundfont est deja charge, sinon All
            tab = if (state.loadedFileId != null) SfTab.Presets else SfTab.All
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
            .shadow(4.dp, RowShape, spotColor = Color(0x99000000), ambientColor = Color(0x00000000))
            .clip(RowShape)
            .background(if (active) Color(0xFF1F2836) else Color(0xFF19212E))
            .border(1.dp, if (active) white(0.15f) else white(0.09f), RowShape)
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
                .shadow(4.dp, RowShape, spotColor = Color(0x99000000), ambientColor = Color(0x00000000))
                .clip(RowShape)
                .background(if (row.isLoaded) Color(0xFF1F2836) else Color(0xFF19212E))
                .border(1.dp, if (row.isLoaded) white(0.15f) else white(0.09f), RowShape)
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
            // A short confirmation, not a full-width bar: bounded and centred at the bottom.
            .widthIn(max = 420.dp)
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
            Text(text, style = sfText(13.5.sp, FontWeight.SemiBold, SfColors.Ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(16.dp))
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

````

---

## 2. Référence visuelle — `SoundfontsPreview.jsx` (fichier intégral)

Le JSX d'origine. C'est lui qui fait foi pour toute question de style.

**Attention conversion** : le JSX dessine sur un canvas de 891 × 411 px avec un
`scale()`. Ses `fontSize`/`height` sont des **pixels de canvas**, pas des dp. Un
`fontSize: 14.5` JSX ≠ `14.5.sp` Kotlin. Aucun facteur d'échelle officiel n'a été
défini — c'est l'origine historique des tailles « disproportionnées ».

### `SoundfontsPreview.jsx`

- **Chemin :** `app/assets/SoundfontsPreview.jsx`
- **Taille :** 47282 octets · **lignes :** 665 · **sha256 (12) :** `6ac2a4f3717c`
- Référence de design. Les valeurs sont en pixels de canvas, pas en dp.

````jsx
import { useState, useRef, useEffect, useMemo } from "react";

/* ─────────────────────────────────────────────────────────────
   SOUNDFONTS WINDOW — landscape smartphone preview (891 × 411 dp)
   Everything inside the device frame is the real design, in English.
   The mixer behind the window is a simple simulation.
   Controls under the frame are preview helpers only.
   ───────────────────────────────────────────────────────────── */

const W = 891, H = 411;
const AC = "#8FB2E8";
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const vib = (ms) => { try { navigator.vibrate && navigator.vibrate(ms); } catch (e) {} };
const MONO = "ui-monospace, SFMono-Regular, Menlo, monospace";

const WIN = { x: 116, y: 20, w: 660, h: 372 };

/* ── classification by name (first match wins) ── */
const RULES = [
  ["organ", /organ|b3|hammond|leslie|harmonium/i],
  ["drums", /drum|kit|perc|snare|kick|hi-?hat|cymbal|conga|bongo|tambour|timpani/i],
  ["bass", /bass/i],
  ["keys", /rhodes|wurl|clav|tine|e\.?piano|electric piano|harpsichord|accordion|acordeon|celesta/i],
  ["piano", /piano|grand|honky/i],
  ["guitar", /guitar|nylon|banjo|ukulele|mandolin|sitar/i],
  ["mallets", /vib|marimba|xylo|glock|bell|chime/i],
  ["strings", /string|violin|viola|cello|harp|pizzicato|orchestra/i],
  ["brass", /brass|trumpet|trombone|tuba|horn|cornet|flugel/i],
  ["wind", /flute|sax|oboe|clarinet|bassoon|piccolo|recorder|whistle/i],
  ["pads", /pad|choir|voice|aah|ooh|halo|sweep|atmos/i],
  ["synth", /lead|synth|saw|square|arp|pluck/i],
  ["fx", /fx|rain|sci-?fi|noise|echo|sound ?track|crystal|goblin/i],
];
const CATS = [
  { id: "piano", label: "Piano" }, { id: "keys", label: "Keys" }, { id: "organ", label: "Organ" },
  { id: "guitar", label: "Guitar" }, { id: "bass", label: "Bass" }, { id: "strings", label: "Strings" },
  { id: "brass", label: "Brass" }, { id: "wind", label: "Woodwind" }, { id: "synth", label: "Synth" },
  { id: "pads", label: "Pads" }, { id: "mallets", label: "Mallets" }, { id: "drums", label: "Drums & Perc" },
  { id: "fx", label: "FX" },
];
const SPECIAL = [{ id: "fav", label: "Favorites" }, { id: "other", label: "Other" }];
const catLabel = (id) => [...CATS, ...SPECIAL].find((c) => c.id === id)?.label || id;
const classify = (name) => {
  for (const [id, re] of RULES) if (re.test(name)) return id;
  return "other";
};

/* ── data ── */
const B0 = ["Grand Piano", "Bright Grand Piano", "Electric Grand Piano", "Honky-Tonk Piano", "Tine Electric Piano", "Chorused Electric Piano", "Harpsichord", "Clavinet", "Celesta", "Glockenspiel", "Vibraphone", "Marimba", "Xylophone", "Tubular Bells", "Hammond Organ", "Percussive Organ", "Rock Organ", "Church Organ", "Reed Organ", "Accordion", "Nylon Guitar", "Steel Guitar", "Jazz Guitar", "Clean Guitar", "Muted Guitar", "Overdriven Guitar", "Acoustic Bass", "Fingered Bass", "Picked Bass", "Fretless Bass", "Slap Bass", "Synth Bass", "Violin", "Viola", "Cello", "Pizzicato Strings", "Orchestral Harp", "String Ensemble", "Synth Strings", "Choir Aahs", "Voice Oohs", "Trumpet", "Trombone", "Tuba", "French Horn", "Brass Section", "Soprano Sax", "Alto Sax", "Oboe", "Clarinet", "Flute", "Pan Flute", "Square Lead", "Saw Lead", "New Age Pad", "Warm Pad", "Halo Pad", "Sweep Pad", "Rain", "Sci-Fi", "Standard Kit"];
const BANKS = [
  { id: 0, label: "Bank 0 (Main)", presets: B0 },
  { id: 1, label: "Bank 1", presets: ["Detuned Piano", "Stereo Grand", "Soft Rhodes", "Wurly", "Dream Pad", "Analog Saw", "Pluck Lead", "Soft Choir"] },
  { id: 2, label: "Bank 2", presets: ["Room Kit", "Brush Kit", "Electronic Kit", "Orchestra Kit", "Jazz Organ", "Gospel Organ"] },
  { id: 3, label: "Bank 3", presets: ["Dark Strings", "Slow Strings", "Warm Brass", "Solo Cello", "Solo Violin"] },
  { id: 4, label: "Bank 4", presets: ["Nylon Soft", "Steel Bright", "Funk Guitar", "Octave Guitar"] },
  { id: 5, label: "Bank 5", presets: ["Sub Bass", "Reese Bass", "Pulse Bass", "Wobble Lead"] },
];
const one = (presets) => [{ id: 0, label: "Bank 0 (Main)", presets }];
const SFS0 = [
  { id: "gu", name: "GeneralUser GS.sf2", size: "30,6 MB", banks: BANKS },
  { id: "bg", name: "241-Bassguitars.SF2", size: "80 KB", banks: one(["Finger Bass", "Pick Bass"]) },
  { id: "tr", name: "463-Trumpet.sf2", size: "1,6 MB", banks: one(["Trumpet", "Muted Trumpet", "Flugelhorn", "Brass Stab"]) },
  { id: "ac", name: "ACORDEON X5D.sf2", size: "7,6 MB", banks: one(["Musette", "Bandoneon", "Accordion Soft"]) },
  { id: "av", name: "Alto Soft Vib.sf2", size: "127,2 MB", banks: one(["Soft Vibraphone", "Alto Vib Hard", "Vib Tremolo"]) },
  { id: "aw", name: "Analog Warm Pad Vip.sf2", size: "40,4 MB", banks: one(["Warm Pad", "Warm Pad Slow", "Warm Pad Wide"]) },
  { id: "b3", name: "B3 Nord 1 Leslie.sf2", size: "12,8 MB", banks: one(["B3 Slow", "B3 Fast", "B3 Rock"]) },
  { id: "dk", name: "Drum Kit Studio.sf2", size: "22,0 MB", banks: one(["Studio Kit", "Tight Kit", "Big Room Kit"]) },
  { id: "fs", name: "Flute & Sax.sf2", size: "9,1 MB", banks: one(["Flute", "Alto Sax", "Tenor Sax"]) },
  { id: "pi", name: "Grand Piano Steinway.sf2", size: "210,4 MB", banks: one(["Steinway Grand", "Steinway Soft"]) },
  { id: "rh", name: "Rhodes Mark I.sf2", size: "14,3 MB", banks: one(["Rhodes Soft", "Rhodes Bright", "Rhodes Phase"]) },
  { id: "se", name: "Strings Ensemble Pro.sf2", size: "58,0 MB", banks: one(["Strings Legato", "Strings Staccato"]) },
  { id: "ze", name: "Zebra Unknown.sf2", size: "3,2 MB", banks: one(["Texture One", "Texture Two"]) },
  { id: "sw", name: "Sine Waves Collection.sf2", size: "1,1 MB", banks: one(["Sine 440", "Sine Sweep"]) },
];

const CSS = `
.sf *{box-sizing:border-box}
.sf button{font-family:inherit}
@keyframes rise{from{opacity:0;translate:0 8px}to{opacity:1;translate:0 0}}
.rise{animation:rise .24s cubic-bezier(.2,.8,.2,1) backwards}
@keyframes slideL{from{opacity:0;translate:14px 0}to{opacity:1;translate:0 0}}
@keyframes slideR{from{opacity:0;translate:-14px 0}to{opacity:1;translate:0 0}}
@keyframes roll{from{opacity:0;translate:0 7px}to{opacity:1;translate:0 0}}
.roll{animation:roll .2s ease-out}
@keyframes pop{0%{transform:scale(1)}40%{transform:scale(1.5) rotate(-12deg)}100%{transform:scale(1)}}
@keyframes bar{0%,100%{transform:scaleY(.35)}50%{transform:scaleY(1)}}
@keyframes fill{from{width:0}to{width:100%}}
@keyframes snackIn{from{opacity:0;translate:0 14px}to{opacity:1;translate:0 0}}
@keyframes menuIn{from{opacity:0;scale:.95}to{opacity:1;scale:1}}
@keyframes sheetIn{from{opacity:0;translate:0 24px}to{opacity:1;translate:0 0}}
@keyframes breathe{0%,100%{opacity:.5}50%{opacity:1}}
.press{transition:transform .2s cubic-bezier(.3,1.5,.5,1),background .25s,border-color .25s,opacity .25s}
.press:active{transform:scale(.985)}
.btn{transition:transform .15s cubic-bezier(.3,1.5,.5,1),filter .2s,background .2s}
.btn:active{transform:scale(.95)}
.btn:hover{filter:brightness(1.08)}
.noscroll::-webkit-scrollbar{width:0;height:0}
.noscroll{scrollbar-width:none}
`;

/* ── icons ── */
function Ico({ n, size = 16, color = "currentColor", sw = 2, fill = "none" }) {
  const P = {
    search: <><circle cx="11" cy="11" r="6.5" /><path d="M20 20l-4.2-4.2" /></>,
    plus: <path d="M12 5v14M5 12h14" />,
    x: <path d="M6 6l12 12M18 6L6 18" />,
    check: <path d="M5 12.5l4.5 4.5L19 7.5" />,
    star: <path d="M12 3.5l2.6 5.4 5.9.8-4.3 4.1 1 5.9L12 16.9l-5.2 2.8 1-5.9L3.5 9.7l5.9-.8z" />,
    dots: <><circle cx="12" cy="5" r="1.6" fill="currentColor" /><circle cx="12" cy="12" r="1.6" fill="currentColor" /><circle cx="12" cy="19" r="1.6" fill="currentColor" /></>,
    file: <><path d="M6 3h8l4 4v14H6z" /><path d="M14 3v4h4" /></>,
    note: <><path d="M9 18V6l10-2v12" /><circle cx="7" cy="18" r="2.4" /><circle cx="17" cy="16" r="2.4" /></>,
  };
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill={fill} stroke={color} strokeWidth={sw} strokeLinecap="round" strokeLinejoin="round">{P[n]}</svg>
  );
}

function Roll({ value }) {
  const prev = useRef(value);
  const [k, setK] = useState(0);
  useEffect(() => { if (prev.current !== value) { prev.current = value; setK((a) => a + 1); } }, [value]);
  return <span key={k} className={k ? "roll" : ""} style={{ display: "inline-block" }}>{value}</span>;
}

function Hl({ text, q }) {
  const s = q.trim();
  if (!s) return text;
  const i = text.toLowerCase().indexOf(s.toLowerCase());
  if (i < 0) return text;
  return <>{text.slice(0, i)}<span style={{ color: AC, background: "rgba(143,178,232,.14)", borderRadius: 3 }}>{text.slice(i, i + s.length)}</span>{text.slice(i + s.length)}</>;
}

/* ── simulated mixer behind the window ── */
function MixerSim({ onName }) {
  const Blk = ({ x, y, w, h, r = 10, bg = "linear-gradient(180deg,#2A3347,#202839)", children, style, onClick }) => (
    <div onClick={onClick} style={{ position: "absolute", left: x, top: y, width: w, height: h, borderRadius: r, background: bg, border: "1px solid rgba(255,255,255,.06)", display: "flex", alignItems: "center", justifyContent: "center", color: "#C9D1E0", fontSize: 12, fontWeight: 700, cursor: onClick ? "pointer" : "default", ...style }}>{children}</div>
  );
  const cols = ["#E08A2E", "#2FB37A", "#3C7BE0", "#8A4FD8"];
  const names = ["Grand Piano", "Motif XF Galaxy DX", "Soft Trumpet", "Accordeon"];
  return (
    <div style={{ position: "absolute", inset: 0, background: "linear-gradient(180deg,#141B27,#0F141D)" }}>
      <Blk x={11} y={9} w={117} h={40}>TRANS 0</Blk>
      <Blk x={135} y={9} w={117} h={40}>OCT 0</Blk>
      <Blk x={258} y={9} w={306} h={40} bg="#0F141D" style={{ color: "#566075" }}>- - -</Blk>
      <Blk x={570} y={9} w={40} h={40} bg="#C2587A" style={{ color: "#fff" }}>⊘</Blk>
      <Blk x={617} y={9} w={77} h={40} style={{ fontSize: 10, letterSpacing: 1 }}>SUSTAIN</Blk>
      <Blk x={842} y={9} w={38} h={40}>⚙</Blk>
      {cols.map((c, i) => {
        const x = 11 + i * 132.2;
        return (
          <div key={i} style={{ position: "absolute", left: x, top: 53, width: 126, height: 284, borderRadius: 16, background: `linear-gradient(180deg,#1B2232 30%, ${c}22)`, border: "1px solid rgba(255,255,255,.06)" }}>
            <div onClick={i === 0 ? (e) => onName(e, "track") : undefined} className={i === 0 ? "btn" : ""}
              style={{ position: "absolute", left: 4, top: 5, width: 118, height: 25, borderRadius: 9, background: "#141A26", border: "1px solid rgba(255,255,255,.08)", display: "grid", placeItems: "center", color: "#E3E8F2", fontSize: 11.5, fontWeight: 800, cursor: i === 0 ? "pointer" : "default", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{names[i]}</div>
            <div style={{ position: "absolute", left: 33, top: 36, width: 42, height: 23, borderRadius: 7, background: "#2A3347", display: "grid", placeItems: "center", fontSize: 11, fontWeight: 800 }}>S</div>
            <div style={{ position: "absolute", left: 78, top: 36, width: 42, height: 23, borderRadius: 7, background: "#2A3347", display: "grid", placeItems: "center", fontSize: 11, fontWeight: 800 }}>M</div>
            <div style={{ position: "absolute", left: 55, top: 66, width: 15, height: 184, borderRadius: 8, background: "#0C1018" }} />
            <div style={{ position: "absolute", left: 55, top: 130, width: 15, height: 120, borderRadius: 8, background: c, opacity: 0.8 }} />
            <div style={{ position: "absolute", left: 37, top: 108, width: 52, height: 40, borderRadius: 7, background: "linear-gradient(180deg,#6A7396,#3C4466)", border: "1px solid rgba(255,255,255,.15)" }} />
          </div>
        );
      })}
      <Blk x={17} y={348} w={86} h={45}>CPU 12%</Blk>
      <Blk x={108} y={348} w={76} h={45}>116 BPM</Blk>
      <Blk x={189} y={348} w={89} h={45} bg="#8FB2E8" style={{ color: "#0F1726" }}>Default</Blk>
      <Blk x={282} y={348} w={88} h={45}>Snapshot 1</Blk>
      <Blk x={375} y={348} w={88} h={45}>Snapshot 2</Blk>
      <Blk x={697} y={348} w={125} h={45}>M ▬▬▬ FX</Blk>
      <Blk x={828} y={348} w={46} h={45}>♫</Blk>
    </div>
  );
}

/* ═══════════════ the window ═══════════════ */

function SoundfontWindow({ open, variant, origin, onClose, snack }) {
  const [tab, setTab] = useState("presets");
  const [dir, setDir] = useState(1);
  const [sfs, setSfs] = useState(SFS0);
  const [activeSf, setActiveSf] = useState("gu");
  const [bank, setBank] = useState(0);
  const [activeKey, setActiveKey] = useState("gu|0|0");
  const [pending, setPending] = useState(null);
  const [cat, setCat] = useState(null);
  const [query, setQuery] = useState("");
  const [favs, setFavs] = useState(() => new Set(["gu|0|10", "rh"]));
  const [over, setOver] = useState({});            // key -> category id (manual moves)
  const [leaving, setLeaving] = useState([]);
  const [load, setLoad] = useState(null);          // { id, p, ms }
  const [preview, setPreview] = useState(null);
  const [drag, setDrag] = useState(null);          // { key, label, x, y }
  const [hover, setHover] = useState(null);
  const [menu, setMenu] = useState(null);          // { key, kind, x, y }
  const [sheet, setSheet] = useState(null);        // { key, kind, name }
  const [popId, setPopId] = useState(null);
  const [indexOn, setIndexOn] = useState(false);
  const [indexLetter, setIndexLetter] = useState(null);
  const [hold, setHold] = useState(false);

  const listRef = useRef(null);
  const rowRefs = useRef({});
  const catRefs = useRef({});
  const winRef = useRef(null);
  const g = useRef({ key: null, name: "", x: 0, y: 0, t: null, held: false, dragging: false, moved: false, kind: "preset" });
  const idxT = useRef(null);
  const loadT = useRef(null);

  const sf = sfs.find((s) => s.id === activeSf);
  const multiBank = sf.banks.length > 1;
  const isPad = variant === "pad";

  /* keep the bank valid when the soundfont changes */
  useEffect(() => { if (!sf.banks.some((b) => b.id === bank)) setBank(sf.banks[0].id); }, [activeSf]); // eslint-disable-line

  /* pad variant starts on Pads */
  useEffect(() => { if (open) { setCat(isPad ? "pads" : null); setQuery(""); } }, [open, variant]); // eslint-disable-line

  /* base items of the current tab */
  const searching = query.trim().length > 0;
  const presetsAll = useMemo(() => sf.banks.flatMap((b) => b.presets.map((p, i) => ({ key: `${sf.id}|${b.id}|${i}`, kind: "preset", name: p, num: i, bank: b.id, bankLabel: b.label }))), [sf]);
  const base = useMemo(() => {
    if (tab === "all") return [...sfs].sort((a, b) => a.name.localeCompare(b.name, undefined, { sensitivity: "base" })).map((s) => ({ key: s.id, kind: "sf", name: s.name, size: s.size }));
    return searching ? presetsAll : presetsAll.filter((p) => p.bank === bank);
  }, [tab, sfs, presetsAll, bank, searching]);
  const catOf = (it) => over[it.key] || classify(it.name);

  const counts = useMemo(() => {
    const src = tab === "all" ? base : presetsAll.filter((p) => p.bank === bank);
    const c = { fav: 0, other: 0 };
    CATS.forEach((x) => { c[x.id] = 0; });
    src.forEach((it) => { const k = over[it.key] || classify(it.name); c[k] = (c[k] || 0) + 1; if (favs.has(it.key)) c.fav++; });
    return c;
  }, [tab, base, presetsAll, bank, over, favs]);

  const visCats = [...CATS, ...SPECIAL].filter((c) => counts[c.id] > 0);

  /* leave a category that became empty */
  useEffect(() => { if (cat && !(counts[cat] > 0)) setCat(null); }, [counts, cat]);

  const visible = base
    .filter((it) => !searching || it.name.toLowerCase().includes(query.trim().toLowerCase()))
    .filter((it) => {
      if (searching || !cat) return true;           // a search looks in every category and bank
      if (cat === "fav") return favs.has(it.key);
      return catOf(it) === cat;
    });
  const shown = [...visible, ...base.filter((it) => leaving.includes(it.key) && !visible.some((v) => v.key === it.key))];

  const listKey = `${tab}|${bank}|${cat}|${query}|${activeSf}`;
  const letters = useMemo(() => Array.from(new Set(visible.map((v) => (/[a-z]/i.test(v.name[0]) ? v.name[0].toUpperCase() : "#")))), [visible.length, tab]); // eslint-disable-line

  /* actions */
  const toggleFav = (key) => {
    setFavs((s) => { const n = new Set(s); n.has(key) ? n.delete(key) : n.add(key); return n; });
    setPopId(key); setTimeout(() => setPopId(null), 460); vib(8);
    if (cat === "fav" && favs.has(key)) { setLeaving((l) => [...l, key]); setTimeout(() => setLeaving((l) => l.filter((x) => x !== key)), 280); }
  };
  const moveTo = (key, to, name) => {
    const prev = over[key];
    const before = over[key] || classify(name);
    if (before === to) return;
    setOver((o) => ({ ...o, [key]: to }));
    if (cat && cat !== to && !searching) { setLeaving((l) => [...l, key]); setTimeout(() => setLeaving((l) => l.filter((x) => x !== key)), 280); }
    vib(12);
    snack(`Moved to ${catLabel(to)}`, () => setOver((o) => { const n = { ...o }; if (prev) n[key] = prev; else delete n[key]; return n; }));
  };
  const pickPreset = (it) => {
    if (it.bank !== bank) setBank(it.bank);
    setPending(it.key); vib(8);
    setTimeout(() => { setActiveKey(it.key); setPending(null); }, 430);
  };
  const startLoad = (it) => {
    if (it.key === activeSf || load) return;
    const mb = parseFloat(it.size.replace(",", ".")) * (/KB/.test(it.size) ? 0.001 : 1);
    const ms = clamp(mb * 14, 650, 1800);
    setLoad({ id: it.key, p: 0 }); vib(8);
    const t0 = performance.now();
    clearInterval(loadT.current);
    loadT.current = setInterval(() => {
      const p = clamp((performance.now() - t0) / ms, 0, 1);
      setLoad({ id: it.key, p });
      if (p >= 1) {
        clearInterval(loadT.current); setLoad(null);
        setActiveSf(it.key); setActiveKey(`${it.key}|0|0`); setBank(0); vib(12);
        snack(`Loaded ${it.name}`);
      }
    }, 40);
  };
  const cancelLoad = () => { clearInterval(loadT.current); setLoad(null); };
  const importSf = () => {
    const id = "imp" + Date.now();
    const nsf = { id, name: "Imported Strings.sf2", size: "4,2 MB", banks: one(["Imported Strings", "Imported Pad"]) };
    setSfs((s) => [nsf, ...s]); setTab("all"); setCat(null); setQuery("");
    setLoad({ id, p: 0, imp: true });
    const t0 = performance.now(); clearInterval(loadT.current);
    loadT.current = setInterval(() => {
      const p = clamp((performance.now() - t0) / 1100, 0, 1);
      setLoad({ id, p, imp: true });
      if (p >= 1) { clearInterval(loadT.current); setLoad(null); snack("Imported Strings.sf2 added"); }
    }, 40);
  };
  const switchTab = (t) => { if (t === tab) return; setDir(t === "all" ? 1 : -1); setTab(t); setCat(isPad ? "pads" : null); setQuery(""); };

  /* press gesture: tap = pick · hold = audition · hold + move = drag to a category */
  const stopAll = () => { window.removeEventListener("pointermove", onMove); window.removeEventListener("pointerup", onUp); window.removeEventListener("pointercancel", onUp); };
  function onMove(e) {
    const s = g.current;
    if (!s.key) return;
    const dx = e.clientX - s.x, dy = e.clientY - s.y;
    const dist = Math.hypot(dx, dy);
    if (!s.held && dist > 8) { clearTimeout(s.t); s.moved = true; return; }
    if (s.held && !s.dragging && dist > 12) { s.dragging = true; setPreview(null); vib(10); }
    if (s.dragging) {
      const sr = winRef.current.getBoundingClientRect();
      const sc = sr.width / WIN.w;
      setDrag({ key: s.key, label: s.name, x: (e.clientX - sr.left) / sc, y: (e.clientY - sr.top) / sc });
      let hv = null;
      Object.entries(catRefs.current).forEach(([id, el]) => {
        if (!el) return;
        const r = el.getBoundingClientRect();
        if (e.clientX >= r.left && e.clientX <= r.right && e.clientY >= r.top && e.clientY <= r.bottom) hv = id;
      });
      setHover(hv);
    }
  }
  function onUp() {
    const s = g.current;
    clearTimeout(s.t);
    if (s.dragging) {
      const target = hoverRef.current;
      if (target && target !== "fav") moveTo(s.key, target, s.name);
      else if (target === "fav") toggleFav(s.key);
      setDrag(null); setHover(null);
    } else if (s.held) {
      setPreview(null);
    } else if (!s.moved && s.tap) {
      s.tap();
    }
    g.current = { ...g.current, key: null, held: false, dragging: false, moved: false };
    stopAll();
  }
  const hoverRef = useRef(null);
  hoverRef.current = hover;
  const rowDown = (e, it, tap) => {
    if (e.target.closest("[data-nopress]")) return;
    clearTimeout(g.current.t);
    g.current = {
      key: it.key, name: it.name, x: e.clientX, y: e.clientY, held: false, dragging: false, moved: false, tap,
      t: setTimeout(() => { g.current.held = true; setPreview(it.key); vib(10); }, 360),
    };
    window.addEventListener("pointermove", onMove);
    window.addEventListener("pointerup", onUp);
    window.addEventListener("pointercancel", onUp);
  };
  useEffect(() => {
    const el = listRef.current;
    if (!el) return;
    const stop = (e) => { if (g.current.held || g.current.dragging) e.preventDefault(); };
    el.addEventListener("touchmove", stop, { passive: false });
    return () => el.removeEventListener("touchmove", stop);
  }, [tab, open]);
  useEffect(() => () => { stopAll(); clearInterval(loadT.current); }, []); // eslint-disable-line

  /* subtle A–Z index */
  const onListScroll = () => {
    if (tab !== "all" || searching) return;
    setIndexOn(true);
    clearTimeout(idxT.current);
    idxT.current = setTimeout(() => { setIndexOn(false); setIndexLetter(null); }, 1300);
  };
  const jumpLetter = (clientY, strip) => {
    const r = strip.getBoundingClientRect();
    const f = clamp((clientY - r.top) / r.height, 0, 0.999);
    const L = letters[Math.floor(f * letters.length)];
    setIndexLetter(L); setIndexOn(true);
    clearTimeout(idxT.current);
    idxT.current = setTimeout(() => { setIndexOn(false); setIndexLetter(null); }, 1300);
    const it = visible.find((v) => (/[a-z]/i.test(v.name[0]) ? v.name[0].toUpperCase() : "#") === L);
    const el = it && rowRefs.current[it.key];
    if (el && listRef.current) listRef.current.scrollTo({ top: el.offsetTop - 4, behavior: "auto" });
  };

  /* ── rows ── */
  const renderRow = (it, i) => {
    const isPreset = it.kind === "preset";
    const active = isPreset ? it.key === activeKey : it.key === activeSf;
    const isPending = pending === it.key;
    const isLeaving = leaving.includes(it.key) && !visible.some((v) => v.key === it.key);
    const isLoad = load && load.id === it.key;
    const dragging = drag && drag.key === it.key;
    const fav = favs.has(it.key);
    const tap = isPreset ? () => pickPreset(it) : () => (isLoad ? cancelLoad() : startLoad(it));
    return (
      <div key={it.key} ref={(el) => (rowRefs.current[it.key] = el)}
        style={{ maxHeight: isLeaving ? 0 : 44, opacity: isLeaving ? 0 : 1, marginBottom: isLeaving ? 0 : 4, overflow: "hidden", transition: "max-height .26s ease, opacity .2s, margin .26s" }}>
        <div className="rise" style={{ animationDelay: `${Math.min(i, 8) * 16}ms` }}>
          <div className="press" onPointerDown={(e) => rowDown(e, it, tap)}
            style={{
              position: "relative", height: 40, borderRadius: 12, display: "flex", alignItems: "center", gap: 10, padding: "0 6px 0 12px", overflow: "hidden", cursor: "pointer", userSelect: "none", touchAction: "pan-y",
              background: active ? "linear-gradient(180deg, rgba(255,255,255,.12), rgba(255,255,255,.07))" : "linear-gradient(180deg, rgba(255,255,255,.065), rgba(255,255,255,.03))",
              border: `1px solid ${active ? "rgba(255,255,255,.17)" : "rgba(255,255,255,.06)"}`,
              opacity: dragging ? 0.3 : 1, transform: preview === it.key ? "translateY(-1px)" : "none", boxShadow: preview === it.key ? "0 6px 16px rgba(0,0,0,.4)" : "none",
            }}>
            {isPreset ? (
              <span style={{ width: 24, fontSize: 11, fontWeight: 800, color: "#6F7A90", fontFamily: MONO, textAlign: "right", flex: "none" }}>{it.num}</span>
            ) : (
              <span style={{ width: 24, display: "grid", placeItems: "center", color: "#7B859A", flex: "none" }}><Ico n="file" size={17} /></span>
            )}
            <span style={{ flex: 1, minWidth: 0, display: "flex", alignItems: "baseline", gap: 8 }}>
              <span style={{ fontSize: 14.5, fontWeight: 700, color: "#E9EDF5", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}><Hl text={it.name} q={query} /></span>
              {isPreset && (searching || !multiBank) && <span style={{ fontSize: 10.5, color: "#6F7A90", whiteSpace: "nowrap", flex: "none" }}>{searching ? `${it.bankLabel} · #${it.num}` : `Preset #${it.num}`}</span>}
            </span>
            {preview === it.key && (
              <span style={{ display: "flex", alignItems: "center", gap: 2, height: 14, flex: "none" }}>
                {[0, 1, 2, 3].map((b) => <span key={b} style={{ width: 2.5, height: 14, borderRadius: 2, background: AC, transformOrigin: "50% 100%", animation: `bar ${0.5 + b * 0.12}s ease-in-out infinite` }} />)}
              </span>
            )}
            {!isPreset && <span style={{ fontSize: 12, fontWeight: 600, color: "#7B859A", fontFamily: MONO, flex: "none", minWidth: 58, textAlign: "right" }}>{it.size}</span>}
            {active && !isLoad && (
              <span style={{ display: "inline-flex", alignItems: "center", gap: 4, padding: "3px 8px", borderRadius: 8, background: "rgba(255,255,255,.1)", color: "#C7CFDD", fontSize: 9.5, letterSpacing: 1.1, fontWeight: 800, flex: "none" }}>
                <Ico n="check" size={11} sw={3} color={AC} />{isPreset ? "ACTIVE" : "LOADED"}
              </span>
            )}
            {isLoad && (
              <span data-nopress onClick={(e) => { e.stopPropagation(); cancelLoad(); }} style={{ width: 24, height: 24, flex: "none", position: "relative", display: "grid", placeItems: "center", cursor: "pointer" }}>
                <svg width="24" height="24" viewBox="0 0 24 24" style={{ transform: "rotate(-90deg)" }}>
                  <circle cx="12" cy="12" r="9" fill="none" stroke="rgba(255,255,255,.12)" strokeWidth="2.5" />
                  <circle cx="12" cy="12" r="9" fill="none" stroke={AC} strokeWidth="2.5" strokeLinecap="round" strokeDasharray={2 * Math.PI * 9} strokeDashoffset={2 * Math.PI * 9 * (1 - load.p)} />
                </svg>
                {!load.imp && <span style={{ position: "absolute", color: "#C7CFDD" }}><Ico n="x" size={9} sw={3} /></span>}
              </span>
            )}
            <button data-nopress onClick={() => toggleFav(it.key)} className="btn" aria-label="Favorite"
              style={{ width: 28, height: 28, border: 0, background: "transparent", cursor: "pointer", display: "grid", placeItems: "center", color: fav ? "#E6BC73" : "#566075", flex: "none", animation: popId === it.key ? "pop .45s" : "none" }}>
              <Ico n="star" size={15} sw={1.8} fill={fav ? "currentColor" : "none"} />
            </button>
            <button data-nopress onClick={(e) => { const r = e.currentTarget.getBoundingClientRect(); const wr = winRef.current.getBoundingClientRect(); const sc = wr.width / WIN.w; setMenu({ key: it.key, kind: it.kind, name: it.name, x: (r.right - wr.left) / sc, y: (r.bottom - wr.top) / sc }); }} className="btn" aria-label="More"
              style={{ width: 26, height: 28, border: 0, background: "transparent", cursor: "pointer", display: "grid", placeItems: "center", color: "#6F7A90", flex: "none" }}>
              <Ico n="dots" size={15} />
            </button>
            {isPending && <span style={{ position: "absolute", left: 0, bottom: 0, height: 2, background: AC, animation: "fill .43s linear forwards" }} />}
            {isLoad && load.imp && <span style={{ position: "absolute", left: 0, bottom: 0, height: 2, width: `${load.p * 100}%`, background: AC }} />}
          </div>
        </div>
      </div>
    );
  };

  /* ── window ── */
  const ox = origin.x - WIN.x, oy = origin.y - WIN.y;
  const tabIdx = tab === "presets" ? 0 : 1;
  const catIdx = visCats.findIndex((c) => c.id === cat);

  return (
    <div style={{ position: "absolute", inset: 0, zIndex: 50, pointerEvents: open ? "auto" : "none" }}>
      <div onClick={onClose} style={{ position: "absolute", inset: 0, background: "rgba(6,9,14,.62)", backdropFilter: "blur(2.5px)", opacity: open ? 1 : 0, transition: "opacity .28s" }} />
      <div ref={winRef} style={{
        position: "absolute", left: WIN.x, top: WIN.y, width: WIN.w, height: WIN.h, borderRadius: 24, overflow: "hidden",
        background: "linear-gradient(180deg,#232C3E,#1A2230)", border: "1px solid rgba(255,255,255,.1)", boxShadow: "0 28px 70px rgba(0,0,0,.6)",
        transformOrigin: `${ox}px ${oy}px`, transform: open ? "scale(1)" : "scale(.5)", opacity: open ? 1 : 0,
        transition: open ? "transform .38s cubic-bezier(.2,.9,.25,1), opacity .22s" : "transform .24s cubic-bezier(.4,0,.8,.4), opacity .18s",
      }}>
        {isPad && <div style={{ position: "absolute", inset: 0, pointerEvents: "none", background: `radial-gradient(60% 70% at 30% 20%, rgba(143,178,232,${hold ? 0.22 : 0.09}), transparent 70%)`, transition: "background .5s", animation: hold ? "none" : "breathe 5s ease-in-out infinite" }} />}

        {/* header */}
        <div style={{ position: "absolute", left: 14, right: 14, top: 14, height: 44, display: "flex", alignItems: "center", gap: 10 }}>
          <div style={{ flex: 1, height: 42, borderRadius: 14, border: "1px solid rgba(255,255,255,.1)", background: "rgba(15,20,29,.7)", display: "flex", alignItems: "center", gap: 8, padding: "0 12px", color: "#8690A4" }}>
            <Ico n="search" size={17} />
            <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder={tab === "all" ? "Search soundfonts" : "Search presets"}
              style={{ flex: 1, minWidth: 0, background: "transparent", border: 0, outline: "none", color: "#E9EDF5", fontSize: 14.5 }} />
            {query && <button onClick={() => setQuery("")} className="btn" style={{ border: 0, background: "transparent", color: "#8690A4", display: "grid", placeItems: "center", cursor: "pointer", padding: 0 }}><Ico n="x" size={14} /></button>}
          </div>
          <button onClick={importSf} className="btn" aria-label="Import" style={{ width: 42, height: 42, borderRadius: 21, border: "1px solid rgba(255,255,255,.2)", background: "linear-gradient(180deg,#9DBDEB,#7FA3DD)", color: "#0F1726", display: "grid", placeItems: "center", cursor: "pointer" }}><Ico n="plus" size={19} sw={2.6} /></button>
          <button onClick={onClose} className="btn" aria-label="Close" style={{ width: 42, height: 42, borderRadius: 21, border: "1px solid rgba(255,255,255,.12)", background: "rgba(255,255,255,.07)", color: "#E9EDF5", display: "grid", placeItems: "center", cursor: "pointer" }}><Ico n="x" size={17} /></button>
        </div>

        {/* left column */}
        <div style={{ position: "absolute", left: 14, top: 68, width: 154, bottom: 14, display: "flex", flexDirection: "column" }}>
          <div style={{ fontSize: 10, letterSpacing: 1.3, fontWeight: 700, color: "#7B859A", height: 16, textTransform: "uppercase" }}>{isPad ? "Pad sound" : "Track sound"}</div>
          <div style={{ position: "relative", display: "flex", padding: 3, borderRadius: 13, background: "rgba(15,20,29,.8)", border: "1px solid rgba(255,255,255,.08)", marginBottom: 8 }}>
            <div style={{ position: "absolute", top: 3, bottom: 3, left: 3, width: "calc((100% - 6px) / 2)", borderRadius: 10, background: "linear-gradient(180deg,#38445E,#2B3650)", boxShadow: "0 2px 6px rgba(0,0,0,.4)", transform: `translateX(${tabIdx * 100}%)`, transition: "transform .32s cubic-bezier(.3,1.35,.5,1)" }} />
            {[["presets", "Presets"], ["all", "All"]].map(([k, l]) => (
              <button key={k} onClick={() => switchTab(k)} style={{ position: "relative", flex: 1, height: 28, border: 0, background: "transparent", cursor: "pointer", fontSize: 12.5, fontWeight: 800, color: tab === k ? "#F1F4FA" : "#8690A4", transition: "color .2s" }}>{l}</button>
            ))}
          </div>
          <div className="noscroll" style={{ position: "relative", flex: 1, overflowY: "auto", paddingBottom: isPad ? 44 : 0 }}>
            <div style={{ position: "relative" }}>
              <div style={{ position: "absolute", left: 0, right: 0, top: 0, height: 32, borderRadius: 10, background: "linear-gradient(180deg, rgba(255,255,255,.11), rgba(255,255,255,.06))", transform: `translateY(${Math.max(0, catIdx) * 34}px)`, opacity: catIdx >= 0 ? 1 : 0, transition: "transform .32s cubic-bezier(.3,1.3,.5,1), opacity .2s" }} />
              {visCats.map((c, i) => {
                const on = c.id === cat, hv = hover === c.id && drag;
                return (
                  <div key={c.id}>
                    {c.id === "fav" && <div style={{ height: 1, background: "rgba(255,255,255,.07)", margin: "-1px 8px 3px" }} />}
                    <div ref={(el) => (catRefs.current[c.id] = el)} onClick={() => setCat(on ? null : c.id)} className="press"
                      style={{ position: "relative", height: 32, marginBottom: 2, padding: "0 10px", borderRadius: 10, display: "flex", alignItems: "center", cursor: "pointer", background: hv ? "rgba(143,178,232,.2)" : "transparent", boxShadow: hv ? "inset 0 0 0 1px rgba(143,178,232,.55)" : "none", transform: hv ? "scale(1.03)" : "none", transition: "background .18s, box-shadow .18s, transform .2s cubic-bezier(.3,1.5,.5,1)" }}>
                      <span style={{ flex: 1, fontSize: 13, fontWeight: 700, color: on ? "#F1F4FA" : c.id === "fav" ? "#E6BC73" : "#9AA5BA", transition: "color .2s" }}>{c.label}</span>
                      <span style={{ fontSize: 11, fontWeight: 700, color: on ? "#C7CFDD" : "#6F7A90", fontFamily: MONO }}><Roll value={counts[c.id]} /></span>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
          {isPad && (
            <button onPointerDown={() => setHold(true)} onPointerUp={() => setHold(false)} onPointerLeave={() => setHold(false)} onPointerCancel={() => setHold(false)} className="btn"
              style={{ position: "absolute", left: 0, right: 0, bottom: 0, height: 36, borderRadius: 13, border: "1px solid rgba(255,255,255,.14)", background: hold ? "rgba(143,178,232,.22)" : "rgba(255,255,255,.06)", color: hold ? AC : "#C7CFDD", display: "flex", alignItems: "center", justifyContent: "center", gap: 8, cursor: "pointer", fontSize: 12.5, fontWeight: 800, transition: "background .25s, color .25s", touchAction: "none" }}>
              <Ico n="note" size={15} /> Hold to hear
            </button>
          )}
        </div>

        {/* right column */}
        <div style={{ position: "absolute", left: 182, right: 14, top: 68, bottom: 14, display: "flex", flexDirection: "column" }}>
          {/* banks: always shown when the soundfont has several */}
          {tab === "presets" && multiBank && (
            <div className="noscroll" style={{ display: "flex", gap: 6, overflowX: "auto", paddingBottom: 8, flex: "none", opacity: searching ? 0.4 : 1, transition: "opacity .25s" }}>
              {sf.banks.map((b) => {
                const on = b.id === bank;
                return (
                  <button key={b.id} onClick={() => { setDir(b.id > bank ? 1 : -1); setBank(b.id); setQuery(""); }} className="btn"
                    style={{ flex: "none", height: 30, padding: "0 13px", borderRadius: 15, border: `1px solid ${on ? "rgba(255,255,255,.26)" : "rgba(255,255,255,.09)"}`, background: on ? "rgba(255,255,255,.13)" : "transparent", color: on ? "#F1F4FA" : "#8690A4", fontSize: 12, fontWeight: 800, cursor: "pointer", whiteSpace: "nowrap", transition: "background .25s, border-color .25s, color .25s" }}>{b.label}</button>
                );
              })}
            </div>
          )}
          {(cat || searching) && (
            <div style={{ display: "flex", gap: 6, paddingBottom: 6, flex: "none", alignItems: "center", height: 28 }}>
              {cat && <span onClick={() => setCat(null)} className="press" style={{ cursor: "pointer", display: "inline-flex", alignItems: "center", gap: 5, padding: "3px 6px 3px 10px", borderRadius: 10, background: "rgba(255,255,255,.08)", fontSize: 11.5, fontWeight: 700, color: "#C7CFDD" }}>{catLabel(cat)}<Ico n="x" size={11} sw={2.6} /></span>}
              {searching && <span style={{ fontSize: 11.5, color: "#7B859A" }}>{visible.length} {visible.length === 1 ? "result" : "results"} in all banks</span>}
            </div>
          )}

          <div style={{ position: "relative", flex: 1, minHeight: 0 }}>
            <div ref={listRef} onScroll={onListScroll} className="noscroll" style={{ position: "absolute", inset: 0, overflowY: "auto", paddingRight: tab === "all" ? 12 : 0 }}>
              <div key={listKey} style={{ animation: `${dir > 0 ? "slideL" : "slideR"} .26s cubic-bezier(.2,.8,.2,1)` }}>
                {shown.length === 0 && <div style={{ padding: "22px 6px", fontSize: 13.5, color: "#7B859A" }}>{searching ? `Nothing matches “${query}”.` : "Nothing here yet."}</div>}
                {shown.map((it, i) => renderRow(it, i))}
              </div>
            </div>

            {/* subtle A–Z index: only while scrolling or touching, only on the soundfonts list */}
            {tab === "all" && !searching && letters.length > 3 && (
              <div
                onPointerDown={(e) => { e.currentTarget.setPointerCapture(e.pointerId); jumpLetter(e.clientY, e.currentTarget); }}
                onPointerMove={(e) => { if (e.buttons) jumpLetter(e.clientY, e.currentTarget); }}
                style={{ position: "absolute", right: 0, top: 4, bottom: 4, width: 14, display: "flex", flexDirection: "column", justifyContent: "space-between", alignItems: "center", opacity: indexOn ? 1 : 0, transition: "opacity .3s", touchAction: "none", pointerEvents: indexOn ? "auto" : "none" }}>
                {letters.map((L) => <span key={L} style={{ fontSize: 8.5, fontWeight: 800, color: indexLetter === L ? AC : "#6F7A90", fontFamily: MONO }}>{L}</span>)}
              </div>
            )}
            {indexLetter && (
              <div style={{ position: "absolute", right: 24, top: "50%", translate: "0 -50%", width: 40, height: 40, borderRadius: 14, background: "#2B3650", border: "1px solid rgba(255,255,255,.16)", boxShadow: "0 8px 20px rgba(0,0,0,.45)", display: "grid", placeItems: "center", fontSize: 18, fontWeight: 800, color: "#F1F4FA", animation: "menuIn .14s ease-out" }}>{indexLetter}</div>
            )}
          </div>
        </div>

        {/* row menu */}
        {menu && (
          <>
            <div onClick={() => setMenu(null)} style={{ position: "absolute", inset: 0, zIndex: 20 }} />
            <div style={{ position: "absolute", left: clamp(menu.x - 170, 8, WIN.w - 178), top: Math.min(menu.y + 4, WIN.h - 112), width: 170, zIndex: 21, borderRadius: 14, padding: 5, background: "linear-gradient(180deg,#2A3447,#1E2736)", border: "1px solid rgba(255,255,255,.12)", boxShadow: "0 14px 34px rgba(0,0,0,.55)", animation: "menuIn .16s ease-out", transformOrigin: "top right" }}>
              {[
                [favs.has(menu.key) ? "Remove favorite" : "Add to favorites", () => { toggleFav(menu.key); setMenu(null); }],
                ["Move to…", () => { setSheet({ key: menu.key, kind: menu.kind, name: menu.name }); setMenu(null); }],
              ].map(([l, fn]) => (
                <button key={l} onClick={fn} className="btn" style={{ width: "100%", height: 34, border: 0, borderRadius: 9, background: "transparent", color: "#DCE2EE", fontSize: 13, fontWeight: 700, cursor: "pointer", textAlign: "left", padding: "0 10px" }}>{l}</button>
              ))}
            </div>
          </>
        )}

        {/* Move to… sheet */}
        {sheet && (
          <div style={{ position: "absolute", inset: 0, zIndex: 30 }}>
            <div onClick={() => setSheet(null)} style={{ position: "absolute", inset: 0, background: "rgba(6,9,14,.5)", animation: "fadeIn .2s" }} />
            <div style={{ position: "absolute", left: 14, right: 14, bottom: 14, borderRadius: 20, padding: "14px 16px", background: "linear-gradient(180deg,#2A3447,#1E2736)", border: "1px solid rgba(255,255,255,.12)", boxShadow: "0 -10px 40px rgba(0,0,0,.5)", animation: "sheetIn .26s cubic-bezier(.2,.9,.25,1)" }}>
              <div style={{ display: "flex", alignItems: "center", marginBottom: 10 }}>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontSize: 15, fontWeight: 800, color: "#F1F4FA" }}>Move to…</div>
                  <div style={{ fontSize: 11.5, color: "#7B859A", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{sheet.name} · now in {catLabel(over[sheet.key] || classify(sheet.name))}</div>
                </div>
                <button onClick={() => setSheet(null)} className="btn" style={{ width: 30, height: 30, borderRadius: 15, border: 0, background: "rgba(255,255,255,.07)", color: "#9AA5BA", display: "grid", placeItems: "center", cursor: "pointer" }}><Ico n="x" size={14} /></button>
              </div>
              <div style={{ display: "flex", flexWrap: "wrap", gap: 7 }}>
                {[...CATS, { id: "other", label: "Other" }].map((c) => {
                  const cur = (over[sheet.key] || classify(sheet.name)) === c.id;
                  return (
                    <button key={c.id} onClick={() => { moveTo(sheet.key, c.id, sheet.name); setSheet(null); }} className="btn"
                      style={{ height: 32, padding: "0 14px", borderRadius: 16, border: `1px solid ${cur ? "rgba(143,178,232,.6)" : "rgba(255,255,255,.1)"}`, background: cur ? "rgba(143,178,232,.14)" : "rgba(255,255,255,.04)", color: cur ? AC : "#C7CFDD", fontSize: 12.5, fontWeight: 700, cursor: "pointer" }}>{c.label}</button>
                  );
                })}
              </div>
            </div>
          </div>
        )}

        {/* drag ghost */}
        {drag && (
          <div style={{ position: "absolute", left: drag.x, top: drag.y, translate: "-30px -20px", zIndex: 40, pointerEvents: "none", width: 230, height: 40, borderRadius: 12, padding: "0 12px", display: "flex", alignItems: "center", background: "linear-gradient(180deg,#3A4663,#2B3650)", border: "1px solid rgba(255,255,255,.22)", boxShadow: "0 16px 34px rgba(0,0,0,.55)", scale: "1.03", fontSize: 14, fontWeight: 700, color: "#F1F4FA", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{drag.label}</div>
        )}
      </div>
    </div>
  );
}

/* ═══════════════ main ═══════════════ */

export default function SoundfontsPreview() {
  const [open, setOpen] = useState(false);
  const [variant, setVariant] = useState("track");
  const [origin, setOrigin] = useState({ x: 100, y: 72 });
  const [snack, setSnack] = useState(null);
  const [vp, setVp] = useState({ w: typeof window !== "undefined" ? window.innerWidth : 900, h: typeof window !== "undefined" ? window.innerHeight : 500 });
  const snackT = useRef(null);
  const stageRef = useRef(null);

  useEffect(() => {
    const on = () => setVp({ w: window.innerWidth, h: window.innerHeight });
    window.addEventListener("resize", on);
    return () => window.removeEventListener("resize", on);
  }, []);
  const scale = Math.max(0.3, Math.min((vp.w - 16) / W, (vp.h - 120) / H, 1.5));
  const portrait = vp.h > vp.w;

  const showSnack = (text, undo) => {
    clearTimeout(snackT.current);
    setSnack({ text, undo, key: Date.now() });
    snackT.current = setTimeout(() => setSnack(null), undo ? 4500 : 1800);
  };
  const openWin = (v, pt) => { setVariant(v); setOrigin(pt); setOpen(true); vib(8); };
  const fromName = (e) => {
    const sr = stageRef.current.getBoundingClientRect();
    const r = e.currentTarget.getBoundingClientRect();
    openWin("track", { x: (r.left + r.width / 2 - sr.left) / scale, y: (r.top + r.height / 2 - sr.top) / scale });
  };

  return (
    <div className="sf" style={{ minHeight: "100vh", background: "#080B11", color: "#E9EDF5", fontFamily: "Inter, 'Segoe UI', system-ui, sans-serif", display: "flex", flexDirection: "column", alignItems: "center", padding: "8px 8px 12px", fontVariantNumeric: "tabular-nums" }}>
      <style>{CSS}</style>
      <div style={{ width: W * scale, height: H * scale, borderRadius: 24 * scale, boxShadow: `0 0 0 ${Math.max(2, 3 * scale)}px #1B212C, 0 24px 60px rgba(0,0,0,.6)`, overflow: "hidden", position: "relative", flex: "none" }}>
        <div ref={stageRef} style={{ width: W, height: H, transform: `scale(${scale})`, transformOrigin: "top left", position: "relative", overflow: "hidden", background: "#0F141D" }}>
          <MixerSim onName={fromName} />
          <div style={{ position: "absolute", inset: 0, background: "rgba(8,11,18,.0)" }} />
          <SoundfontWindow open={open} variant={variant} origin={origin} onClose={() => setOpen(false)} snack={showSnack} />
          {snack && (
            <div key={snack.key} style={{ position: "absolute", left: "50%", bottom: 8, transform: "translateX(-50%)", zIndex: 90, animation: "snackIn .3s cubic-bezier(.2,.9,.25,1)" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 16, padding: "9px 16px", borderRadius: 14, background: "#222C40", border: "1px solid rgba(255,255,255,.1)", boxShadow: "0 12px 30px rgba(0,0,0,.5)", fontSize: 13.5, fontWeight: 600, color: "#E9EDF5" }}>
                <span>{snack.text}</span>
                {snack.undo && <button onClick={() => { snack.undo(); setSnack(null); }} className="btn" style={{ border: 0, background: "transparent", color: AC, fontWeight: 800, fontSize: 12.5, letterSpacing: 1, cursor: "pointer" }}>UNDO</button>}
              </div>
            </div>
          )}
        </div>
      </div>

      <div style={{ marginTop: 14, display: "flex", flexWrap: "wrap", gap: 8, justifyContent: "center", maxWidth: 760 }}>
        {[["Open · Track sound", () => openWin("track", { x: 70, y: 72 })], ["Open · Pad sound", () => openWin("pad", { x: 70, y: 375 })]].map(([l, fn]) => (
          <button key={l} onClick={fn} style={{ padding: "7px 14px", borderRadius: 999, border: "1px solid #2A3446", background: "#121823", color: "#C9D2E2", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>{l}</button>
        ))}
      </div>
      <div style={{ marginTop: 8, fontSize: 11.5, color: "#59647A", textAlign: "center", maxWidth: 620, lineHeight: 1.55 }}>
        Tap a row to load it · hold a row to audition · hold, then move, to drag it onto a category (or use ⋮ → Move to…) · star = favorite · + imports a soundfont · the first track name also opens the window
        {portrait && <><br />Rotate your phone to landscape for a larger view.</>}
      </div>
    </div>
  );
}

````

---

## 3. Câblage dans `MixerScreen.kt` (extrait)

C'est ici que l'état est construit et que les actions sont branchées sur le
ViewModel. Trois points à retenir :

- La **variante** est choisie par le slot : `8 -> Drum`, `9 -> Pad`, sinon `Track`.
- Le **nom du soundfont** vient de `tracks[i].soundfontName` pour 0..7, et de
  `audioSlots[slot].soundFontPath` pour 8 et 9 (piège n°1).
- Les **banques disponibles** sont dérivées des presets chargés
  (`presets.map { it.bankNumber }.distinct().sorted()`).

### `MixerScreen.kt — construction de l'état + actions soundfont`

- **Source :** `app/src/main/java/com/soundstage/mixer/ui/MixerScreen.kt`, lignes **960–1100**
- **Taille de l'extrait :** 6813 octets · **lignes :** 141
- Extrait centré sur l'appel à `SoundfontWindow`.

````kotlin
            }
        }
    }

        when (uiState.activePopup) {
            ActivePopup.EFFECTS -> {
                // The Æther FX window is the only Effects surface. The legacy
                // EffectsDialog is gone: FxOverlay renders FxWindowHost, the
                // ported shell (rack, effect window, add panel).
                val slotIdx = if (uiState.activeEffectTrackId == 0) null else uiState.activeEffectTrackId - 1
                val sfTrack = slotIdx?.let { uiState.tracks.getOrNull(it) }
                com.soundstage.mixer.ui.fx.FxOverlay(
                    trackId = uiState.activeEffectTrackId,
                    soundfontName = sfTrack?.soundfontName.orEmpty(),
                    program = sfTrack?.program ?: 0,
                    onClose = { viewModel.closePopup() },
                    onChooseSoundfont = {
                        if (uiState.activeEffectTrackId in 1..8) {
                            viewModel.openSoundfontForSlot(uiState.activeEffectTrackId - 1)
                        }
                    },
                    modifier = Modifier.fillMaxSize().zIndex(130f),
                )
            }
            ActivePopup.SOUNDFONT -> {
                val slot = uiState.audioSlots.getOrNull(uiState.activeSoundfontSlotId)
                val activePresets = slot?.presets ?: emptyList()
                val activePresetId = slot?.preset ?: 0
                // tracks a 8 elements (0..7) : les slots 8 (Drum) et 9 (Pad) n'y ont pas
                // d'entree, getOrNull renvoie null. Le nom fiable est soundFontPath (ecrit
                // pour tous les slots), comme le fait deja DrumPadScreen.
                val activeSfName = if (uiState.activeSoundfontSlotId in 0..7) {
                    uiState.tracks.getOrNull(uiState.activeSoundfontSlotId)?.soundfontName.orEmpty()
                } else {
                    slot?.soundFontPath?.substringAfterLast("/").orEmpty()
                }
                val activeBank = slot?.bank ?: 0

                val variant = when (uiState.activeSoundfontSlotId) {
                    8 -> SoundfontVariant.Drum
                    9 -> SoundfontVariant.Pad
                    else -> SoundfontVariant.Track
                }

                val loadedFileId = activeSfName.ifEmpty { null }

                val availableBanks = remember(activePresets) {
                    activePresets.map { it.bankNumber }.distinct().sorted()
                }

                val sfWindowState = SoundfontWindowState(
                    isOpen = true,
                    variant = variant,
                    loadedFileId = loadedFileId,
                    loadedFileName = activeSfName,
                    loadedPresets = activePresets,
                    loadedBank = activeBank,
                    loadedPresetId = activePresetId,
                    loadedBanks = availableBanks,
                    files = uiState.realSoundfonts.map { item ->
                        val parsed = uiState.sfParsedPresets[item.path]
                        SfRow.File(
                            key = item.name,
                            name = item.name,
                            sizeBytes = item.size,
                            formattedSize = item.formattedSize,
                            isLoaded = item.name == activeSfName,
                            presetCount = parsed?.size ?: 0,
                        )
                    },
                    favorites = uiState.sfFavorites,
                    overrides = uiState.sfCategoryOverrides,
                    parsedPresets = uiState.sfParsedPresets.mapValues { (_, v) ->
                        v.map { SoundfontPreset(it.preset, it.displayName, it.bank) }
                    },
                    parsingFiles = uiState.sfParsingFiles,
                    loadingFileId = null,
                    loadingProgress = null,
                    importingName = null,
                    importingProgress = null,
                )

                SoundfontWindow(
                    state = sfWindowState,
                    actions = object : SfWindowActions {
                        override fun onClose() { viewModel.closePopup() }

                        override fun onSelectPreset(row: SfRow.Preset) {
                            viewModel.selectSf2Preset(
                                SoundfontPreset(row.presetId, row.name, row.bankNumber)
                            )
                        }

                        override fun onLoadFile(fileId: String) {
                            val item = uiState.realSoundfonts.firstOrNull { it.name == fileId } ?: return
                            viewModel.loadSoundfontFromStorage(item)
                        }

                        override fun onCancelLoad() {}

                        override fun onToggleFavorite(key: String) {
                            viewModel.toggleSfFavorite(key)
                        }

                        override fun onMoveToCategory(key: String, categoryId: String) {
                            viewModel.moveSfToCategory(key, categoryId)
                        }

                        override fun onImportFile() {
                            sf2PickerLauncher.launch(arrayOf("*/*"))
                        }

                        override fun onDeleteFile(fileId: String) {
                            val item = uiState.realSoundfonts.firstOrNull { it.name == fileId } ?: return
                            viewModel.deleteSoundFontFile(item)
                        }

                        override fun onRequestParse(fileId: String) {
                            val item = uiState.realSoundfonts.firstOrNull { it.name == fileId } ?: return
                            viewModel.parseSfInBackground(item.path)
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(120f)
                )
            }
            ActivePopup.TRACK_SETTINGS -> {
                val targetTrack = uiState.tracks.find { it.id == uiState.activeTrackSettingsId }
                if (targetTrack != null) {
                    // Dimming scrim: taps outside the window dismiss it, and it swallows
                    // every touch so nothing behind reacts.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x66000000))
                            .zIndex(119f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { viewModel.closeTrackSettings() }

````

---

## 4. Fonctions soundfont du ViewModel (extraits)

`MixerViewModel.kt` fait **320 Ko** : il n'est pas reproduit en entier. Voici le
sous-ensemble qui concerne la fenêtre soundfonts, avec ses bornes exactes.

| Fonction | Rôle |
|---|---|
| `openSoundfontForSlot` | Ouvre la fenêtre sur un slot donné |
| `setSf2Tab` | Change d'onglet/catégorie |
| `loadSoundFontForSlot` | **Le chargement** : volume anti-pop, lecture des presets, restauration |
| `selectSf2Preset` | Program change vers le moteur |
| `loadSoundfontFromStorage` | Charge le fichier du slot actif |
| `unloadSoundFontFromSlot` | Décharge |
| `deleteSoundFontFile` | Supprime le fichier |
| `toggleSfFavorite` | Favori (persisté) |
| `moveSfToCategory` | Déplacement manuel de catégorie (persisté) |
| `parseSfInBackground` | Parse un `.sf2` hors du thread UI |
| `garbageCollectUnusedSoundFonts` | Libère les soundfonts non utilisés |
| `assignDrumSf2Note` | Assigne un preset à une note du DrumPad |

### `openSoundfontForSlot`

- **Source :** `app/src/main/java/com/soundstage/mixer/viewmodel/MixerViewModel.kt`, lignes **2876–2886**
- **Taille de l'extrait :** 362 octets · **lignes :** 11

````kotlin
    fun openSoundfontForSlot(slotId: Int) {
        audioEngine.activeTargetChannel = AudioSlot.midiChannelForSlot(slotId)
        _uiState.update {
            it.copy(
                activePopup = ActivePopup.SOUNDFONT,
                activeSoundfontSlotId = slotId
            )
        }
    }

    // ================= DRUM PAD CONTROLS =================

````

### `assignDrumSf2Note`

- **Source :** `app/src/main/java/com/soundstage/mixer/viewmodel/MixerViewModel.kt`, lignes **3069–3086**
- **Taille de l'extrait :** 631 octets · **lignes :** 18

````kotlin
    fun assignDrumSf2Note(padId: Int, key: String, octave: Int) {
        _uiState.update { state ->
            val updated = state.drumPads.map { pad ->
                if (pad.id == padId) {
                    pad.copy(
                        soundType = DrumSoundType.SF2_NOTE,
                        sf2Note = "$key$octave",
                        sf2NoteOctave = octave,
                        sf2NoteKey = key,
                        label = "$key$octave"
                    )
                } else pad
            }
            state.copy(drumPads = updated)
        }
        persistCurrentStateDebounced()
    }


````

### `setSf2Tab`

- **Source :** `app/src/main/java/com/soundstage/mixer/viewmodel/MixerViewModel.kt`, lignes **3312–3315**
- **Taille de l'extrait :** 96 octets · **lignes :** 4

````kotlin
    fun setSf2Tab(tab: String) {
        _uiState.update { it.copy(activeSf2Tab = tab) }
    }


````

### `loadSoundFontForSlot`

- **Source :** `app/src/main/java/com/soundstage/mixer/viewmodel/MixerViewModel.kt`, lignes **3320–3512**
- **Taille de l'extrait :** 10725 octets · **lignes :** 193
- Le chemin critique. **Le volume est restauré dans un `finally`** : un SF2 corrompu laissait sinon le volume à zéro.

````kotlin
    fun loadSoundFontForSlot(slotId: Int, sf2Path: String, bank: Int = 0, preset: Int = 0, patchName: String? = null, saveAfterLoad: Boolean = true) {
        val slot = _uiState.value.audioSlots.getOrNull(slotId) ?: return
        val targetChannel = AudioSlot.midiChannelForSlot(slotId)

        viewModelScope.launch(Dispatchers.IO) {
            soundFontLoadMutex.withLock {
                val oldSfId = slot.soundFontId

                // Anti-pop / Anti-crack: smoothly silence the channel and all notes before swapping soundfonts
                // to prevent DC offset pulse or abrupt buffer discontinuity ("TACK")
                NativeAudioBridge.safeAllNotesOff(targetChannel)
                NativeAudioBridge.safeSetTrackVolume(targetChannel, 0f)

                // The channel is silenced above and only restored at the end of the
                // load. If anything throws in between (an unreadable SF2, a native
                // bridge error), the old code left the track at 0 and the user saw
                // the fader pinned to the bottom with no way to hear it. The body is
                // wrapped so the volume is always re-applied from state on the way
                // out, success or not.
                try {

                Log.d("SoundFontLoad", "[DIAGNOSTIC] Requested load for slot=$slotId, raw sf2Path=$sf2Path")

                // Ensure file path is accessible by native C++ fopen (bridge external storage files if needed)
                val nativeReadableFile = fileManager.getNativeReadableSoundFontFile(sf2Path)
                val readablePath = nativeReadableFile.absolutePath
                Log.d("SoundFontLoad", "[DIAGNOSTIC] Resolved native-readable path=$readablePath (exists=${nativeReadableFile.exists()}, length=${nativeReadableFile.length()})")

                // 1. Charger d'abord le nouveau SoundFont via le chemin natif garanti sur le moteur unifié
                val newSfId = NativeAudioBridge.safeLoadSoundFont(NativeAudioBridge.ENGINE_FADER, readablePath)
                Log.d("SoundFontLoad", "[DIAGNOSTIC] Native safeLoadSoundFont returned ID=$newSfId for slot=$slotId")

                // 2. Décharger l'ancien UNIQUEMENT si le nouveau a réussi et que l'ancien n'est plus utilisé nulle part
                if (newSfId >= 0 && oldSfId > 0 && oldSfId != newSfId) {
                    val inUse = _uiState.value.audioSlots.any { it.slotId != slotId && it.soundFontId == oldSfId }
                    if (!inUse) {
                        NativeAudioBridge.safeUnloadSoundFont(NativeAudioBridge.ENGINE_FADER, oldSfId)
                    }
                }

                // 3. Récupérer la liste des presets du SoundFont chargé en bornant strictly 0..127 par banque
                val effectiveSfId = if (newSfId >= 0) newSfId else oldSfId
                val nativePresets = if (effectiveSfId >= 0 && NativeAudioBridge.isNativeReady()) {
                    NativeAudioBridge.safeListPresets(effectiveSfId)
                } else emptyList()

                val realPresets = if (nativePresets.isNotEmpty()) {
                    nativePresets
                        .filter { info ->
                            val lower = info.name.trim().lowercase()
                            info.preset in 0..127 && info.bank >= 0 &&
                                !lower.contains("unknown") &&
                                !lower.contains("ghost") &&
                                !lower.startsWith("unused") &&
                                !lower.startsWith("null")
                        }
                        .distinctBy { Pair(it.bank, it.preset) }
                        .sortedWith(compareBy({ it.bank }, { it.preset }))
                        .map { info ->
                            val cleanName = info.name.trim().ifEmpty { "Preset ${info.preset + 1}" }
                            SoundfontPreset(
                                id = info.preset,
                                name = cleanName,
                                bankNumber = info.bank
                            )
                        }
                } else {
                    val file = File(sf2Path)
                    if (file.exists() && file.canRead()) {
                        val parsed = SF2Parser.parsePresets(file)
                        if (parsed.isNotEmpty()) {
                            parsed.map { p ->
                                SoundfontPreset(
                                    id = p.preset,
                                    name = p.displayName,
                                    bankNumber = p.bank
                                )
                            }
                        } else emptyList()
                    } else emptyList()
                }

                val targetPreset = realPresets.find { it.bankNumber == bank && it.id == preset }
                    ?: realPresets.firstOrNull()
                    ?: SoundfontPreset(preset, patchName ?: File(sf2Path).name.removeSuffix(".sf2"), bank)

                // 4. Appeler safeSelectProgram avec le soundFontId obtenu, le bank, et le preset demandés
                audioEngine.setChannelProgram(targetChannel, targetPreset.id, targetPreset.bankNumber)
                if (effectiveSfId >= 0) {
                    NativeAudioBridge.safeSelectProgram(
                        engineIndex = NativeAudioBridge.ENGINE_FADER,
                        channel = targetChannel,
                        soundFontId = effectiveSfId,
                        bank = targetPreset.bankNumber,
                        preset = targetPreset.id
                    )
                }

                // Sync channel volume immediately so fader movement is not required to
                // hear sound. Same rule as the finally block below (disabled and muted
                // both mean silence) so the two cannot disagree.
                val targetTrackNow = _uiState.value.tracks.getOrNull(slotId)
                val effectiveVol = when {
                    targetTrackNow == null -> 0.8f
                    !targetTrackNow.isEnabled -> 0f
                    targetTrackNow.isMuted -> 0f
                    else -> targetTrackNow.volume
                }
                audioEngine.setChannelVolume(targetChannel, effectiveVol)
                NativeAudioBridge.safeSetTrackVolume(targetChannel, effectiveVol)

                // Track Settings' "defaults at load" are applied when the user moves
                // those sliders, not here. Re-applying them on every soundfont load
                // overwrote the live fader: defaultVolume is 100 (0.787) while the
                // normal fader is 0.85, so every load silently pulled track 1 down and
                // the user had to nudge a snapshot to get the level back. Pan is still
                // restored here because it has no separate live fader write.
                _uiState.value.tracks.getOrNull(slotId)?.let { t ->
                    NativeAudioBridge.safeSetTrackPan(targetChannel, (t.defaultPan.coerceIn(-64, 63)) / 63f)
                }

                // Re-validate program selection for all other active audio slots to ensure complete isolation
                val currentSlots = _uiState.value.audioSlots
                for (s in currentSlots) {
                    if (s.slotId != slotId && s.soundFontId > 0) {
                        NativeAudioBridge.safeSelectProgram(
                            engineIndex = NativeAudioBridge.ENGINE_FADER,
                            channel = s.midiChannel,
                            soundFontId = s.soundFontId,
                            bank = s.bank,
                            preset = s.preset
                        )
                    }
                }

                // 5 & 6. Mettre à jour audioSlots[slotId] et notifier l'UI
                val sfName = File(sf2Path).name
                _uiState.update { state ->
                    val updatedSlots = state.audioSlots.map { s ->
                        if (s.slotId == slotId) {
                            s.copy(
                                soundFontId = effectiveSfId,
                                soundFontPath = sf2Path,
                                patchName = targetPreset.name,
                                bank = targetPreset.bankNumber,
                                preset = targetPreset.id,
                                presets = realPresets
                            )
                        } else s
                    }
                    val updatedTracks = if (slotId in 0..7) {
                        state.tracks.mapIndexed { idx, t ->
                            if (idx == slotId) {
                                t.copy(
                                    soundfontName = sfName,
                                    patchName = targetPreset.name,
                                    bank = targetPreset.bankNumber,
                                    program = targetPreset.id
                                )
                            } else t
                        }
                    } else state.tracks

                    val newTonicPadName = if (slotId == 9) targetPreset.name else state.activeTonicPadName
                    state.copy(
                        audioSlots = updatedSlots,
                        tracks = updatedTracks,
                        activeTonicPadName = newTonicPadName
                    )
                }
                if (saveAfterLoad) {
                    persistCurrentStateDebounced()
                }
                } catch (t: Throwable) {
                    Log.e("SoundFontLoad", "Load failed for slot=$slotId: ${t.message}", t)
                } finally {
                    // Always re-apply the live fader volume: the channel was forced to
                    // 0 before the swap, and a failure must not leave it there. Read
                    // the current state so a load that happened while the user was
                    // already fading still lands on the right level. Slot 9 (TonicPad)
                    // has no TrackChannel, so its level comes from the audio slot.
                    val track = _uiState.value.tracks.getOrNull(slotId)
                    val vol = when {
                        track != null -> if (!track.isEnabled || track.isMuted) 0f else track.volume
                        else -> _uiState.value.audioSlots.getOrNull(slotId)?.volume ?: 0.8f
                    }
                    audioEngine.setChannelVolume(targetChannel, vol)
                    NativeAudioBridge.safeSetTrackVolume(targetChannel, vol)
                }
            }
        }
    }


````

### `selectSf2Preset`

- **Source :** `app/src/main/java/com/soundstage/mixer/viewmodel/MixerViewModel.kt`, lignes **3513–3519**
- **Taille de l'extrait :** 330 octets · **lignes :** 7

````kotlin
    fun selectSf2Preset(presetId: Int) {
        val slotId = _uiState.value.activeSoundfontSlotId
        val slot = _uiState.value.audioSlots.getOrNull(slotId) ?: return
        val p = slot.presets.find { it.id == presetId } ?: SoundfontPreset(presetId, "Preset $presetId", slot.bank)
        selectSf2Preset(slotId, p)
    }


````

### `loadSoundfontFromStorage`

- **Source :** `app/src/main/java/com/soundstage/mixer/viewmodel/MixerViewModel.kt`, lignes **3572–3575**
- **Taille de l'extrait :** 139 octets · **lignes :** 4

````kotlin
    fun loadSoundfontFromStorage(file: StorageItem) {
        loadSoundFontForSlot(_uiState.value.activeSoundfontSlotId, file.path)
    }


````

### `unloadSoundFontFromSlot`

- **Source :** `app/src/main/java/com/soundstage/mixer/viewmodel/MixerViewModel.kt`, lignes **3576–3631**
- **Taille de l'extrait :** 2396 octets · **lignes :** 56

````kotlin
    fun unloadSoundFontFromSlot(slotId: Int = _uiState.value.activeSoundfontSlotId) {
        val slot = _uiState.value.audioSlots.getOrNull(slotId) ?: return
        val targetChannel = AudioSlot.midiChannelForSlot(slotId)
        val oldSfId = slot.soundFontId

        viewModelScope.launch(Dispatchers.IO) {
            soundFontLoadMutex.withLock {
                // Cut any playing notes on this channel
                audioEngine.allNotesOff()

                // Safe native unload if not used by another slot
                if (oldSfId > 0) {
                    val inUse = _uiState.value.audioSlots.any { it.slotId != slotId && it.soundFontId == oldSfId }
                    if (!inUse) {
                        NativeAudioBridge.safeUnloadSoundFont(NativeAudioBridge.ENGINE_FADER, oldSfId)
                        Log.d("SoundFontUnload", "Freed SoundFont ID=$oldSfId from RAM for slot $slotId")
                    }
                }

                _uiState.update { state ->
                    val updatedSlots = state.audioSlots.map { s ->
                        if (s.slotId == slotId) {
                            s.copy(
                                soundFontId = -1,
                                soundFontPath = "",
                                patchName = "No Instrument",
                                bank = 0,
                                preset = 0,
                                presets = emptyList()
                            )
                        } else s
                    }
                    val updatedTracks = if (slotId in 0..7) {
                        state.tracks.mapIndexed { idx, t ->
                            if (idx == slotId) {
                                t.copy(
                                    soundfontName = "",
                                    patchName = "No Instrument",
                                    bank = 0,
                                    program = 0
                                )
                            } else t
                        }
                    } else state.tracks

                    state.copy(
                        audioSlots = updatedSlots,
                        tracks = updatedTracks
                    )
                }
                persistCurrentStateDebounced()
                System.gc() // Reclaim memory
            }
        }
    }


````

### `deleteSoundFontFile`

- **Source :** `app/src/main/java/com/soundstage/mixer/viewmodel/MixerViewModel.kt`, lignes **3632–3661**
- **Taille de l'extrait :** 1266 octets · **lignes :** 30

````kotlin
    fun deleteSoundFontFile(file: StorageItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val f = File(file.path)
                if (f.exists()) {
                    f.delete()
                }

                // If any slot was using this soundfont, unload it
                _uiState.value.audioSlots.forEach { slot ->
                    if (slot.soundFontPath == file.path || slot.soundFontPath?.contains(file.name) == true) {
                        unloadSoundFontFromSlot(slot.slotId)
                    }
                }

                // Instantly filter out from UI lists
                _uiState.update { state ->
                    val updatedList = state.realSoundfonts.filterNot { it.path == file.path || it.name == file.name }
                    val updatedBankFiles = state.soundfontBankFiles.filterNot { it.path == file.path || it.name == file.name }
                    state.copy(
                        realSoundfonts = updatedList,
                        soundfontBankFiles = updatedBankFiles
                    )
                }
            } catch (e: Exception) {
                Log.e("MixerViewModel", "Error deleting soundfont file: ${e.message}", e)
            }
        }
    }


````

### `toggleSfFavorite`

- **Source :** `app/src/main/java/com/soundstage/mixer/viewmodel/MixerViewModel.kt`, lignes **3662–3669**
- **Taille de l'extrait :** 287 octets · **lignes :** 8

````kotlin
    fun toggleSfFavorite(key: String) {
        _uiState.update { state ->
            val next = if (state.sfFavorites.contains(key)) state.sfFavorites - key else state.sfFavorites + key
            state.copy(sfFavorites = next)
        }
        persistCurrentStateDebounced()
    }


````

### `moveSfToCategory`

- **Source :** `app/src/main/java/com/soundstage/mixer/viewmodel/MixerViewModel.kt`, lignes **3670–3676**
- **Taille de l'extrait :** 245 octets · **lignes :** 7

````kotlin
    fun moveSfToCategory(key: String, categoryId: String) {
        _uiState.update { state ->
            state.copy(sfCategoryOverrides = state.sfCategoryOverrides + (key to categoryId))
        }
        persistCurrentStateDebounced()
    }


````

### `parseSfInBackground`

- **Source :** `app/src/main/java/com/soundstage/mixer/viewmodel/MixerViewModel.kt`, lignes **3677–3700**
- **Taille de l'extrait :** 1032 octets · **lignes :** 24

````kotlin
    fun parseSfInBackground(filePath: String) {
        val state = _uiState.value
        if (state.sfParsedPresets.containsKey(filePath)) return
        if (state.sfParsingFiles.contains(filePath)) return
        _uiState.update { it.copy(sfParsingFiles = it.sfParsingFiles + filePath) }
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(filePath)
            val parsed = try {
                kotlinx.coroutines.withTimeoutOrNull(2_500L) {
                    com.soundstage.mixer.model.SF2Parser.parsePresets(file)
                } ?: emptyList()
            } catch (e: Exception) {
                Log.w("MixerViewModel", "SF2 parse failed for $filePath: ${e.message}")
                emptyList()
            }
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(
                    sfParsedPresets = it.sfParsedPresets + (filePath to parsed),
                    sfParsingFiles = it.sfParsingFiles - filePath,
                ) }
            }
        }
    }


````

### `garbageCollectUnusedSoundFonts`

- **Source :** `app/src/main/java/com/soundstage/mixer/viewmodel/MixerViewModel.kt`, lignes **4520–4556**
- **Taille de l'extrait :** 1840 octets · **lignes :** 37

````kotlin
    fun garbageCollectUnusedSoundFonts() {
        val state = _uiState.value
        val visibleTracks = state.tracks.take(state.visibleTracksCount)
        val activeSoundfontNames = visibleTracks.filter { it.isEnabled && it.soundfontName.isNotEmpty() }
            .map { it.soundfontName }
            .toSet()

        val activeSfPaths = activeSoundfontNames.mapNotNull { name ->
            val f = File(fileManager.soundfontsDir, name)
            if (f.exists()) f.absolutePath else null
        }.toSet()

        val activeSfIds = mutableSetOf<Int>()
        state.audioSlots.forEachIndexed { idx, slot ->
            if (idx < state.visibleTracksCount && visibleTracks.getOrNull(idx)?.isEnabled == true) {
                if (slot.soundFontId > 0 && !slot.soundFontPath.isNullOrEmpty() && activeSfPaths.contains(slot.soundFontPath)) {
                    activeSfIds.add(slot.soundFontId)
                }
            }
        }

        // Unload any slot soundfonts that are not in activeSfIds
        _uiState.update { current ->
            val updatedSlots = current.audioSlots.mapIndexed { idx, slot ->
                if (idx >= current.visibleTracksCount || visibleTracks.getOrNull(idx)?.isEnabled != true) {
                    if (slot.soundFontId > 0 && !activeSfIds.contains(slot.soundFontId)) {
                        NativeAudioBridge.safeUnloadSoundFont(NativeAudioBridge.ENGINE_FADER, slot.soundFontId)
                        Log.d("SoundFontUnload", "Garbage collected unused soundfont ID=${slot.soundFontId} from RAM")
                    }
                    slot.copy(soundFontId = -1, soundFontPath = null, patchName = null, presets = emptyList())
                } else slot
            }
            current.copy(audioSlots = updatedSlots)
        }
        NativeAudioBridge.safeTrimMemory()
    }


````

### `importSoundFontUri / importSoundFontUris`

- **Source :** `app/src/main/java/com/soundstage/mixer/viewmodel/MixerViewModel.kt`, lignes **1289–1300**
- **Taille de l'extrait :** 618 octets · **lignes :** 12
- Entrée de l'import (sélecteur de fichier système).

````kotlin
    fun importSoundFontUri(uri: android.net.Uri, targetSlotId: Int? = null) {
        importSoundFontUris(listOf(uri), targetSlotId)
    }

    fun importSoundFontUris(uris: List<android.net.Uri>, targetSlotId: Int? = null) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val importedFiles = fileManager.importMultipleSoundFonts(uris)
            if (importedFiles.isNotEmpty()) {
                refreshStorageFiles()
                val slotToLoad = targetSlotId ?: _uiState.value.activeSoundfontSlotId
                importedFiles.firstOrNull()?.let { firstFile ->

````

---

## 5. Le moteur et les modèles

### `SF2Parser.kt` (fichier intégral)

Lit un `.sf2` et en extrait les presets (id, nom, banque). C'est ce qui alimente
les onglets de catégories et la liste des presets.

### `SF2Parser.kt`

- **Chemin :** `app/src/main/java/com/soundstage/mixer/model/SF2Parser.kt`
- **Taille :** 7848 octets · **lignes :** 205 · **sha256 (12) :** `a6dd2cadaefe`

````kotlin
package com.soundstage.mixer.model

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Metadata for a single SoundFont 2 (SF2) Preset extracted from binary RIFF headers.
 */
data class SF2PresetInfo(
    val presetIndex: Int,
    val name: String,
    val bank: Int,
    val preset: Int
) {
    val displayName: String get() = SF2Parser.cleanPresetName(name)
}

/**
 * Pure Kotlin, high-performance binary parser for SoundFont 2 (.sf2) files.
 * Directly navigates RIFF chunks to find the 'pdta' (preset data) list
 * and parses ALL 'phdr' (Preset Header) chunk records.
 */
object SF2Parser {

    private const val TAG = "SF2Parser"

    fun cleanPresetName(rawName: String): String {
        var name = rawName.trim { it <= ' ' || it == '\u0000' }
        // Clean bracketed bank/preset markers like [000:000] if present
        name = name.replace("\\[\\d+:\\d+\\]".toRegex(), "").trim()
        if (name.isBlank()) {
            name = "Instrument"
        }
        return name
    }

    suspend fun parsePresets(sf2File: File): List<SF2PresetInfo> = withContext(Dispatchers.IO) {
        val presets = mutableListOf<SF2PresetInfo>()
        if (!sf2File.exists() || !sf2File.canRead() || sf2File.length() < 128) {
            return@withContext presets
        }

        var raf: RandomAccessFile? = null
        try {
            raf = RandomAccessFile(sf2File, "r")
            val fileLength = raf.length()

            // 1. Verify RIFF header
            val headerBytes = ByteArray(12)
            raf.readFully(headerBytes)
            val headerBuffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)

            val riffId = readString(headerBuffer, 4)
            if (riffId != "RIFF") {
                Log.w(TAG, "Not a valid RIFF file: ${sf2File.name}")
                return@withContext fallbackSinglePreset(sf2File)
            }
            val riffSize = headerBuffer.int
            val sfbkId = readString(headerBuffer, 4)
            if (sfbkId != "sfbk") {
                Log.w(TAG, "Not a valid sfbk SoundFont file: ${sf2File.name}")
                return@withContext fallbackSinglePreset(sf2File)
            }

            // 2. Scan top-level chunks to find LIST of type 'pdta'
            var phdrOffset: Long = -1L
            var phdrSize: Long = 0L

            while (raf.filePointer < fileLength - 8) {
                val chunkIdBytes = ByteArray(4)
                raf.readFully(chunkIdBytes)
                val chunkId = String(chunkIdBytes, Charsets.US_ASCII)
                val chunkSize = readUInt32(raf)
                val paddedChunkSize = if (chunkSize % 2L != 0L) chunkSize + 1L else chunkSize
                val chunkEnd = (raf.filePointer + paddedChunkSize).coerceAtMost(fileLength)

                if (chunkId == "LIST") {
                    val listTypeBytes = ByteArray(4)
                    raf.readFully(listTypeBytes)
                    val listType = String(listTypeBytes, Charsets.US_ASCII)

                    if (listType == "pdta") {
                        // Scan sub-chunks inside pdta to locate 'phdr'
                        while (raf.filePointer < chunkEnd - 8) {
                            val prevPos = raf.filePointer
                            val subIdBytes = ByteArray(4)
                            raf.readFully(subIdBytes)
                            val subId = String(subIdBytes, Charsets.US_ASCII)
                            val subSize = readUInt32(raf)
                            val paddedSubSize = if (subSize % 2L != 0L) subSize + 1L else subSize

                            if (subId == "phdr") {
                                phdrOffset = raf.filePointer
                                phdrSize = subSize
                                break
                            } else {
                                val nextPos = (raf.filePointer + paddedSubSize).coerceAtMost(chunkEnd)
                                if (nextPos <= prevPos) {
                                    // Safeguard: force advance to avoid infinite loop
                                    raf.seek((prevPos + 8).coerceAtMost(chunkEnd))
                                } else {
                                    raf.seek(nextPos)
                                }
                            }
                        }
                        break
                    } else {
                        raf.seek(chunkEnd)
                    }
                } else {
                    raf.seek(chunkEnd)
                }
            }

            // 3. Parse records in phdr chunk
            if (phdrOffset > 0 && phdrSize >= 38) {
                raf.seek(phdrOffset)
                val recordCount = (phdrSize / 38).toInt()
                val recordBuffer = ByteArray(38)

                for (i in 0 until recordCount) {
                    raf.readFully(recordBuffer)
                    val buf = ByteBuffer.wrap(recordBuffer).order(ByteOrder.LITTLE_ENDIAN)

                    // achPresetName: 20 bytes null-terminated ASCII
                    val nameBytes = ByteArray(20)
                    buf.get(nameBytes)
                    val rawName = String(nameBytes, Charsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }

                    val wPreset = buf.short.toInt() and 0xFFFF
                    val wBank = buf.short.toInt() and 0xFFFF
                    val wPresetBagNdx = buf.short.toInt() and 0xFFFF
                    val dwLibrary = buf.int
                    val dwGenre = buf.int
                    val dwMorphology = buf.int

                    // SF2 spec: The terminal record is always named "EOP" (End of Presets)
                    if (rawName.equals("EOP", ignoreCase = true)) {
                        break
                    }

                    if (rawName.isNotEmpty()) {
                        val cleaned = cleanPresetName(rawName)
                        presets.add(
                            SF2PresetInfo(
                                presetIndex = presets.size,
                                name = cleaned,
                                bank = wBank,
                                preset = wPreset
                            )
                        )
                    }
                }
            }

            if (presets.isEmpty()) {
                fallbackSinglePreset(sf2File)
            } else {
                presets.sortedWith(compareBy({ it.bank }, { it.preset }))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing SF2 binary presets: ${e.message}", e)
            fallbackSinglePreset(sf2File)
        } finally {
            try {
                raf?.close()
            } catch (_: Exception) {}
        }
    }

    private fun fallbackSinglePreset(file: File): List<SF2PresetInfo> {
        val cleanName = file.nameWithoutExtension.ifEmpty { file.name }
        return listOf(
            SF2PresetInfo(
                presetIndex = 0,
                name = cleanName,
                bank = 0,
                preset = 0
            )
        )
    }

    private fun readString(buf: ByteBuffer, length: Int): String {
        val bytes = ByteArray(length)
        buf.get(bytes)
        return String(bytes, Charsets.US_ASCII)
    }

    private fun readUInt32(raf: RandomAccessFile): Long {
        val b0 = raf.read()
        val b1 = raf.read()
        val b2 = raf.read()
        val b3 = raf.read()
        if ((b0 or b1 or b2 or b3) < 0) return 0L
        return ((b0 and 0xFF) or
                ((b1 and 0xFF) shl 8) or
                ((b2 and 0xFF) shl 16) or
                ((b3 and 0xFF) shl 24)).toLong() and 0xFFFFFFFFL
    }
}

````

### Modèles partagés — `MixerState.kt`

`SoundfontPreset`, `AudioSlot` (avec son `presets`), `TrackChannel` (son
`soundfontName`), `SoundfontBankFile`. **Fichier intégral** : il est petit et sert
partout.

### `MixerState.kt`

- **Chemin :** `app/src/main/java/com/soundstage/mixer/model/MixerState.kt`
- **Taille :** 11469 octets · **lignes :** 320 · **sha256 (12) :** `7edccb1fc7db`

````kotlin
package com.soundstage.mixer.model

import androidx.compose.ui.graphics.Color
import com.soundstage.mixer.ui.theme.NeonCyan
import com.soundstage.mixer.ui.theme.NeonMagenta
import com.soundstage.mixer.ui.theme.MuteRed
import com.soundstage.mixer.ui.theme.SoloAmber
import com.soundstage.mixer.ui.components.VelocityConfig

enum class ActivePopup {
    NONE, DRUM_PAD, TONIC_PAD, NOTES, SCENE, EFFECTS, SOUNDFONT, STYLE, MIDI, TRACK_SETTINGS, VELOCITY_SETTINGS;

    /**
     * True for popups drawn as a centred floating window over the current page, which dim and
     * blur the page behind them. Inline panels (keyboard bar, notes) are not windows.
     */
    fun isFloatingWindow(): Boolean = when (this) {
        SOUNDFONT, EFFECTS, SCENE, TRACK_SETTINGS, VELOCITY_SETTINGS -> true
        else -> false
    }
}

enum class AppScreenPage {
    PROJECTS, MIXER, DRUMPAD, SHEETS, SETTINGS, SETLISTS
}

enum class AppLanguage(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    FRENCH("fr", "Français"),
    SPANISH("es", "Español")
}

enum class SoundGoodizerMode(val label: String, val description: String) {
    A("A - Warm Tube", "Analog warmth & round harmonic saturation"),
    B("B - Crisp Air", "Brilliant high exciter & punchy dynamics"),
    C("C - Deep Crunch", "Heavy multiband compression & bass crunch"),
    D("D - Hard Limiter", "Maximum impact, brickwall & punchy loudness")
}

enum class AppTheme(val displayName: String, val description: String) {
    CYBER_NEON("Cyber Neon (Default)", "Dark canvas with cyan & violet neon accents"),
    OBSIDIAN_GOLD("Obsidian Gold", "Deep black with luxury amber & gold accents"),
    TOKYO_NIGHT("Tokyo Night", "Deep night blue with magenta & indigo touches"),
    STUDIO_SLATE("Studio Slate", "Clean professional studio slate gray"),
    OLED_BLACK("OLED Pure Black", "Absolute pitch black for maximum contrast")
}

enum class DrumSoundType {
    SAMPLE, SF2_NOTE
}

enum class DrumPadCategory(val title: String) {
    GRADIENT("GRADIENT"),
    LED("LED"),
    NEON("NEON"),
    DYNAMIC_TONAL("DYNAMIC TONAL")
}

enum class DrumPadStyle(
    val displayName: String,
    val category: DrumPadCategory,
    val primaryColor: Color,
    val secondaryColor: Color
) {
    // GRADIENT
    GRADIENT_CYAN("Cyan Profond", DrumPadCategory.GRADIENT, Color(0xFF00E5FF), Color(0xFF0052CC)),
    GRADIENT_SUNSET("Sunset Lave", DrumPadCategory.GRADIENT, Color(0xFFFF3D00), Color(0xFFD50000)),
    GRADIENT_PURPLE("Violet Galaxie", DrumPadCategory.GRADIENT, Color(0xFFD500F9), Color(0xFF651FFF)),
    GRADIENT_GOLD("Or Impérial", DrumPadCategory.GRADIENT, Color(0xFFFFD700), Color(0xFFB8860B)),
    GRADIENT_EMERALD("Émeraude Lux", DrumPadCategory.GRADIENT, Color(0xFF00E676), Color(0xFF00796B)),
    GRADIENT_CRIMSON("Crimson Dark", DrumPadCategory.GRADIENT, Color(0xFFFF1744), Color(0xFF880E4F)),

    // LED
    LED_AMBER("LED Ambre 808", DrumPadCategory.LED, Color(0xFFFFB300), Color(0xFFFF6F00)),
    LED_ICE_BLUE("LED Ice Blue", DrumPadCategory.LED, Color(0xFF38BDF8), Color(0xFF0284C7)),
    LED_MATRIX_GREEN("LED Matrix", DrumPadCategory.LED, Color(0xFF22C55E), Color(0xFF15803D)),
    LED_HOT_PINK("LED Hot Pink", DrumPadCategory.LED, Color(0xFFF43F5E), Color(0xFF9F1239)),
    LED_UV_VIOLET("LED UV", DrumPadCategory.LED, Color(0xFFA855F7), Color(0xFF6B21A8)),

    // NEON
    NEON_CYAN("Néon Laser Cyan", DrumPadCategory.NEON, Color(0xFF06B6D4), Color(0xFF0891B2)),
    NEON_LIME("Néon Acid Lime", DrumPadCategory.NEON, Color(0xFF84CC16), Color(0xFF4D7C0F)),
    NEON_MAGENTA("Néon Tokyo Rose", DrumPadCategory.NEON, Color(0xFFEC4899), Color(0xFFBE185D)),
    NEON_PURPLE("Néon Electric", DrumPadCategory.NEON, Color(0xFF8B5CF6), Color(0xFF5B21B6)),

    // HARMONIE TONALE
    MY_CORAL("Tonal Coral", DrumPadCategory.DYNAMIC_TONAL, Color(0xFFFF8A80), Color(0xFFC51162)),
    MY_TURQUOISE("Dynamic Teal", DrumPadCategory.DYNAMIC_TONAL, Color(0xFF80DEEA), Color(0xFF00695C)),
    MY_LAVENDER("Pastel Lavender", DrumPadCategory.DYNAMIC_TONAL, Color(0xFFD1C4E9), Color(0xFF512DA8)),
    MY_MINT("Mint Pistache", DrumPadCategory.DYNAMIC_TONAL, Color(0xFFA7F3D0), Color(0xFF047857)),
    MY_PEACH("Peach Blossom", DrumPadCategory.DYNAMIC_TONAL, Color(0xFFFED7AA), Color(0xFFC2410C)),

    // DUBSTEP CLUB / LIVE DRUMPAD
    DUBSTEP_CORAL("Coral Pink", DrumPadCategory.NEON, Color(0xFFFF3366), Color(0xFFFF6B97)),
    DUBSTEP_PURPLE("Neon Purple", DrumPadCategory.NEON, Color(0xFFB829D6), Color(0xFFE879F9)),
    DUBSTEP_BLUE("Vibrant Blue", DrumPadCategory.NEON, Color(0xFF1E88E5), Color(0xFF60A5FA)),
    DUBSTEP_GREEN("Acid Green", DrumPadCategory.NEON, Color(0xFF5CD626), Color(0xFF86EFAC)),
    DUBSTEP_YELLOW("Golden Sun", DrumPadCategory.NEON, Color(0xFFFBC02D), Color(0xFFFEF08A))
}

fun midiChannelForSlot(slotId: Int): Int {
    return when (slotId) {
        in 0..7 -> slotId
        8 -> 8
        9 -> 9
        else -> slotId.coerceIn(0, 15)
    }
}

data class AudioSlot(
    val slotId: Int,           // 0-7 = pistes 1-8, 8 = DrumPad, 9 = Pad
    val midiChannel: Int = midiChannelForSlot(slotId),
    val soundFontId: Int = -1,
    val soundFontPath: String? = null,
    val bank: Int = 0,
    val preset: Int = 0,
    val patchName: String? = null,
    val volume: Float = 0.8f,
    val pan: Float = 0f,
    val presets: List<SoundfontPreset> = emptyList()
) {
    companion object {
        fun midiChannelForSlot(slotId: Int): Int = com.soundstage.mixer.model.midiChannelForSlot(slotId)
    }
}

data class TrackChannel(
    val id: Int,
    val name: String,
    val isMaster: Boolean = false,
    val isEnabled: Boolean = true, // Power On/Off
    val isMuted: Boolean = false,
    val isSolo: Boolean = false,
    val volume: Float = 0.78f,
    val pan: Float = 0.0f, // -1.0f (Left) to +1.0f (Right)
    val fxSummary: String = "Fx, EQ...",
    val soundfontName: String = "",
    val patchName: String = "",
    val bank: Int = 0,
    val program: Int = 0,
    // FX Tabs per track
    val reverbPreset: String = "Concert Hall",
    val reverbMix: Float = 0.25f,
    val reverbSize: Float = 0.60f,
    val reverbDecay: Float = 0.45f,
    val velocityCurve: Float = 0.5f, // 0 = Soft, 0.5 = Linear, 1 = Hard
    val velocityConfig: VelocityConfig = VelocityConfig(),
    val splitNoteMin: Int = 24, // C1
    val splitNoteMax: Int = 108, // C7
    // Playback / performance (Track Settings window)
    val transpose: Int = 0,        // -12..+12 semitones
    val octave: Int = 0,           // -3..+3 octaves
    val pitchBendRange: Int = 2,   // 1..12 semitones at full bend
    val defaultVolume: Int = 100,  // 0..127, applied on load
    val defaultPan: Int = 0,       // -64..63, applied on load
    val sustain: Boolean = true,
    val aftertouch: Boolean = false,
    val programChange: Boolean = true,
    val midiChannel: Int = 1, // 0 = Omni, 1..16 = Ch 1-16
    val selectedMidiDeviceId: String? = null, // null = All devices
    // Per-track colour override (ARGB). null = use the default palette colour for this track index.
    val customColorArgb: Int? = null,
    // Peak meters (0f..1f)
    val peakMeterL: Float = 0.0f,
    val peakMeterR: Float = 0.0f
)

data class DrumPadItem(
    val id: Int,
    val label: String = "Pad",
    val soundType: DrumSoundType = DrumSoundType.SAMPLE,
    val sampleFileName: String = "kick_808.wav",
    val sampleFilePath: String = "",
    val sf2Note: String = "C2",
    val sf2NoteOctave: Int = 2,
    val sf2NoteKey: String = "C",
    val isPressed: Boolean = false,
    val colorStyle: DrumPadStyle = DrumPadStyle.GRADIENT_CYAN,
    val isLoopMode: Boolean = false,
    val isLoopPlaying: Boolean = false,
    val volume: Float = 1.0f,
    val loopBeatsSetting: String = "Auto",
    val customColor: Color? = null
)

data class LoopFile(
    val name: String,
    val duration: String,
    val folder: String,
    val path: String = "",
    val bpm: Int = 120,
    val musicalKey: String = "",
    val timeSignature: String = "4/4",
    val startMs: Int = 0,
    val endMs: Int = 0,
    val beats: Int = 0,
    val startStep: Int = 1,
    val endStep: Int = 16
)

data class LoopFolder(
    val name: String,
    val icon: String,
    val files: List<LoopFile>,
    val isOpen: Boolean = false
)

data class TrackSnapshot(
    val id: Int,
    val volume: Float,
    val pan: Float,
    val isMuted: Boolean = false,
    val isSolo: Boolean = false,
    val isEnabled: Boolean = true,
    val soundfontName: String = "",
    val patchName: String = "",
    val bank: Int = 0,
    val program: Int = 0,
    val transpose: Int = 0,
    val octave: Int = 0,
    val reverbSend: Float = 0f,
    val splitNoteMin: Int = 24,
    val splitNoteMax: Int = 108,
    val velocityCurve: Float = 0.5f
)

data class SubSceneSnapshot(
    val slotName: String, // "INTRO", "S2", "S3", "S4", "END"
    val tracks: List<TrackSnapshot>,
    val globalTranspose: Int = 0,
    val globalOctaveShift: Int = 0,
    val masterVolume: Float = 0.85f,
    val visibleTracksCount: Int = 1,
    val fxParameters: FxParameters? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val recallMode: String = "crossfade"
)

data class PatchItem(
    val id: String,
    val name: String,
    val tracks: List<TrackChannel> = emptyList(),
    val audioSlots: List<AudioSlot> = emptyList(),
    val visibleTracksCount: Int = 1,
    val transpose: Int = 0,
    val octave: Int = 0,
    val bpm: Int = 120,
    val masterVolume: Float = 0.85f,
    val snapshots: Map<String, SubSceneSnapshot> = emptyMap(),
    val configuredSnapshotSlots: List<String> = listOf("slot_default"),
    val snapshotCustomNames: Map<String, String> = emptyMap(),
    val activeSnapshotSlot: String? = "slot_default"
)

data class ScenePreset(
    val id: String,
    val name: String,
    val timestamp: String,
    val color: Color,
    val patchesCount: Int = 1,
    val formattedDate: String = "",
    val snapshots: Map<String, SubSceneSnapshot> = emptyMap(),
    val patches: List<PatchItem> = emptyList()
)

data class SoundfontPreset(
    val id: Int,
    val name: String,
    val bankNumber: Int
)

data class SoundfontBankFile(
    val name: String,
    val path: String,
    val size: String
)

data class MidiDeviceItem(
    val id: String,
    val name: String,
    val type: String = "USB MIDI",
    val isConnected: Boolean = true,
    val isEnabled: Boolean = true
)

data class FxParameters(
    // EQ
    val eqLow: Float = 0.5f,
    val eqMid: Float = 0.5f,
    val eqHigh: Float = 0.5f,
    val eqGain: Float = 0.5f,
    // Reverb
    val isReverbEnabled: Boolean = false,
    val reverbPreset: String = "Concert Hall",
    val reverbMix: Float = 0.24f,
    val reverbSize: Float = 0.6f,
    val reverbDecay: Float = 0.45f,
    val reverbDamp: Float = 0.3f,
    // Chorus
    val isChorusEnabled: Boolean = false,
    val chorusRate: Float = 0.35f,
    val chorusDepth: Float = 0.50f,
    val chorusMix: Float = 0.0f,
    // SoundGoodizer
    val isSgEnabled: Boolean = false,
    val sgAmount: Float = 0f,
    val sgMode: Int = 0,
    // Compressor
    val isCompEnabled: Boolean = false,
    val compThresh: Float = 0.4f,
    val compRatio: Float = 0.5f,
    val compAttack: Float = 0.2f,
    val compRelease: Float = 0.35f,
    // Delay
    val isDelayEnabled: Boolean = false,
    val delayTime: Float = 0.35f,
    val delayFeedback: Float = 0.4f,
    val delayMix: Float = 0.0f,
    val delayPingPong: Float = 0.0f
)

````

### Persistance — `AppStatePersistence.kt`

Seuls deux éléments sont persistés : `sfFavorites` et `sfCategoryOverrides`
(clés `sf_favorites` et `sf_category_overrides`). Le cache de presets parsés
(`sfParsedPresets`) et l'ensemble en cours de parsing (`sfParsingFiles`) vivent
dans `MixerUiState` uniquement — **cache runtime, non persisté**.

Le fichier fait 48 Ko : seuls les points d'ancrage sont donnés.

### `AppStatePersistence.kt — clés de préférences`

- **Source :** `app/src/main/java/com/soundstage/mixer/model/AppStatePersistence.kt`, lignes **70–85**
- **Taille de l'extrait :** 1056 octets · **lignes :** 16

````kotlin
        private const val KEY_AUDIO_ENGINE = "audio_engine"
        private const val KEY_FLUID_GAIN = "fluid_gain"
        private const val KEY_ANTI_JITTER = "anti_jitter"
        private const val KEY_JITTER_WINDOW_MS = "jitter_window_ms"
        private const val KEY_USE_FLATS = "use_flats"
        private const val KEY_HAPTICS_ENABLED = "haptics_enabled"
        private const val KEY_REDUCED_MOTION = "reduced_motion"
        private const val KEY_SF_FAVORITES = "sf_favorites"
        private const val KEY_SF_CATEGORY_OVERRIDES = "sf_category_overrides"
        private const val KEY_TRANSPORT_BPM = "transport_bpm"
        private const val KEY_TRANSPORT_SIGNATURE = "transport_signature"
        private const val KEY_TRANSPORT_SUBDIV = "transport_subdiv"
        private const val KEY_METRONOME_SOUND = "metronome_sound"
        private const val KEY_METRONOME_ACCENT1 = "metronome_accent1"
        private const val KEY_METRONOME_ACCENT13 = "metronome_accent13"
        private const val KEY_METRONOME_VISUAL_FLASH = "metronome_visual_flash"

````

### `AppStatePersistence.kt — champs de l'état restauré`

- **Source :** `app/src/main/java/com/soundstage/mixer/model/AppStatePersistence.kt`, lignes **185–200**
- **Taille de l'extrait :** 725 octets · **lignes :** 16

````kotlin
        val headroom: Float? = null,
        val audioEngine: String? = null,
        val fluidSynthGain: Float? = null,
        val antiJitter: Boolean? = null,
        val jitterWindowMs: Int? = null,
        val useFlats: Boolean? = null,
        val hapticsEnabled: Boolean? = null,
        val reducedMotion: Boolean? = null,
        val sfFavorites: Set<String> = emptySet(),
        val sfCategoryOverrides: Map<String, String> = emptyMap(),
        val transportBpm: Int? = null,
        val transportSignature: String? = null,
        val transportSubdivision: String? = null,
        val metronomeSound: String? = null,
        val metronomeAccent1: Boolean? = null,
        val metronomeAccent13: Boolean? = null,

````

### `AppStatePersistence.kt — écriture`

- **Source :** `app/src/main/java/com/soundstage/mixer/model/AppStatePersistence.kt`, lignes **560–580**
- **Taille de l'extrait :** 1193 octets · **lignes :** 21

````kotlin
                putString(KEY_AUDIO_ENGINE, audioEngine)
                putFloat(KEY_FLUID_GAIN, fluidSynthGain)
                putBoolean(KEY_ANTI_JITTER, antiJitter)
                putInt(KEY_JITTER_WINDOW_MS, jitterWindowMs)
                putBoolean(KEY_USE_FLATS, useFlats)
                putBoolean(KEY_HAPTICS_ENABLED, hapticsEnabled)
                putBoolean(KEY_REDUCED_MOTION, reducedMotion)

                val sfFavArray = JSONArray()
                sfFavorites.forEach { sfFavArray.put(it) }
                val sfOverridesObj = JSONObject()
                sfCategoryOverrides.forEach { (k, v) -> sfOverridesObj.put(k, v) }
                putString(KEY_SF_FAVORITES, sfFavArray.toString())
                putString(KEY_SF_CATEGORY_OVERRIDES, sfOverridesObj.toString())

                putInt(KEY_TRANSPORT_BPM, transportBpm)
                putString(KEY_TRANSPORT_SIGNATURE, transportSignature)
                putString(KEY_TRANSPORT_SUBDIV, transportSubdivision)
                putString(KEY_METRONOME_SOUND, metronomeSound)
                putBoolean(KEY_METRONOME_ACCENT1, metronomeAccent1)
                putBoolean(KEY_METRONOME_ACCENT13, metronomeAccent13)

````

### `AppStatePersistence.kt — lecture`

- **Source :** `app/src/main/java/com/soundstage/mixer/model/AppStatePersistence.kt`, lignes **1025–1050**
- **Taille de l'extrait :** 1503 octets · **lignes :** 26

````kotlin
            val jitterWindowMs = if (prefs.contains(KEY_JITTER_WINDOW_MS)) prefs.getInt(KEY_JITTER_WINDOW_MS, 15) else null
            val useFlats = if (prefs.contains(KEY_USE_FLATS)) prefs.getBoolean(KEY_USE_FLATS, false) else null
            val hapticsEnabled = if (prefs.contains(KEY_HAPTICS_ENABLED)) prefs.getBoolean(KEY_HAPTICS_ENABLED, true) else null
            val reducedMotion = if (prefs.contains(KEY_REDUCED_MOTION)) prefs.getBoolean(KEY_REDUCED_MOTION, false) else null

            val sfFavoritesSet = mutableSetOf<String>()
            prefs.getString(KEY_SF_FAVORITES, null)?.let { raw ->
                try {
                    val arr = JSONArray(raw)
                    for (i in 0 until arr.length()) sfFavoritesSet.add(arr.getString(i))
                } catch (_: Exception) {}
            }
            val sfOverridesMap = mutableMapOf<String, String>()
            prefs.getString(KEY_SF_CATEGORY_OVERRIDES, null)?.let { raw ->
                try {
                    val obj = JSONObject(raw)
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        sfOverridesMap[k] = obj.getString(k)
                    }
                } catch (_: Exception) {}
            }

            val transportBpm = if (prefs.contains(KEY_TRANSPORT_BPM)) prefs.getInt(KEY_TRANSPORT_BPM, 120) else null
            val transportSignature = prefs.getString(KEY_TRANSPORT_SIGNATURE, null)

````

### Pont natif — `NativeAudioBridge.kt` (extraits)

Les appels JNI liés aux soundfonts. Toute nouvelle méthode publique d'`AudioEngine`
doit être déclarée **au-dessus** du `private:` (vers la ligne 888), sinon erreur
« private member ».

### `NativeAudioBridge.kt — déclarations natives`

- **Source :** `app/src/main/java/com/soundstage/mixer/audio/NativeAudioBridge.kt`, lignes **50–100**
- **Taille de l'extrait :** 3520 octets · **lignes :** 51

````kotlin
    }

    fun isNativeReady(): Boolean = isLibraryLoaded

    external fun startEngine(driverType: Int = 0): Boolean
    external fun stopEngine()
    external fun setAudioDriver(driverType: Int)
    external fun loadSoundFont(engineIndex: Int, absolutePath: String): Int
    external fun unloadSoundFont(engineIndex: Int, soundFontId: Int): Int
    external fun unloadAllSoundFonts(engineIndex: Int = -1)
    external fun trimMemory()
    external fun listPresets(soundFontId: Int): Array<PresetInfo>
    external fun selectProgram(engineIndex: Int, channel: Int, soundFontId: Int, bank: Int, preset: Int): Boolean
    external fun programChange(engineIndex: Int, channel: Int, preset: Int): Boolean
    external fun noteOn(engineIndex: Int, channel: Int, midiNote: Int, velocity: Int)
    external fun noteOff(engineIndex: Int, channel: Int, midiNote: Int)
    external fun allNotesOff(engineIndex: Int, channel: Int)
    external fun setTrackVolume(engineIndex: Int, channel: Int, volume01: Float)
    external fun setTrackPan(engineIndex: Int, channel: Int, pan: Float)
    external fun setTrackTranspose(engineIndex: Int, channel: Int, semitones: Int)
    external fun setPitchBendRange(engineIndex: Int, channel: Int, semitones: Int)
    external fun channelPressure(engineIndex: Int, channel: Int, value: Int)
    external fun pitchBend(engineIndex: Int, channel: Int, bendValue: Int)
    external fun setMasterVolume(volume01: Float)
    external fun setFluidSynthGain(gain: Float)
    external fun setPolyphony(engineIndex: Int, polyphony: Int)
    external fun setBufferSize(bufferSize: Int)
    external fun setMasterEq(lowGainDb: Float, midGainDb: Float, highGainDb: Float)
    external fun setSoundGoodizer(enabled: Boolean, mode: Int, amount: Float)
    external fun setMasterReverb(enabled: Boolean, size: Float, decay: Float, damp: Float, mix: Float)
    external fun setMasterDelay(enabled: Boolean, timeSec: Float, feedback: Float, mix: Float, pingPong: Boolean)
    external fun setMasterChorus(enabled: Boolean, rateHz: Float, depthMs: Float, mix: Float)
    external fun setMasterCompressor(enabled: Boolean, thresholdDb: Float, ratio: Float, attackMs: Float, releaseMs: Float, makeupGainDb: Float)
    external fun setSpatialWidener(amount: Float)
    external fun setMasterPunch(amount: Float)
    external fun setPadBrightness(brightness: Float)
    external fun setChannelReverb(engineIndex: Int, channel: Int, reverb: Float)
    external fun setChannelChorus(engineIndex: Int, channel: Int, chorus: Float)
    external fun setTonicFxChain(filterOn: Boolean, filterAmt: Float, brightnessOn: Boolean, brightnessAmt: Float, delayOn: Boolean, delayAmt: Float, reverbOn: Boolean, reverbAmt: Float, shimmerOn: Boolean, shimmerAmt: Float)
    external fun setPadsFxChain(filterOn: Boolean, filterAmt: Float, brightnessOn: Boolean, brightnessAmt: Float, delayOn: Boolean, delayAmt: Float, reverbOn: Boolean, reverbAmt: Float)
    external fun renderNativeAudio(audioBuffer: ShortArray, numFrames: Int): Int
    external fun hasActiveSoundFonts(): Boolean
    external fun isOboeActive(): Boolean
    external fun setBypassMasterFX(bypass: Boolean)
    external fun isMasterFxBypassed(): Boolean
    external fun controlChange(engineIndex: Int, channel: Int, cc: Int, value: Int)

    // Dedicated SamplePlaybackEngine external functions
    external fun triggerDrumSample(sampleId: Int, velocity: Float, pan: Float)
    external fun stopDrumSample(sampleId: Int)
    external fun stopAllDrumSamples()

````

### `NativeAudioBridge.kt — enveloppes sûres`

- **Source :** `app/src/main/java/com/soundstage/mixer/audio/NativeAudioBridge.kt`, lignes **185–240**
- **Taille de l'extrait :** 1920 octets · **lignes :** 56
- Tout appel natif est enveloppé : une exception qui s'échappe d'un callback Compose démonte la fenêtre.

````kotlin
            } catch (e: Throwable) {
                Log.e("NativeAudioBridge", "Error invoking setAudioDriver: ${e.message}")
            }
        }
    }

    fun safeLoadSoundFont(engineIndex: Int = ENGINE_FADER, absolutePath: String): Int {
        if (!isLibraryLoaded) {
            Log.e("NativeAudioBridge", "Cannot load SoundFont: native library is not loaded!")
            return -1
        }
        return try {
            val resId = loadSoundFont(engineIndex, absolutePath)
            Log.d("NativeAudioBridge", "loadSoundFont(engine=$engineIndex, path='$absolutePath') -> result ID: $resId")
            resId
        } catch (e: Throwable) {
            Log.e("NativeAudioBridge", "Error invoking loadSoundFont for path '$absolutePath': ${e.message}", e)
            -1
        }
    }

    fun safeUnloadSoundFont(engineIndex: Int = ENGINE_FADER, soundFontId: Int): Int {
        return if (isLibraryLoaded && soundFontId > 0) {
            try {
                val res = unloadSoundFont(engineIndex, soundFontId)
                safeTrimMemory()
                res
            } catch (e: Throwable) {
                Log.e("NativeAudioBridge", "Error invoking unloadSoundFont: ${e.message}")
                -1
            }
        } else -1
    }

    fun safeTrimMemory() {
        if (isLibraryLoaded) {
            try {
                trimMemory()
            } catch (e: Throwable) {
                Log.e("NativeAudioBridge", "Error invoking trimMemory: ${e.message}")
            }
        }
    }

    fun safeUnloadAllSoundFonts(engineIndex: Int = -1) {
        if (isLibraryLoaded) {
            try {
                unloadAllSoundFonts(engineIndex)
            } catch (e: Throwable) {
                Log.e("NativeAudioBridge", "Error invoking unloadAllSoundFonts: ${e.message}")
            }
        }
    }

    fun safeSelectProgram(
        engineIndex: Int = ENGINE_FADER,

````

### Avertissement d'incompatibilité — `FxSfWarning.kt` (fichier intégral)

Le bandeau rouge « CHOOSE A SOUNDFONT » de la fenêtre FX. Tables `FAMOK` (plages
GM acceptées par instrument), `KW` (mots-clés), `SHN` (nom lisible). **C'est le
point d'entrée direct si la migration Organ/Synth doit être avertie elle aussi** :
ajouter une entrée dans les trois tables suffit.

Note : les instruments vérifiés aujourd'hui sont 5 (piano), 7 (sax), 8 (strings),
9 (guitar), 12 (brass). **Organ (6) et Synth (14) n'y sont pas** — à ajouter si la
migration le demande.

### `FxSfWarning.kt`

- **Chemin :** `app/src/main/java/com/soundstage/mixer/ui/fx/FxSfWarning.kt`
- **Taille :** 1929 octets · **lignes :** 47 · **sha256 (12) :** `f61169487eed`

````kotlin
package com.soundstage.mixer.ui.fx

/**
 * Soundfont / instrument mismatch warning, from the preview's `FAMOK`, `KW` and
 * `SHN` tables. A shaper instrument only sounds right on a soundfont of its own
 * family; the window warns when the loaded one looks unrelated.
 */
object FxSfWarning {
    /** GM program ranges accepted per instrument (inclusive). */
    private val FAMOK: Map<Int, List<IntRange>> = mapOf(
        5 to listOf(0..7, 16..23),
        7 to listOf(64..71),
        8 to listOf(40..51),
        9 to listOf(24..31),
        12 to listOf(56..63),
    )

    private val KW: Map<Int, Regex> = mapOf(
        5 to Regex("piano|keys|clav|harpsichord", RegexOption.IGNORE_CASE),
        7 to Regex("sax|reed", RegexOption.IGNORE_CASE),
        8 to Regex("violin|viola|cello|string|orchestra|fiddle", RegexOption.IGNORE_CASE),
        9 to Regex("guitar|gtr|strat|nylon|banjo", RegexOption.IGNORE_CASE),
        12 to Regex("trumpet|trombone|horn|brass|tuba|cornet", RegexOption.IGNORE_CASE),
    )

    private val SHN: Map<Int, String> = mapOf(
        5 to "a piano",
        7 to "a saxophone",
        8 to "a violin or string section",
        9 to "a guitar",
        12 to "a brass instrument",
    )

    /** True when [effectId] is not a family-checked instrument (no warning applies). */
    fun checked(effectId: Int): Boolean = FAMOK.containsKey(effectId)

    fun matches(effectId: Int, soundfontName: String, program: Int): Boolean {
        val ranges = FAMOK[effectId] ?: return true
        if (KW[effectId]?.containsMatchIn(soundfontName) == true) return true
        return ranges.any { program in it }
    }

    fun head(effectId: Int): String = "This doesn't look like ${SHN[effectId] ?: "a matching instrument"}."

    fun body(effectId: Int, soundfontName: String): String =
        "The loaded soundfont is \u201C$soundfontName\u201D. Choose a matching soundfont for the best result."
}

````

---

## 6. Ce que la migration Organ / Synth implique

Le code ci-dessus suffit pour agir. Points d'ancrage, par ordre de nécessité :

1. **`FxSfWarning.kt`** — ajouter `6` (Organ) et `14` (Synth) dans `FAMOK`, `KW` et
   `SHN`. Sans ça, aucun avertissement ne s'affichera pour ces deux instruments.
2. **`FxRegistry.kt`** — vérifier que les ids 6 et 14 sont bien `FxKind.SOURCE`
   (un instrument remplace le soundfont) et non `SHAPER`. `instrumentIds` liste
   aujourd'hui `5, 6, 7, 8, 9, 12, 14`.
3. **`SfClassifier`** (dans `SoundfontWindow.kt`) — la catégorie `Organ` existe
   déjà et est prioritaire. Pour Synth, la catégorie `Synth` existe
   (`lead|synth|saw|square|arp|pluck`) mais elle est en **12e position** : elle
   n'attrape que ce que `Piano`/`Keys`/`Strings`/`Pads` n'ont pas déjà pris.
4. **`SfWindowActions`** — si la migration ajoute un filtre ou un onglet dédié,
   l'interface a déjà `onMoveToCategory` et `onRequestParse` ; préférer les
   réutiliser à ajouter des callbacks.
5. **`SoundfontVariant`** — trois valeurs seulement (`Track`, `Pad`, `Drum`). Si
   Organ/Synth exigent une quatrième forme, c'est ici, et il faudra mettre à jour
   le `when` du câblage en §3.

### Ce qui existe déjà et ne doit pas être recréé

- La catégorie **Organ** et la catégorie **Synth** dans `SfCategory`.
- Le mécanisme de **favoris** et de **déplacement manuel de catégorie**, persistés.
- Le **parsing en arrière-plan** (`parseSfInBackground`) avec `sfParsingFiles`.
- Le **ramasse-miettes** de soundfonts (`garbageCollectUnusedSoundFonts`).
- L'**avertissement d'incompatibilité** (à étendre, pas à écrire).

---

## 7. Fichiers annexes, non reproduits

| Fichier | Pourquoi |
|---|---|
| `MixerViewModel.kt` (320 Ko) | Seules les fonctions soundfont sont reproduites (§4) |
| `AppStatePersistence.kt` (48 Ko) | Seuls les points d'ancrage (§5) |
| `NativeAudioBridge.kt` (27 Ko) | Seules les déclarations et enveloppes (§5) |
| `FileManager.kt` (48 Ko) | Gestion des dossiers et du scan ; hors périmètre fenêtre |
| `AudioEngine.kt`, `FallbackSynth.kt` | Moteur audio ; le soundfont y est un chemin |
| `Theme.kt` | Fournit `AppColors` utilisés par `SfColors` |
| `soundfont_engine.h/.cpp` (C++) | DSP FluidSynth ; le soundfont y est chargé par chemin |

Liens bruts :

```bash
BASE=https://raw.githubusercontent.com/amiel767/NewKeys-/main
curl -L $BASE/app/src/main/java/com/soundstage/mixer/ui/components/SoundfontWindow.kt -o SoundfontWindow.kt
curl -L $BASE/app/src/main/java/com/soundstage/mixer/viewmodel/MixerViewModel.kt  -o MixerViewModel.kt
```

---

## 8. Résumé pour un agent pressé

1. L'écran est `SoundfontWindow(state, actions)` — **stateless**. Ne pas y mettre
   de logique métier.
2. Le JSX (§2) fait foi pour le style ; ses valeurs sont en px de canvas.
3. L'état se construit en §3 ; les actions sont branchées sur le ViewModel en §4.
4. Le chargement passe par `loadSoundFontForSlot` (§4) — attention au `finally`
   qui restaure le volume.
5. Pour la migration Organ/Synth : commencer par `FxSfWarning.kt` (§5), puis
   vérifier `FxRegistry` et les catégories. **Les catégories Organ et Synth
   existent déjà.**

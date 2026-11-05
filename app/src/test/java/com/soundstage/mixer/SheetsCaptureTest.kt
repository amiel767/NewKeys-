package com.soundstage.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import com.aetherkeys.sheets.model.Bar
import com.aetherkeys.sheets.model.Chord
import com.aetherkeys.sheets.model.Ext
import com.aetherkeys.sheets.model.LibraryData
import com.aetherkeys.sheets.model.Quality
import com.aetherkeys.sheets.model.Section
import com.aetherkeys.sheets.model.SectionKind
import com.aetherkeys.sheets.model.Setlist
import com.aetherkeys.sheets.model.Sheet
import com.aetherkeys.sheets.model.SheetsSettings
import com.aetherkeys.sheets.data.SheetsRepository
import com.aetherkeys.sheets.ui.SheetsRoot
import com.aetherkeys.sheets.vm.SheetsLibraryViewModel
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Captures the real Sheets library screen and its settings, so the chrome
 * (background, buttons, switches) can be compared against the mixer and
 * Settings. Sheets are written into a temp folder with the real repository.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w891dp-h411dp-440dpi", sdk = [36])
class SheetsCaptureTest {

    @get:Rule val composeTestRule = createComposeRule()

    private fun chord(root: Int, ext: Set<Ext> = emptySet()) = Chord(root, Quality.Major, ext)

    private fun seed(dir: File) = runBlocking {
        val repo = SheetsRepository(dir)
        val sheets = listOf(
            Sheet(
                title = "Midnight Drive", keyPc = 9, bpm = 124, colorArgb = 0xFF7A5CFFL,
                favorite = true,
                sections = listOf(
                    Section(
                        kind = SectionKind.Verse, name = "Verse",
                        bars = listOf(
                            Bar(chords = listOf(chord(9))), Bar(chords = listOf(chord(2))),
                            Bar(chords = listOf(chord(4))), Bar(chords = listOf(chord(7, setOf(Ext.Seven)))),
                        ),
                    ),
                    Section(
                        kind = SectionKind.Chorus, name = "Chorus",
                        bars = listOf(Bar(chords = listOf(chord(0))), Bar(chords = listOf(chord(5)))),
                    ),
                ),
            ),
            Sheet(
                title = "Salt Wind", keyPc = 4, bpm = 92, colorArgb = 0xFF26A58AL,
                sections = listOf(
                    Section(kind = SectionKind.Intro, name = "Intro", bars = listOf(Bar(chords = listOf(chord(4))), Bar(chords = listOf(chord(9))))),
                ),
            ),
            Sheet(
                title = "Paper Lanterns", keyPc = 0, bpm = 76, colorArgb = 0xFFE0508FL,
                sections = listOf(Section(kind = SectionKind.Bridge, name = "Bridge", bars = listOf(Bar(chords = listOf(chord(0)))))),
            ),
        )
        sheets.forEach { repo.save(it) }
        repo.saveLibrary(
            LibraryData(
                settings = SheetsSettings(notation = com.aetherkeys.sheets.model.Notation.Nashville),
                // updatedAt distinct : l'ordre d'affichage des Sets est deterministe.
                setlists = listOf(
                    Setlist(name = "Live set", sheetIds = sheets.map { it.id }, colorArgb = 0xFF3F7BE8L, updatedAt = 2_000L),
                    Setlist(name = "Dimanche matin", sheetIds = sheets.take(2).map { it.id }, colorArgb = 0xFF26A58AL, updatedAt = 1_000L),
                ),
            )
        )
        sheets
    }

    @Test
    fun sheets_home() {
        val dir = File.createTempFile("sheets", "").let { it.delete(); it.mkdirs(); it }
        seed(dir)
        composeTestRule.setContent {
            SheetsRoot(
                repo = SheetsRepository(dir),
                bottomInset = androidx.compose.ui.unit.Dp(0f),
                onBackToMixer = {},
                onFloatOverMixer = {},
            )
        }
        // La bibliotheque est lue sur Dispatchers.IO : attendre la fin du chargement reel.
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText("Midnight Drive").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.mainClock.advanceTimeBy(1_500)
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/sheets_home.png")
    }

    /**
     * La carte flottante posee sur le mixer : sans contour, a la couleur de la feuille,
     * plusieurs sections a la fois, redimensionnable. Rendue seule.
     */
    @Test
    fun sheets_floating() {
        val sheet = Sheet(
            title = "Midnight Drive", keyPc = 9, bpm = 124, colorArgb = 0xFF7A5CFFL,
            sections = listOf(
                Section(
                    kind = SectionKind.Verse, name = "Verse",
                    bars = listOf(
                        Bar(chords = listOf(chord(9))), Bar(chords = listOf(chord(2))),
                        Bar(chords = listOf(chord(4))), Bar(chords = listOf(chord(7, setOf(Ext.Seven)))),
                    ),
                ),
                Section(kind = SectionKind.Chorus, name = "Chorus", bars = listOf(Bar(chords = listOf(chord(0))), Bar(chords = listOf(chord(5))))),
                Section(kind = SectionKind.Bridge, name = "Bridge", bars = listOf(Bar(chords = listOf(chord(11))), Bar(chords = listOf(chord(4))))),
            ),
        )
        composeTestRule.setContent {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(Color(0xFF10141CL))) {
                com.aetherkeys.sheets.ui.floating.FloatingSheetHost(
                    sheet = sheet,
                    settings = SheetsSettings(),
                    originRect = null,
                    bottomInset = androidx.compose.ui.unit.Dp(0f),
                    onOpenEditor = {},
                    onClose = {},
                )
            }
        }
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.mainClock.advanceTimeBy(2_000)
        // Les trois commandes de la carte existent : X (haut droite), verrou (bas gauche),
        // et la pastille de section. Le verrou a quitte la barre du haut.
        composeTestRule.onNodeWithContentDescription("Close scene").assertExists()
        composeTestRule.onNodeWithContentDescription("Lock scene").assertExists()
        composeTestRule.onNodeWithTag("resize-grip").assertExists()
        composeTestRule.onNodeWithTag("section-strip").assertExists()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/sheets_floating.png")
    }

    /**
     * Edition rapide : un appui long sur la scene ouvre la ligne d'accords de la section
     * affichee, relue au clavier systeme. C'est le meme langage que le chart, donc rien a
     * expliquer ; l'editeur complet reste au double tap.
     */
    @Test
    fun sheets_floating_quick_edit() {
        val sheet = Sheet(
            title = "Midnight Drive", keyPc = 9, bpm = 124, colorArgb = 0xFF7A5CFFL,
            sections = listOf(
                Section(
                    kind = SectionKind.Verse, name = "Verse",
                    bars = listOf(
                        Bar(chords = listOf(chord(9))), Bar(chords = listOf(chord(2))),
                        Bar(chords = listOf(chord(4))),
                    ),
                ),
            ),
        )
        composeTestRule.setContent {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(Color(0xFF10141CL))) {
                com.aetherkeys.sheets.ui.floating.FloatingSheetHost(
                    sheet = sheet,
                    settings = SheetsSettings(),
                    originRect = null,
                    bottomInset = androidx.compose.ui.unit.Dp(0f),
                    onOpenEditor = {},
                    onClose = {},
                )
            }
        }
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.mainClock.advanceTimeBy(2_000)
        composeTestRule.onNodeWithTag("floating-card").performTouchInput { longClick() }
        composeTestRule.mainClock.advanceTimeBy(1_000)
        // La ligne relue est celle de la section affichee, en degres (notation Nashville).
        composeTestRule.onNodeWithText("1 | 4 | 5", substring = true).assertExists()
        composeTestRule.onNodeWithText("Valider").assertExists()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/sheets_floating_quick_edit.png")
    }

    /** La liste des sections s'ouvre DANS la carte (voile interne), pas dans un popover. */
    @Test
    fun sheets_floating_sections() {
        val sheet = Sheet(
            title = "Midnight Drive", keyPc = 9, bpm = 124, colorArgb = 0xFF7A5CFFL,
            sections = listOf(
                Section(kind = SectionKind.Verse, name = "Verse", bars = listOf(Bar(chords = listOf(chord(9))), Bar(chords = listOf(chord(2))))),
                Section(kind = SectionKind.Chorus, name = "Chorus", bars = listOf(Bar(chords = listOf(chord(0))), Bar(chords = listOf(chord(5))))),
                Section(kind = SectionKind.Bridge, name = "Bridge", bars = listOf(Bar(chords = listOf(chord(11))), Bar(chords = listOf(chord(4))))),
            ),
        )
        composeTestRule.setContent {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(Color(0xFF10141CL))) {
                com.aetherkeys.sheets.ui.floating.FloatingSheetHost(
                    sheet = sheet,
                    settings = SheetsSettings(),
                    originRect = null,
                    bottomInset = androidx.compose.ui.unit.Dp(0f),
                    onOpenEditor = {},
                    onClose = {},
                )
            }
        }
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.mainClock.advanceTimeBy(2_000)
        composeTestRule.onNodeWithTag("section-strip").performClick()
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.onNodeWithTag("section-picker").assertExists()
        composeTestRule.onNodeWithText("SECTIONS").assertExists()
        composeTestRule.onNodeWithText("Chorus").assertExists()
        composeTestRule.onNodeWithText("Bridge").assertExists()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/sheets_floating_sections.png")
    }

    /**
     * Les Sets en tete de bibliotheque, quatre par ligne comme les feuilles : un Set
     * est un dossier de feuilles, on lui donne un nom et une couleur, et il se range
     * dans la meme grille que le reste.
     */
    @Test
    fun sheets_sets() {
        val dir = File.createTempFile("sheets-sets", "").let { it.delete(); it.mkdirs(); it }
        seed(dir)
        composeTestRule.setContent {
            SheetsRoot(
                repo = SheetsRepository(dir),
                bottomInset = androidx.compose.ui.unit.Dp(0f),
                onBackToMixer = {},
                onFloatOverMixer = {},
            )
        }
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText("Live set").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.mainClock.advanceTimeBy(1_500)
        // Les libelles de section sont rendus en majuscules (OverlineLabel).
        // Les Sets sont la, en haut, sans avoir touche au filtre.
        composeTestRule.onAllNodesWithTag("set-card").assertCountEquals(2)
        // Un seul endroit porte le nom d'un Set : sa carte.
        composeTestRule.onNodeWithText("Live set").assertExists()
        composeTestRule.onNodeWithText("Dimanche matin").assertExists()
        // La liste du bas montre les feuilles avec leurs informations.
        composeTestRule.onNodeWithText("Midnight Drive").assertExists()
        composeTestRule.onNodeWithText("Salt Wind").assertExists()
        composeTestRule.onNodeWithText("RECEMMENT MODIFIES").assertExists()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/sheets_sets.png")
    }

    /** Une feuille rangee dans un Set, vue depuis l'interieur du Set : ordre de jeu. */
    @Test
    fun sheets_inside_a_set() {
        val dir = File.createTempFile("sheets-inset", "").let { it.delete(); it.mkdirs(); it }
        seed(dir)
        composeTestRule.setContent {
            SheetsRoot(
                repo = SheetsRepository(dir),
                bottomInset = androidx.compose.ui.unit.Dp(0f),
                onBackToMixer = {},
                onFloatOverMixer = {},
            )
        }
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText("Live set").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.mainClock.advanceTimeBy(1_500)
        // Ouvrir le Set depuis sa carte (la premiere de la grille : « Live set »).
        composeTestRule.onAllNodesWithTag("set-card")[0].performClick()
        composeTestRule.mainClock.advanceTimeBy(600)
        // La rangee de Sets reste : on passe d'un Set a l'autre sans revenir en arriere.
        composeTestRule.onAllNodesWithTag("set-card").assertCountEquals(2)
        // Le Set ouvert se marque, et la liste annonce ce qu'on regarde.
        composeTestRule.onNodeWithText("OUVERT").assertExists()
        composeTestRule.onNodeWithText("← Tout").assertExists()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/sheets_inside_a_set.png")
    }

    private fun floatingSheet(sections: Int) = Sheet(
        title = "Midnight Drive", keyPc = 9, bpm = 124, colorArgb = 0xFF7A5CFFL,
        sections = listOf(
            Section(kind = SectionKind.Verse, name = "Verse", bars = List(4) { Bar(chords = listOf(chord(it % 12))) }),
            Section(kind = SectionKind.Chorus, name = "Chorus", bars = List(4) { Bar(chords = listOf(chord((it + 5) % 12))) }),
            Section(kind = SectionKind.Bridge, name = "Bridge", bars = List(4) { Bar(chords = listOf(chord((it + 7) % 12))) }),
            Section(kind = SectionKind.Outro, name = "Outro", bars = List(2) { Bar(chords = listOf(chord((it + 2) % 12))) }),
        ).take(sections),
    )

    private fun renderFloating(sections: Int) {
        val sheet = floatingSheet(sections)
        composeTestRule.setContent {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(Color(0xFF10141CL))) {
                com.aetherkeys.sheets.ui.floating.FloatingSheetHost(
                    sheet = sheet,
                    settings = SheetsSettings(),
                    originRect = null,
                    bottomInset = androidx.compose.ui.unit.Dp(0f),
                    onOpenEditor = {},
                    onClose = {},
                )
            }
        }
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.mainClock.advanceTimeBy(2_000)
    }

    /**
     * Pastille fine du bas : en allongeant la fenetre, les sections suivantes
     * s'affichent tant qu'elles entrent dans la hauteur.
     */
    @Test
    fun sheets_floating_tall() {
        renderFloating(sections = 4)
        // Avant : une seule section, celle choisie par defaut.
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/sheets_floating_before.png")
        composeTestRule.onNodeWithTag("resize-grip").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(0f, 400f))
            up()
        }
        composeTestRule.mainClock.advanceTimeBy(800)
        composeTestRule.onNodeWithText("Chorus").assertExists()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/sheets_floating_tall.png")
    }

    /**
     * Pastille fine de gauche : en elargissant la fenetre, les polices des accords
     * grandissent avec la largeur.
     */
    @Test
    fun sheets_floating_wide() {
        renderFloating(sections = 2)
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/sheets_floating_narrow.png")
        composeTestRule.onNodeWithTag("resize-grip-left").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(260f, 0f))
            up()
        }
        composeTestRule.mainClock.advanceTimeBy(800)
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/sheets_floating_wide.png")
    }
}

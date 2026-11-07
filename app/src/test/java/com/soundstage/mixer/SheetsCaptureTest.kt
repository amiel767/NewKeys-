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
     * The settings screen. It is part of the redesign: the notation group explains what
     * the choice does, and the default-key chips are spelled per key signature, so the
     * settings and the list never disagree on a note name.
     */
    @Test
    fun sheets_settings() {
        val dir = File.createTempFile("sheets-settings", "").let { it.delete(); it.mkdirs(); it }
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
            composeTestRule.onAllNodesWithText("Midnight Drive").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.mainClock.advanceTimeBy(1_500)
        composeTestRule.onNodeWithContentDescription("Sheets settings").performClick()
        composeTestRule.mainClock.advanceTimeBy(800)
        // The explanation follows the chosen notation.
        composeTestRule.onNodeWithText("Chords read as scale degrees (1, 4, 5) — the same chart transposes for free.").assertExists()
        // The default key reads Bb, not A#.
        composeTestRule.onNodeWithText("Bb").assertExists()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/sheets_settings.png")
    }

    /**
     * The floating card sitting on the mixer: no outline, tinted like the sheet, several
     * sections at once, resizable. Rendered on its own.
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
        // and the section strip. The lock has left the top bar.
        composeTestRule.onNodeWithContentDescription("Close scene").assertExists()
        composeTestRule.onNodeWithContentDescription("Lock scene").assertExists()
        composeTestRule.onNodeWithTag("resize-grip").assertExists()
        composeTestRule.onNodeWithTag("section-strip").assertExists()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/sheets_floating.png")
    }

    /**
     * Quick edit: a long press on the scene opens the chord line of the displayed
     * section, read back through the system keyboard. Same language as the chart, so
     * nothing to explain; the full editor stays on double tap.
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
        // The line read back is the displayed section, in degrees (Nashville notation).
        composeTestRule.onNodeWithText("1 | 4 | 5", substring = true).assertExists()
        composeTestRule.onNodeWithText("Apply").assertExists()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/sheets_floating_quick_edit.png")
    }

    /** The section list opens INSIDE the card (internal veil), not in a popover. */
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
     * The Sets at the top of the library, one row of cards: a Set is a folder of sheets,
     * it gets a name and a colour of its own, and it sits
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
        // Overline labels are uppercased, hence the shouty spelling.
        // The Sets are there, at the top, without touching any filter.
        composeTestRule.onAllNodesWithTag("set-card").assertCountEquals(2)
        // Exactly one place carries a Set name: its card.
        composeTestRule.onNodeWithText("Live set").assertExists()
        composeTestRule.onNodeWithText("Dimanche matin").assertExists()
        // The list below shows the sheets with their details.
        composeTestRule.onNodeWithText("Midnight Drive").assertExists()
        composeTestRule.onNodeWithText("Salt Wind").assertExists()
        composeTestRule.onNodeWithText("RECENTLY EDITED").assertExists()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/sheets_sets.png")
    }

    /** A sheet filed in a Set, seen from inside the Set: play order. */
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
        // Open the Set from its card (the first one: "Live set").
        composeTestRule.onAllNodesWithTag("set-card")[0].performClick()
        composeTestRule.mainClock.advanceTimeBy(600)
        // The Set row stays: you move from one Set to the next without going back.
        composeTestRule.onAllNodesWithTag("set-card").assertCountEquals(2)
        // The open Set is marked, and the list announces what is being shown.
        composeTestRule.onNodeWithText("OPEN").assertExists()
        composeTestRule.onNodeWithText("← All").assertExists()
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

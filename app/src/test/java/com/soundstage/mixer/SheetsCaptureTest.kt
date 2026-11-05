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
import androidx.compose.ui.test.performClick
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
                setlists = listOf(Setlist(name = "Live set", sheetIds = sheets.map { it.id })),
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
}

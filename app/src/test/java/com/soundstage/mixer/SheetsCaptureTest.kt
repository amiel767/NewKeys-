package com.soundstage.mixer

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
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
}

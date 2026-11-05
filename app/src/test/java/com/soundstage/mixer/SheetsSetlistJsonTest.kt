package com.soundstage.mixer

import com.aetherkeys.sheets.data.SheetJson
import com.aetherkeys.sheets.model.LibraryData
import com.aetherkeys.sheets.model.Setlist
import com.aetherkeys.sheets.model.SheetsSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Persistance d'un Set : couleur et dates doivent survivre a un aller-retour JSON,
 * et une bibliotheque ecrite par une version anterieure (sans ces champs) doit se
 * relire sans erreur, avec les valeurs par defaut.
 */
@RunWith(RobolectricTestRunner::class)
class SheetsSetlistJsonTest {

    @Test
    fun setlistRoundTripKeepsColourAndDates() {
        val set = Setlist(
            name = "Dimanche matin",
            sheetIds = listOf("a", "b", "c"),
            colorArgb = 0xFF26A58AL,
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_500_000L,
        )
        val back = SheetJson.decodeLibrary(SheetJson.encodeLibrary(LibraryData(setlists = listOf(set)))).setlists.single()

        assertEquals(set.id, back.id)
        assertEquals("Dimanche matin", back.name)
        assertEquals(listOf("a", "b", "c"), back.sheetIds)
        assertEquals(0xFF26A58AL, back.colorArgb)
        assertEquals(1_700_000_000_000L, back.createdAt)
        assertEquals(1_700_000_500_000L, back.updatedAt)
    }

    /** Le Set conserve l'ordre de jeu : c'est lui qui fait la setlist. */
    @Test
    fun setlistKeepsPlayOrder() {
        val set = Setlist(name = "Live", sheetIds = listOf("z", "a", "m"))
        val back = SheetJson.decodeLibrary(SheetJson.encodeLibrary(LibraryData(setlists = listOf(set)))).setlists.single()
        assertEquals(listOf("z", "a", "m"), back.sheetIds)
    }

    /** Ancien format : pas de couleur ni de dates. Rien ne doit lever. */
    @Test
    fun oldLibraryWithoutSetColourStillLoads() {
        val legacy = """
            {"settings":{"notation":"Nashville"},"setlists":[{"id":"s1","name":"Live set","sheets":["x","y"]}]}
        """.trimIndent()
        val lib = SheetJson.decodeLibrary(legacy)
        val set = lib.setlists.single()
        assertEquals("s1", set.id)
        assertEquals("Live set", set.name)
        assertEquals(listOf("x", "y"), set.sheetIds)
        assertNull(set.colorArgb)
        assertTrue(set.createdAt > 0)
        assertEquals(SheetsSettings().notation, lib.settings.notation)
    }

    /** Le nom par defaut a change de « Setlist » a « Set ». */
    @Test
    fun setWithoutNameFallsBackToSet() {
        val lib = SheetJson.decodeLibrary("""{"setlists":[{"id":"s2","sheets":[]}]}""")
        assertEquals("Set", lib.setlists.single().name)
    }

    @Test
    fun emptyLibraryIsSafe() {
        val lib = SheetJson.decodeLibrary("{}")
        assertTrue(lib.setlists.isEmpty())
    }
}

/**
 * Aller-retour par le vrai depot : un fichier `.library.json` ecrit puis relu.
 * Aucun mock — c'est le meme code que celui utilise par l'application.
 */
@RunWith(RobolectricTestRunner::class)
class SheetsSetlistRepositoryTest {

    @Test
    fun setSurvivesAWriteAndReadThroughTheRepository() = kotlinx.coroutines.runBlocking {
        val dir = java.io.File.createTempFile("sheets-sets", "").let { it.delete(); it.mkdirs(); it }
        val repo = com.aetherkeys.sheets.data.SheetsRepository(dir)
        val set = Setlist(name = "Mariage", sheetIds = listOf("s1", "s2"), colorArgb = 0xFFE0508FL)

        repo.saveLibrary(LibraryData(setlists = listOf(set)))
        val back = repo.loadLibrary().setlists.single()

        assertEquals("Mariage", back.name)
        assertEquals(listOf("s1", "s2"), back.sheetIds)
        assertEquals(0xFFE0508FL, back.colorArgb)
    }

    @Test
    fun aMissingLibraryFileYieldsAnEmptyLibrary() = kotlinx.coroutines.runBlocking {
        val dir = java.io.File.createTempFile("sheets-empty", "").let { it.delete(); it.mkdirs(); it }
        assertTrue(com.aetherkeys.sheets.data.SheetsRepository(dir).loadLibrary().setlists.isEmpty())
    }
}

/** Filtrage de la bibliotheque : c'est la logique qui decide ce qui s'affiche. */
@RunWith(RobolectricTestRunner::class)
class SheetsLibraryFilterTest {

    private fun state(sheets: List<com.aetherkeys.sheets.model.Sheet>, sets: List<Setlist>, filter: com.aetherkeys.sheets.vm.LibraryFilter, query: String = "") =
        com.aetherkeys.sheets.vm.LibraryUiState(
            sheets = sheets,
            library = LibraryData(setlists = sets),
            filter = filter,
            query = query,
        )

    private val a = com.aetherkeys.sheets.model.Sheet(title = "Alpha")
    private val b = com.aetherkeys.sheets.model.Sheet(title = "Bravo")
    private val set = Setlist(name = "Dimanche", sheetIds = listOf(b.id, a.id))

    @Test
    fun setFilterShowsItsSheetsInPlayOrder() {
        val st = state(listOf(a, b), listOf(set), com.aetherkeys.sheets.vm.LibraryFilter.OfSetlist(set.id))
        assertEquals(listOf(b.id, a.id), st.visibleSheets().map { it.id })
    }

    @Test
    fun setsFilterShowsNoSheetButEverySet() {
        val st = state(listOf(a, b), listOf(set), com.aetherkeys.sheets.vm.LibraryFilter.Sets)
        assertTrue(st.visibleSheets().isEmpty())
        assertEquals(1, st.visibleSetlists().size)
    }

    @Test
    fun searchMatchesSetNamesToo() {
        val other = Setlist(name = "Veille")
        val st = state(listOf(a, b), listOf(set, other), com.aetherkeys.sheets.vm.LibraryFilter.All, query = "diman")
        assertEquals(listOf("Dimanche"), st.visibleSetlists().map { it.name })
    }

    @Test
    fun aSheetKnowsWhichSetsHoldIt() {
        val st = state(listOf(a, b), listOf(set), com.aetherkeys.sheets.vm.LibraryFilter.All)
        assertEquals(1, st.setlistsContaining(a.id).size)
        assertTrue(st.setlistsContaining("inconnu").isEmpty())
    }
}

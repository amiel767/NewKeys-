package com.soundstage.mixer

import com.aetherkeys.fx.FxDesign
import com.aetherkeys.fx.FxGeo
import com.aetherkeys.fx.FxKindD
import com.aetherkeys.fx.SpaceStyle
import com.aetherkeys.fx.spaceStyleFor
import com.soundstage.mixer.fx.FxRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les invariants du design FX, verifies sans rendre une image.
 *
 * Ces tests ne remplacent pas les captures : ils verrouillent les CONTRATS dont
 * une capture ne dit rien. Le plus important est l'ORDRE DES REGLAGES : l'UI
 * pousse une valeur normalisee par INDEX, donc un catalogue qui derive du
 * registre moteur enverrait silencieusement un reglage sur le mauvais parametre
 * DSP — « Decay » reglerait la taille, par exemple. Rien a l'ecran ne le
 * signalerait ; c'est ce test qui le refuse.
 *
 * C'est un test JVM PUR, sans Robolectric : le catalogue ne touche aucune API
 * Android (ses couleurs sont des `Color` de Compose, qui vivent sur la JVM). Il
 * tourne donc en une seconde au lieu de monter un Android entier, et il ne
 * depend pas du telechargement d'un jar Robolectric.
 */
class FxDesignTest {

    private val space = FxDesign.SPACE

    /** L'ordre exact du moteur, celui de `fx_registry.h` (FX_SPACE). */
    private val moteur = listOf(
        "Mix", "Pre-delay", "Decay", "HighFreq", "HighShelf", "BassFreq", "BassMult",
        "Size", "Attack", "Early", "Late", "Rate", "Depth", "HighCut", "LowCut",
    )

    @Test
    fun chaquePresetDeSpaceAPorteSaPalette() {
        space.presets.keys.forEach { name ->
            assertNotNull("preset $name sans palette", space.palettes[name])
        }
    }

    /** Le style du titre et le fond suivent le preset : rien ne doit etre uniforme. */
    @Test
    fun leStyleDuTitreSuitLePreset() {
        val sizes = space.palettes.values.map { it.titleSize }.distinct()
        val spacings = space.palettes.values.map { it.titleSpacing }.distinct()
        val weights = space.palettes.values.map { it.titleWeight }.distinct()
        assertTrue("toutes les tailles de titre identiques : $sizes", sizes.size >= 3)
        assertTrue("toutes les interlettres identiques : $spacings", spacings.size >= 3)
        assertTrue("toutes les graisses identiques : $weights", weights.size >= 2)
    }

    @Test
    fun leFondDeuxTeintesDiffertDunPresetALautre() {
        val bgs = space.palettes.values.map { it.bgA }
        assertEquals("deux presets partagent le meme fond", bgs.size, bgs.distinct().size)
    }

    /** La rampe de cinq couleurs de la reference : cinq teintes distinctes par preset. */
    @Test
    fun chaquePresetAPorteUneRampeDeCinqCouleurs() {
        space.palettes.forEach { (name, pal) ->
            assertEquals("$name n'a pas cinq couleurs", 5, pal.colors.size)
            assertEquals("$name a des couleurs repetees", 5, pal.colors.distinct().size)
        }
    }

    /** Un seul reglage porte le gros potard, sinon l'index affiche serait ambigu. */
    @Test
    fun unSeulReglagePrincipal() {
        assertEquals(1, space.params.count { it.main })
        assertEquals("le gros potard porte DECAY", "decay", space.mainParam.key)
    }

    /**
     * LE CONTRAT PRINCIPAL : le catalogue de design et le registre moteur listent
     * les memes reglages, dans le meme ordre. C'est ce qui autorise l'UI a pousser
     * une valeur par index.
     */
    @Test
    fun leCatalogueSuitLeRegistreMoteur() {
        val engine = FxRegistry.info(FxDesign.SPACE.id)
        assertEquals("nombre de reglages", engine.params.size, space.params.size)
        assertEquals("ordre des reglages", moteur, space.params.map { it.name })
        assertEquals("ordre des reglages (moteur)", moteur, engine.params.map { it.name })
    }

    /**
     * Chaque preset a une valeur par reglage. Une table trop courte laisserait
     * les derniers reglages sur leur defaut, ce qui se lit « le preset ne marche
     * qu'a moitie » et ne se voit pas a l'oeil.
     */
    @Test
    fun chaquePresetCouvreTousLesReglages() {
        space.presets.forEach { (name, table) ->
            assertEquals("$name ne couvre pas les 15 reglages", space.params.size, table.size)
            table.forEachIndexed { i, v ->
                assertTrue("$name reglage $i hors 0..1 : $v", v in 0f..1f)
            }
        }
    }

    /**
     * Les presets du catalogue et ceux du moteur sont les MEMES, dans le meme
     * ordre : `pickPreset` retrouve l'index moteur par `indexOf`, donc un ordre
     * different appliquerait le mauvais preset.
     */
    @Test
    fun lesPresetsSuiventLeRegistreMoteur() {
        val engine = FxRegistry.info(FxDesign.SPACE.id).presets
        assertEquals("ordre des presets", engine.map { it.name }, space.presets.keys.toList())
    }

    /** La carte regle six reglages, et ils existent tous. */
    @Test
    fun lesReglagesDeLaCarteExistent() {
        assertEquals(6, FxDesign.CARD_KEYS.size)
        FxDesign.CARD_KEYS.forEach { key ->
            assertTrue("la carte reference un reglage inconnu : $key", space.byKey.containsKey(key))
        }
    }

    /**
     * Les groupes de la reference couvrent tous les reglages sauf ceux portes par
     * la colonne de gauche (Mix, Pre-delay) et le gros potard (Decay). Un reglage
     * ni groupe ni place serait invisible : il ne se reglerait nulle part.
     */
    @Test
    fun chaqueReglageEstPlaceQuelquePart() {
        val grouped = space.groups.flatMap { it.keys }.toSet()
        val placed = grouped + setOf("mix", "pre", "decay")
        space.params.forEach { p ->
            assertTrue("reglage ${p.key} n'est ni groupe ni pose", p.key in placed)
        }
        // Et aucun groupe ne renvoie dans le vide.
        grouped.forEach { k -> assertTrue("groupe vers un reglage inconnu : $k", space.byKey.containsKey(k)) }
    }

    /** Chaque preset a un motif circulaire distinct : sinon deux presets se ressemblent. */
    @Test
    fun chaquePresetAUnMotif() {
        val styles = space.presets.keys.map { spaceStyleFor(it) }
        assertEquals("deux presets partagent le meme motif", styles.size, styles.distinct().size)
        assertEquals("il faut un motif par preset", 10, SpaceStyle.entries.size)
        // Un nom inconnu ne doit pas lever : il retombe sur un motif.
        assertNotNull(spaceStyleFor("preset inconnu"))
    }

    /**
     * La geometrie : trois colonnes de cellules. A deux colonnes les rangees
     * ecrasaient le potard (52 unites mesurees au lieu de 96) sans que rien ne le
     * signale — d'ou ce test.
     */
    @Test
    fun laGeometrieDesCellulesEstCelleDessinee() {
        assertEquals(3, FxGeo.CELLS)
        assertTrue("le potard doit rester plus grand que la cible de texte", FxGeo.KNOB > FxGeo.KNOB_TEXT)
        assertTrue("le gros potard doit dominer les petits", FxGeo.KNOB_MAIN > FxGeo.KNOB * 2)
        assertTrue("la colonne de gauche doit tenir dans la fenetre", FxGeo.COL_L < FxGeo.W)
    }

    /** Le catalogue ne propose que ce qui existe : Space en audio, l'ARP en MIDI. */
    @Test
    fun leCatalogueNeProposeQueDesEffetsReels() {
        assertEquals(listOf(FxDesign.SPACE.id), FxDesign.audio.map { it.id })
        assertEquals(listOf(FxDesign.ARP.id), FxDesign.midi.map { it.id })
        assertEquals(FxKindD.AUDIO, FxDesign.SPACE.kind)
        assertEquals(FxKindD.MIDI, FxDesign.ARP.kind)
    }
}

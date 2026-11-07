package com.soundstage.mixer

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.soundstage.mixer.viewmodel.MixerViewModel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Au demarrage, l'app doit rouvrir la setlist qui etait ouverte a la fermeture,
 * et avec elle son patch actif.
 *
 * L'etat persiste porte le nom (`active_project_name`) ; la setlist elle-meme vit
 * dans un fichier `.lkproj` sous `Setlists/`. La restauration a donc besoin des
 * DEUX : le nom pour savoir quoi ouvrir, le fichier pour que le scan le trouve.
 * On monte ici le cas complet — un fichier sur disque, la preference posee, puis
 * un second ViewModel comme au relancement de l'app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BootSetlistRestoreTest {

    private val ctx: Application = ApplicationProvider.getApplicationContext()

    /**
     * Le boot s'execute en tache de fond et la derniere etape (`openProject`)
     * repasse sur le thread principal. Un simple `sleep` ne fait pas tourner le
     * looper Robolectric : il faut le pomper, sinon la restauration reste en
     * attente d'un thread principal qui ne s'execute jamais.
     */
    private fun awaitBoot() {
        val deadline = System.currentTimeMillis() + 3_000
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
        }
    }

    /**
     * Un blob d'etat minimal mais VALIDE : `loadAppState` refuse un etat sans
     * `bpm` ni `tracks_json` (un premier lancement n'en a pas). C'est le seul
     * moyen de faire croire au ViewModel qu'il a deja tourne.
     */
    private fun seedAppState(projectName: String) {
        ctx.getSharedPreferences("livekeys_mixer_prefs", Context.MODE_PRIVATE).edit()
            .putInt("bpm", 120)
            .putString("tracks_json", "[]")
            .putString("active_project_name", projectName)
            .commit()
    }

    /**
     * Le coeur de la demande : la setlist ET le patch actifs reviennent au
     * demarrage. C'est ce qui ramene aussi le rack FX, qui appartient au patch.
     */
    @Test
    fun the_last_opened_setlist_and_patch_come_back_at_boot() {
        // 1. Une setlist sur disque, avec son patch actif.
        val seed = MixerViewModel(ctx)
        awaitBoot()
        val dir = seed.fileManager.setlistsDir
        dir.mkdirs()
        java.io.File(dir, "BootShow.lkproj").writeText(
            """{"patches":[{"id":"patch_boot","name":"Patch Boot"}],"activePatchId":"patch_boot","tracks":[]}""",
            Charsets.UTF_8,
        )

        // 2. Ce que l'app avait laisse : cette setlist etait ouverte.
        seedAppState("BootShow")

        // 3. Relancement : un ViewModel neuf, sur les memes preferences et le meme disque.
        val booted = MixerViewModel(ctx)
        awaitBoot()

        assertEquals(
            "la setlist ouverte a la fermeture doit revenir au demarrage",
            "BootShow", booted.uiState.value.activeProjectName,
        )
        assertEquals(
            "et son patch actif avec elle : c'est lui qui porte le rack FX",
            "patch_boot", booted.uiState.value.activePatchId,
        )
    }

    /**
     * Une setlist supprimee entre les deux lancements ne doit pas etre rouverte :
     * la restauration ne pose que ce que le scan a trouve.
     */
    @Test
    fun a_deleted_setlist_is_not_reopened() {
        seedAppState("Disparue")

        val booted = MixerViewModel(ctx)
        awaitBoot()

        assertEquals(
            "une setlist absente du disque ne doit pas etre ouverte",
            "", booted.uiState.value.activeProjectName,
        )
    }
}

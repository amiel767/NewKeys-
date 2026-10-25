package com.soundstage.mixer.liveui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.printToString
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.soundstage.mixer.model.ActivePopup
import com.soundstage.mixer.model.AppScreenPage
import com.soundstage.mixer.ui.MixerScreen
import com.aetherkeys.fx.ui.FxEngine
import com.aetherkeys.fx.ui.FxShellStore
import com.aetherkeys.fx.ui.FxWindowHost
import com.aetherkeys.fx.ui.fxModules
import com.soundstage.mixer.ui.theme.DarkBg
import com.soundstage.mixer.ui.theme.SoundfontLiveMixerTheme
import com.soundstage.mixer.viewmodel.MixerViewModel
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * LiveUiLoop — renders the real Compose UI and lets an external process drive it.
 *
 * Reads [dir]/cmd.json, applies navigation/clicks, re-renders to [dir]/screen.png
 * and beats [dir]/status.txt. No emulator, no audio: Robolectric draws the real
 * composables off-screen. The loop never returns on purpose; kill the process to stop.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w891dp-h411dp-land-xxhdpi", sdk = [36])
class LiveUiLoop {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun run() {
        val dir = File(System.getenv("LIVEUI_DIR") ?: "/tmp/liveui").also { it.mkdirs() }
        val cmdFile = File(dir, "cmd.json")
        val imgFile = File(dir, "screen.png")
        val statusFile = File(dir, "status.txt")

        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MixerViewModel(app)

        composeTestRule.mainClock.autoAdvance = false
        // Preview mode renders the real FX composables directly. The native bridge is
        // absent under Robolectric, so FxController.setEffect() refuses every id; the
        // FX state is therefore seeded through the controller's public lists.
        val fxPreview = mutableStateOf("")
        composeTestRule.setContent {
            SoundfontLiveMixerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = DarkBg) {
                    when {
                        // FX previews render the ported shell directly: the same
                        // FxShellStore/FxWindowHost the app now uses for Effects.
                        fxPreview.value == "add" -> FxPreviewSurface(Modifier.padding(24.dp), Alignment.Center) {
                            val store = remember { previewStore("add") }
                            FxWindowHost(store = store, modifier = Modifier.fillMaxSize())
                        }
                        fxPreview.value.isNotEmpty() -> FxPreviewSurface(
                            Modifier.padding(horizontal = 12.dp).padding(top = 38.dp, bottom = 10.dp),
                        ) {
                            val store = remember(fxPreview.value) { previewStore(fxPreview.value) }
                            FxWindowHost(store = store, modifier = Modifier.fillMaxSize())
                        }
                        else -> MixerScreen(viewModel = viewModel, skipSplash = true)
                    }
                }
            }
        }

        // Capture into a temp file then rename, so readers never see a half-written PNG.
        val tmpFile = File(dir, "screen.tmp.png")
        var lastSeq = -1L
        statusFile.writeText("ready")
        while (true) {
            try {
                val raw = if (cmdFile.exists()) cmdFile.readText() else ""
                var paused = false
                if (raw.isNotBlank()) {
                    val o = JSONObject(raw)
                    val seq = o.optLong("seq", 0L)
                    if (seq != lastSeq) {
                        lastSeq = seq
                        o.optString("fxPreview").takeIf { it.isNotBlank() }?.let { fxPreview.value = it }
                        o.optString("page").takeIf { it.isNotBlank() }?.let { p ->
                            runCatching { viewModel.navigateToPage(AppScreenPage.valueOf(p)) }
                        }
                        o.optString("popup").takeIf { it.isNotBlank() }?.let { p ->
                            runCatching {
                                val popup = ActivePopup.valueOf(p)
                                if (popup == ActivePopup.NONE) viewModel.closePopup() else viewModel.openPopup(popup)
                            }
                        }
                        o.optJSONArray("clicks")?.let { arr ->
                            for (i in 0 until arr.length()) {
                                val pt = arr.getJSONArray(i)
                                val x = pt.getDouble(0).toFloat()
                                val y = pt.getDouble(1).toFloat()
                                runCatching {
                                    composeTestRule.onRoot().performTouchInput { click(Offset(x, y)) }
                                }
                            }
                        }
                        // Click controls by their visible label: far more robust than
                        // raw coordinates when the layout is centred or scrolls.
                        o.optJSONArray("clickText")?.let { arr ->
                            for (i in 0 until arr.length()) {
                                val label = arr.getString(i)
                                try {
                                    // Invoke the click action itself: a synthetic tap can
                                    // miss a node that sits under a scroll/pointer-input
                                    // wrapper, which is exactly the case in the FX rack.
                                    val node = composeTestRule.onAllNodesWithText(label)[0]
                                    node.performSemanticsAction(SemanticsActions.OnClick)
                                } catch (t: Throwable) {
                                    File(dir, "clickerr.txt").writeText("$label: ${t.javaClass.simpleName}: ${t.message}")
                                }
                            }
                        }
                        // Fail the sequence (so the driver cannot treat a stale frame
                        // as a pass) when an expected label is missing from the tree.
                        o.optString("assertText").takeIf { it.isNotBlank() }?.let { want ->
                            if (composeTestRule.onAllNodesWithText(want).fetchSemanticsNodes().isEmpty()) {
                                throw IllegalStateException("missing text: $want")
                            }
                        }
                        // DrumPad page: the DrumPad / LiveLooper switch is local Compose
                        // state, so it can only be driven through the page selector itself.
                        o.optString("drumpadMode").takeIf { it.isNotBlank() }?.let { m ->
                            runCatching {
                                val label = if (m == "loops") "LiveLooper" else "DrumPad"
                                composeTestRule.onAllNodesWithText(label)[0].performClick()
                            }
                        }
                        // Dump the semantics tree for offline inspection when a capture
                        // looks empty: the quickest way to tell a missing node from an
                        // invisible one.
                        if (o.optBoolean("dumpTree", false)) {
                            runCatching {
                                File(dir, "tree.txt").writeText(
                                    composeTestRule.onRoot(useUnmergedTree = true).printToString(maxDepth = 30))
                            }
                        }
                        // Grow the mixer to N visible tracks (JSX reference draws 4).
                        o.optInt("tracks", 0).takeIf { it > 0 }?.let { n ->
                            runCatching {
                                while (viewModel.uiState.value.visibleTracksCount < n) {
                                    viewModel.addSoundTrack()
                                }
                            }
                        }
                        o.optJSONArray("keys")?.let { arr ->
                            runCatching {
                                val keys = (0 until arr.length()).map { arr.getString(it) }
                                viewModel.uiState.value.pressedKeys.toList().forEach { viewModel.onKeyUp(it) }
                                keys.forEach { viewModel.onKeyDown(it) }
                            }
                        }
                        o.optBoolean("sustain", false).let { on ->
                            runCatching { if (viewModel.uiState.value.isSustainActive != on) viewModel.toggleSustain() }
                        }
                        // Simulate the external MIDI pedal (CC64) so the pedal animation can be captured.
                        o.optBoolean("midiPedal", false).let { on ->
                            runCatching { if (viewModel.uiState.value.isMidiPedalPressed != on) viewModel.debugSetMidiPedal(on) }
                        }
                        o.optBoolean("layer", false).let { on ->
                            runCatching { if (viewModel.uiState.value.isKeyboardLayerExpanded != on) viewModel.toggleKeyboardLayer() }
                        }
                        o.optBoolean("keyboard", false).let { on ->
                            runCatching { viewModel.setKeyboardHeightFraction(if (on) 1f else 0f) }
                        }
                    }
                    paused = o.optBoolean("pause", false)
                }
                composeTestRule.mainClock.advanceTimeBy(16)
                if (!paused) {
                    // Popups and dialogs compose into their own roots; capturing only
                    // the first one would silently drop them. Write every root so the
                    // driver can composite the real screen.
                    val roots = composeTestRule.onAllNodes(isRoot()).fetchSemanticsNodes()
                    roots.indices.forEach { i ->
                        composeTestRule.onAllNodes(isRoot())[i]
                            .captureRoboImage(File(dir, "screen_r$i.png").absolutePath)
                    }
                    File(dir, "roots.txt").writeText(roots.size.toString())
                    composeTestRule.onRoot().captureRoboImage(tmpFile.absolutePath)
                    tmpFile.renameTo(imgFile)
                    // Echo the applied sequence back: the driver waits on this so it can
                    // never copy a stale frame from the previous command.
                    statusFile.writeText("ok $lastSeq ${System.currentTimeMillis()}")
                }
            } catch (t: Throwable) {
                runCatching { statusFile.writeText("err $lastSeq ${t.javaClass.simpleName}: ${t.message}") }
            }
            Thread.sleep(150)
        }
    }
}

/**
 * Builds a store for the FX previews without the native bridge. Under Robolectric
 * the bridge is absent, so FxController refuses every id; the store is driven
 * directly instead. `fxPreview` values: "rack", "add", or "window:<id>".
 */
private fun previewStore(key: String): FxShellStore {
    val store = FxShellStore(fxModules(), object : FxEngine {
        override fun setEffect(strip: Int, slot: Int, id: Int) = true
        override fun setInstrument(strip: Int, id: Int) = true
        override fun setParam(strip: Int, slot: Int, index: Int, value: Float) = Unit
        override fun setInstrumentParam(strip: Int, index: Int, value: Float) = Unit
        override fun setBypass(strip: Int, slot: Int, bypassed: Boolean) = Unit
        override fun applyPreset(strip: Int, slot: Int, presetIndex: Int) = true
        override fun meter(strip: Int, slot: Int, index: Int) = 0f
    })
    when {
        key == "add" -> store.addOpen = true
        key.startsWith("window:") -> {
            val id = key.removePrefix("window:").toIntOrNull() ?: 0
            store.addEffect(id)
            store.openId = id
        }
        else -> listOf(10, 0, 1).forEach { store.addEffect(it) }
    }
    return store
}

/** Full-bleed backdrop matching the FX overlay, so the preview frames are readable. */
@Composable
private fun FxPreviewSurface(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(Color(0xFF05070C)).then(modifier),
        contentAlignment = contentAlignment,
    ) { content() }
}

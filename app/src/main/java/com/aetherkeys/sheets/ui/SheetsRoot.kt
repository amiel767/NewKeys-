package com.aetherkeys.sheets.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aetherkeys.sheets.data.SheetsRepository
import com.aetherkeys.sheets.export.ChordProExporter
import com.aetherkeys.sheets.model.ExportFormat
import com.aetherkeys.sheets.ui.editor.EditorScreen
import com.aetherkeys.sheets.ui.home.HomeScreen
import com.aetherkeys.sheets.ui.settings.SheetsSettingsScreen
import com.aetherkeys.sheets.vm.SheetEditorViewModel
import com.aetherkeys.sheets.vm.SheetsLibraryViewModel

class SheetsViewModelFactory(private val repo: SheetsRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(SheetsLibraryViewModel::class.java) -> SheetsLibraryViewModel(repo) as T
        modelClass.isAssignableFrom(SheetEditorViewModel::class.java) -> SheetEditorViewModel(repo) as T
        else -> error("Unknown ViewModel: ${modelClass.name}")
    }
}

/**
 * Point d'entrée unique de Sheets. À appeler depuis la case d'entrée du mixer (qui ne change pas).
 * [repo] = new SheetsRepository(<dossier Sheets existant>). [bottomInset] = hauteur du mixer à laisser libre.
 */
@Composable
fun SheetsRoot(
    repo: SheetsRepository,
    bottomInset: Dp,
    onBackToMixer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val factory = remember(repo) { SheetsViewModelFactory(repo) }
    val lib: SheetsLibraryViewModel = viewModel(factory = factory)
    val ed: SheetEditorViewModel = viewModel(factory = factory)
    val libState by lib.state.collectAsState()
    val edState by ed.state.collectAsState()
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { lib.refresh() }
    LaunchedEffect(editingId) { editingId?.let { ed.load(it) } }

    fun closeEditor() { editingId = null; lib.refresh() }

    BackHandler(enabled = libState.floatingId != null) { lib.closeFloating() }
    BackHandler(enabled = editingId != null) { closeEditor() }
    BackHandler(enabled = showSettings) { showSettings = false }

    Box(modifier.fillMaxSize()) {
        if (editingId == null) {
            HomeScreen(
                state = libState, actions = lib, bottomInset = bottomInset,
                onBackToMixer = onBackToMixer, onOpenSettings = { showSettings = true },
                onOpenEditor = { id -> lib.closeFloating(); editingId = id },
            )
        } else {
            EditorScreen(
                state = edState, settings = libState.settings, actions = ed,
                onBack = { closeEditor() },
                // Mode scène : feuille flottante (swipe entre sections, verrou).
                onOpenScene = { val id = editingId; closeEditor(); if (id != null) lib.openFloating(id) },
                onSetNotation = lib::setNotation,
                onExport = { f ->
                    val sheet = edState.sheet
                    if (sheet != null && f == ExportFormat.ChordPro) {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, sheet.title)
                            putExtra(Intent.EXTRA_TEXT, ChordProExporter.format(sheet))
                        }
                        ctx.startActivity(Intent.createChooser(send, null))
                    } else {
                        Toast.makeText(ctx, "${f.name} export is not available yet", Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }
        if (showSettings) SheetsSettingsScreen(libState.settings, lib, onBack = { showSettings = false })
    }
}

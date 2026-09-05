package com.example

import android.os.Bundle
import android.os.Build
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MixerScreen
import com.example.ui.theme.DarkBg
import com.example.ui.theme.SoundfontLiveMixerTheme
import com.example.viewmodel.MixerViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  private val viewModel: MixerViewModel by viewModels()

  private var showPermissionErrorDialog by mutableStateOf(false)

  private val requestPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    val allGranted = permissions.entries.all { it.value }
    if (allGranted) {
      setupFileSystem()
    } else {
      showPermissionErrorDialog = true
    }
  }

  private val requestManageStorageLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
  ) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      if (android.os.Environment.isExternalStorageManager()) {
        setupFileSystem()
      } else {
        showPermissionErrorDialog = true
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    WindowCompat.setDecorFitsSystemWindows(window, false)

    checkAndRequestPermissions()

    setContent {
      val uiState by viewModel.uiState.collectAsStateWithLifecycle()
      SoundfontLiveMixerTheme(appTheme = uiState.currentTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
          MixerScreen(viewModel = viewModel)
          
          if (showPermissionErrorDialog) {
            AlertDialog(
              onDismissRequest = { /* Cannot dismiss, required for app */ },
              title = { Text("Permissions Requises") },
              text = { Text("L'application nécessite un accès au stockage pour créer le dossier /LiveKeys/ et lire les SoundFonts. Sans cette autorisation, l'application ne peut pas fonctionner.") },
              confirmButton = {
                TextButton(onClick = {
                  showPermissionErrorDialog = false
                  checkAndRequestPermissions()
                }) {
                  Text("Réessayer")
                }
              }
            )
          }
        }
      }
    }

    window.decorView.post {
      val insetsController = WindowCompat.getInsetsController(window, window.decorView)
      insetsController.systemBarsBehavior =
          WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      insetsController.hide(WindowInsetsCompat.Type.systemBars())
    }
  }

  private fun checkAndRequestPermissions() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      if (!android.os.Environment.isExternalStorageManager()) {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
        intent.data = Uri.parse("package:$packageName")
        requestManageStorageLauncher.launch(intent)
      } else {
        setupFileSystem()
      }
    } else {
      val permissions = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
      )
      val notGranted = permissions.filter {
        ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
      }
      if (notGranted.isNotEmpty()) {
        requestPermissionLauncher.launch(notGranted.toTypedArray())
      } else {
        setupFileSystem()
      }
    }
  }

  private fun setupFileSystem() {
    val fileManager = com.example.model.FileManager(this)
    // Ensures primary LiveKeys directory and all subfolders exist
    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
      fileManager.ensureDirectoriesExist()
      viewModel.refreshStorageFiles()
    }
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) {
      val insetsController = WindowCompat.getInsetsController(window, window.decorView)
      insetsController.hide(WindowInsetsCompat.Type.systemBars())
    }
  }
}


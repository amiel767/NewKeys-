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

  private var showPermissionInfoSnackbar by mutableStateOf(false)

  private val requestPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { _ ->
    setupFileSystem()
  }

  private val requestManageStorageLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
  ) {
    setupFileSystem()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    WindowCompat.setDecorFitsSystemWindows(window, false)

    // Ensure storage structure exists immediately and check permissions
    setupFileSystem()
    checkStoragePermissions()

    setContent {
      val uiState by viewModel.uiState.collectAsStateWithLifecycle()
      SoundfontLiveMixerTheme(appTheme = uiState.currentTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
          MixerScreen(viewModel = viewModel)
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

  private fun setupFileSystem() {
    val fileManager = com.example.model.FileManager(this)
    // Ensures primary SoundStage directory and all subfolders exist
    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
      try {
        fileManager.ensureDirectoriesExist()
        viewModel.refreshStorageFiles()
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  private fun checkStoragePermissions() {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        if (!android.os.Environment.isExternalStorageManager()) {
          try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
              data = Uri.parse("package:$packageName")
            }
            requestManageStorageLauncher.launch(intent)
          } catch (_: Exception) {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            requestManageStorageLauncher.launch(intent)
          }
        }
      } else {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
          permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
          permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (permissionsToRequest.isNotEmpty()) {
          requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
      }
    } catch (_: Exception) {}
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) {
      val insetsController = WindowCompat.getInsetsController(window, window.decorView)
      insetsController.hide(WindowInsetsCompat.Type.systemBars())
    }
  }
}


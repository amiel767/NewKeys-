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

    // Ensure storage structure exists immediately
    setupFileSystem()
    checkAndRequestPermissions()

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

  private fun checkAndRequestPermissions() {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        if (!android.os.Environment.isExternalStorageManager()) {
          try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
              data = Uri.parse("package:$packageName")
            }
            requestManageStorageLauncher.launch(intent)
          } catch (e: Exception) {
            try {
              val fallbackIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
              requestManageStorageLauncher.launch(fallbackIntent)
            } catch (e2: Exception) {
              // Ignore if settings screen not available on virtual environment
            }
          }
        }
      } else {
        val permissions = mutableListOf(
          Manifest.permission.READ_EXTERNAL_STORAGE,
          Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        }
        val notGranted = permissions.filter {
          ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) {
          requestPermissionLauncher.launch(notGranted.toTypedArray())
        }
      }
    } catch (e: Exception) {
      // Non-blocking catch
    }
  }

  private fun setupFileSystem() {
    val fileManager = com.example.model.FileManager(this)
    // Ensures primary LiveKeys directory and all subfolders exist
    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
      try {
        fileManager.ensureDirectoriesExist()
        viewModel.refreshStorageFiles()
      } catch (e: Exception) {
        e.printStackTrace()
      }
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


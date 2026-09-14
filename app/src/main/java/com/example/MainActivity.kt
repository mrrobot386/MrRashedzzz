package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.MahiScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MahiViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: MahiViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsState()

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    viewModel.setMicPermissionGranted(isGranted)
                    if (isGranted) {
                        viewModel.startSession()
                    }
                }

                LaunchedEffect(Unit) {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    viewModel.setMicPermissionGranted(hasPermission)
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
                    MahiScreen(
                        state = uiState,
                        onStartSession = {
                            if (uiState.hasMicPermission) {
                                viewModel.startSession()
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onStopSession = { viewModel.stopSession() },
                        onInterrupt = { viewModel.triggerInterruption() },
                        onToggleMute = { viewModel.toggleMute() },
                        onSendQuickPrompt = { prompt -> viewModel.sendQuickVoicePrompt(prompt) },
                        onDismissError = { viewModel.clearError() }
                    )
                }
            }
        }
    }
}


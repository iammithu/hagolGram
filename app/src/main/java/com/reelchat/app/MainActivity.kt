package com.reelchat.app

import android.Manifest
import android.content.Intent
import android.graphics.Color as AColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {
    private val shared = mutableStateOf<String?>(null)
    private val askNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= 33) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        handle(intent)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = IgBlue, background = Color.Black, surface = Color.Black,
                    surfaceVariant = IgCard, onSurface = Color.White, onBackground = Color.White,
                    secondaryContainer = IgCard, primaryContainer = Color(0xFF1B3A57)
                )
            ) {
                Surface(Modifier.fillMaxSize(), color = Color.Black) { App(shared) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    // Instagram "Share -> hagolGram" lands here
    private fun handle(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND) shared.value = i.getStringExtra(Intent.EXTRA_TEXT)
    }
}

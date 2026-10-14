package com.reelchat.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
        if (Build.VERSION.SDK_INT >= 33) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        handle(intent)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFE1306C))) {
                Surface(Modifier.fillMaxSize()) { App(shared) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    // Instagram "Share -> ReelChat" lands here
    private fun handle(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND) shared.value = i.getStringExtra(Intent.EXTRA_TEXT)
    }
}

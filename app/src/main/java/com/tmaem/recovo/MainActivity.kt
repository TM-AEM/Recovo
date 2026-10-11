package com.tmaem.recovo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tmaem.recovo.core.ui.RecovoApp
import com.tmaem.recovo.ui.theme.RecovoTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      RecovoTheme {
        RecovoApp()
      }
    }
  }
}


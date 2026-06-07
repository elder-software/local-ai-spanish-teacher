package com.example.localllmvoice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.localllmvoice.navigation.SoloTalkNavHost
import com.example.localllmvoice.ui.theme.LocalLLMVoiceTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as SoloTalkApplication).appContainer

        setContent {
            LocalLLMVoiceTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SoloTalkNavHost(appContainer = appContainer)
                }
            }
        }
    }
}

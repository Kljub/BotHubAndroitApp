package com.bothub.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.bothub.app.ui.AppRoot
import com.bothub.app.ui.BotHubTheme

class MainActivity : ComponentActivity() {
    /** A bothub://pair link that opened the app (QR code scanned by the camera app). */
    private val deepLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLink.value = intent?.dataString
        val session = (application as BotHubApp).session
        setContent {
            BotHubTheme {
                AppRoot(session, deepLink.value) { deepLink.value = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deepLink.value = intent.dataString
    }
}

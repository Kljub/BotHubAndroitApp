package com.bothub.app

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.bothub.app.ui.AppRoot
import com.bothub.app.ui.BotHubTheme
import com.bothub.app.ui.LockScreen

/** FragmentActivity: the biometric prompt needs it. */
class MainActivity : FragmentActivity() {
    /** A bothub://pair link that opened the app (QR code scanned by the camera app). */
    private val deepLink = mutableStateOf<String?>(null)
    private val locked = mutableStateOf(false)
    private var stoppedAt = 0L

    private val session get() = (application as BotHubApp).session

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLink.value = intent?.dataString
        locked.value = session.lock.enabled
        setContent {
            BotHubTheme {
                // The surface gives every screen (also pairing, outside the Scaffold)
                // the theme's background and text colour.
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (locked.value) {
                        LockScreen(session.lock) { locked.value = false }
                    } else {
                        AppRoot(session, deepLink.value) { deepLink.value = null }
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        if (stoppedAt > 0 && session.lock.shouldLock(SystemClock.elapsedRealtime() - stoppedAt)) locked.value = true
        // With a lock, the app's content stays out of screenshots and the app switcher.
        if (session.lock.enabled) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deepLink.value = intent.dataString
    }
}

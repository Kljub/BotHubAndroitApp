package com.bothub.app.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.bothub.app.R
import com.bothub.core.PinLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Biometrics the phone can use now (fingerprint, face …). */
fun biometricReady(activity: FragmentActivity): Boolean =
    BiometricManager.from(activity).canAuthenticate(BIOMETRIC_STRONG or BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

/** Shows the system prompt; [onOk] after a match. */
fun askBiometric(activity: FragmentActivity, title: String, cancel: String, onOk: () -> Unit) {
    val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onOk()
    })
    prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(title).setNegativeButtonText(cancel)
        .setAllowedAuthenticators(BIOMETRIC_STRONG or BIOMETRIC_WEAK).build())
}

/** A 4-digit PIN pad: dots, digits, delete and (optionally) fingerprint. */
@Composable
fun PinPad(title: String, message: String?, onBiometric: (() -> Unit)?, onPin: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Text("🔒", fontSize = 40.sp)
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(4) { i ->
                Box(Modifier.size(16.dp).clip(CircleShape)
                    .then(if (i < pin.length) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier.border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)))
            }
        }
        Text(message ?: " ", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "bio", "0", "del")
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { k ->
                    Box(Modifier.size(72.dp).clip(CircleShape).then(
                        when {
                            k == "bio" && onBiometric == null -> Modifier
                            else -> Modifier.background(MaterialTheme.colorScheme.surfaceVariant).clickable {
                                when (k) {
                                    "del" -> pin = pin.dropLast(1)
                                    "bio" -> onBiometric?.invoke()
                                    else -> if (pin.length < 4) {
                                        pin += k
                                        if (pin.length == 4) { onPin(pin); pin = "" }
                                    }
                                }
                            }
                        }
                    ), contentAlignment = Alignment.Center) {
                        when (k) {
                            "del" -> Icon(Icons.AutoMirrored.Filled.Backspace, stringResource(R.string.lock_delete))
                            "bio" -> if (onBiometric != null) Icon(Icons.Filled.Fingerprint, stringResource(R.string.lock_biometric))
                            else -> Text(k, fontSize = 26.sp)
                        }
                    }
                }
            }
        }
    }
}

/** The lock in front of the app: PIN, or biometrics when switched on (asked at once). */
@Composable
fun LockScreen(lock: PinLock, onUnlock: () -> Unit) {
    val activity = LocalContext.current as FragmentActivity
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    val title = stringResource(R.string.lock_title)
    val cancel = stringResource(R.string.lock_use_pin)
    val wrongText = stringResource(R.string.lock_wrong)
    val waitText = stringResource(R.string.lock_wait)
    val bio = if (lock.biometric && biometricReady(activity)) ({
        askBiometric(activity, title, cancel) { lock.biometricOk(); onUnlock() }
    }) else null
    LaunchedEffect(Unit) { bio?.invoke() }
    PinPad(title, message, bio) { pin ->
        scope.launch {
            when (val r = withContext(Dispatchers.Default) { lock.check(pin) }) {
                PinLock.Result.Ok -> onUnlock()
                is PinLock.Result.Wrong -> message = wrongText.format(r.triesLeft)
                is PinLock.Result.Wait -> message = waitText.format((r.ms + 999) / 1000)
            }
        }
    }
}

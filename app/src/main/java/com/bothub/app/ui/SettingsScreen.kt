package com.bothub.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import com.bothub.core.PinLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.unit.dp
import com.bothub.app.BuildConfig
import com.bothub.app.R
import com.bothub.app.Session
import com.bothub.app.Updater
import com.bothub.core.Updates
import kotlinx.coroutines.launch

private val events = listOf("bot_offline" to R.string.notify_bot_offline, "bot_error" to R.string.notify_bot_error, "update" to R.string.notify_update)

/** Account and phone, push events, app updates, unpairing. */
@Composable
fun SettingsScreen(session: Session, modifier: Modifier, onUpdate: (Updates.Release) -> Unit) {
    val pairing by session.pairing.collectAsState()
    var notify by remember { mutableStateOf<List<String>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmUnpair by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val upToDate = stringResource(R.string.settings_up_to_date)

    LaunchedEffect(Unit) { session.call { it.me() }.onSuccess { notify = it.device.notify } }
    val activity = LocalContext.current as FragmentActivity
    var lockOn by remember { mutableStateOf(session.lock.enabled) }
    var bioOn by remember { mutableStateOf(session.lock.biometric) }
    var timeout by remember { mutableStateOf(session.lock.timeout) }
    // PIN setup: first entry, then the same again.
    var pinStep by remember { mutableStateOf(0) }
    var firstPin by remember { mutableStateOf("") }
    var pinMessage by remember { mutableStateOf<String?>(null) }
    val mismatch = stringResource(R.string.lock_mismatch)
    if (pinStep > 0) {
        BackHandler { pinStep = 0 }
        PinPad(stringResource(if (pinStep == 1) R.string.lock_new_pin else R.string.lock_repeat_pin), pinMessage, null) { pin ->
            if (pinStep == 1) { firstPin = pin; pinMessage = null; pinStep = 2 }
            else if (pin == firstPin) {
                scope.launch { withContext(Dispatchers.Default) { session.lock.setPin(pin) }; lockOn = true; pinStep = 0 }
            } else { pinMessage = mismatch; pinStep = 1 }
        }
        return
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.headlineSmall)
        pairing?.let {
            Text(stringResource(R.string.settings_account, it.user, it.server))
            Text(stringResource(R.string.settings_device, it.deviceName), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider()
        Text(stringResource(R.string.settings_notify), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.settings_notify_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
        notify?.let { current ->
            events.forEach { (key, label) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(key in current, { on ->
                        val next = if (on) current + key else current - key
                        notify = next
                        scope.launch { session.call { it.setPush("", next) }.onFailure { message = session.errorText(it) } }
                    })
                    Text(stringResource(label))
                }
            }
        }
        HorizontalDivider()
        Text(stringResource(R.string.lock_section), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.lock_section_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
        SwitchRow(stringResource(R.string.lock_pin), lockOn) { on ->
            if (on) { pinMessage = null; pinStep = 1 } else { session.lock.disable(); lockOn = false; bioOn = false }
        }
        if (lockOn) {
            OutlinedButton({ pinMessage = null; pinStep = 1 }) { Text(stringResource(R.string.lock_change_pin)) }
            if (biometricReady(activity)) SwitchRow(stringResource(R.string.lock_use_biometric), bioOn) { on ->
                if (on) askBiometric(activity, activity.getString(R.string.lock_use_biometric), activity.getString(R.string.cancel)) { session.lock.biometric = true; bioOn = true }
                else { session.lock.biometric = false; bioOn = false }
            }
            Text(stringResource(R.string.lock_after), style = MaterialTheme.typography.labelLarge)
            PinLock.Timeout.entries.forEach { t ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(timeout == t, { session.lock.timeout = t; timeout = t })
                    Text(stringResource(when (t) { PinLock.Timeout.IMMEDIATELY -> R.string.lock_now; PinLock.Timeout.MINUTE -> R.string.lock_1min; PinLock.Timeout.FIVE_MINUTES -> R.string.lock_5min }))
                }
            }
        }
        HorizontalDivider()
        Text(stringResource(R.string.settings_updates), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME))
        OutlinedButton({
            scope.launch { Updater.check(session, force = true)?.let(onUpdate) ?: run { message = upToDate } }
        }) { Text(stringResource(R.string.settings_check)) }
        HorizontalDivider()
        OutlinedButton({ confirmUnpair = true }) { Text(stringResource(R.string.settings_unpair), color = MaterialTheme.colorScheme.error) }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }

    if (confirmUnpair) AlertDialog(
        onDismissRequest = { confirmUnpair = false },
        text = { Text(stringResource(R.string.settings_unpair_confirm)) },
        confirmButton = { TextButton({ confirmUnpair = false; scope.launch { session.unpair() } }) { Text(stringResource(R.string.settings_unpair)) } },
        dismissButton = { TextButton({ confirmUnpair = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun SwitchRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(on, onChange)
    }
}

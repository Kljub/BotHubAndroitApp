package com.bothub.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bothub.app.R
import com.bothub.app.Updater
import com.bothub.core.Updates
import kotlinx.coroutines.launch

/** A new release: notes, then download with progress and the system installer. */
@Composable
fun UpdateDialog(release: Updates.Release, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableIntStateOf(-1) }
    var error by remember { mutableStateOf<String?>(null) }
    val allow = stringResource(R.string.update_allow)

    AlertDialog(
        onDismissRequest = { if (progress < 0) onDismiss() },
        title = { Text(stringResource(R.string.update_title, release.version)) },
        text = {
            Column {
                Text(stringResource(R.string.update_text))
                release.body?.takeIf { it.isNotBlank() }?.let {
                    Text(it, Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall)
                }
                if (progress >= 0) {
                    Text(stringResource(R.string.update_downloading, progress))
                    LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = progress < 0, onClick = {
                if (!Updater.canInstall(context)) {
                    error = allow
                    context.startActivity(Updater.allowInstallIntent(context))
                    return@TextButton
                }
                progress = 0
                scope.launch {
                    runCatching { Updater.download(context, release) { progress = it } }
                        .onSuccess { Updater.install(context, it); onDismiss() }
                        .onFailure { error = it.message; progress = -1 }
                }
            }) { Text(stringResource(R.string.update_install)) }
        },
        dismissButton = { TextButton(onDismiss, enabled = progress < 0) { Text(stringResource(R.string.update_later)) } },
    )
}

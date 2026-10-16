package com.bothub.app.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bothub.app.R
import com.bothub.app.Session
import com.bothub.core.PairLink
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import androidx.activity.compose.rememberLauncherForActivityResult
import kotlinx.coroutines.launch

/**
 * Pairing: scan the dashboard's QR code (or open its link), confirm the
 * phone's name, done. No password is typed on the phone.
 */
@Composable
fun PairScreen(session: Session, deepLink: String?, onDeepLinkUsed: () -> Unit) {
    var link by remember { mutableStateOf<PairLink?>(null) }
    var pasted by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(Build.MODEL.take(40)) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val badLink = stringResource(R.string.pair_bad_link)
    val prompt = stringResource(R.string.pair_scan_prompt)

    fun take(text: String?) {
        val l = text?.let { PairLink.parse(it) }
        if (l == null) error = badLink else { link = l; error = null }
    }
    LaunchedEffect(deepLink) {
        if (deepLink != null) { take(deepLink); onDeepLinkUsed() }
    }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { res -> res.contents?.let { take(it) } }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🤖", style = MaterialTheme.typography.displayMedium)
        Text(stringResource(R.string.pair_title), style = MaterialTheme.typography.headlineSmall)
        val l = link
        if (l == null) {
            Text(stringResource(R.string.pair_intro), textAlign = TextAlign.Center)
            Button(onClick = {
                scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt(prompt).setBeepEnabled(false).setOrientationLocked(false))
            }) {
                Icon(Icons.Filled.QrCodeScanner, null)
                Text("  " + stringResource(R.string.pair_scan))
            }
            OutlinedTextField(pasted, { pasted = it; if (it.startsWith("bothub://")) take(it) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.pair_paste)) }, singleLine = true)
        } else {
            Text(stringResource(R.string.pair_server, l.server))
            if (l.user.isNotEmpty()) Text(stringResource(R.string.pair_user, l.user))
            if (l.insecure) Text(stringResource(R.string.pair_insecure), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            OutlinedTextField(name, { name = it.take(40) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.pair_name)) }, singleLine = true)
            if (busy) CircularProgressIndicator() else Button(enabled = name.isNotBlank(), onClick = {
                busy = true
                scope.launch {
                    runCatching { session.pair(l, name.trim()) }.onFailure { error = session.errorText(it); link = null }
                    busy = false
                }
            }) { Text(stringResource(R.string.pair_confirm)) }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
    }
}

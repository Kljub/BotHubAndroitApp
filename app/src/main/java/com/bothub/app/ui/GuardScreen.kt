package com.bothub.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bothub.app.R
import com.bothub.app.Session
import com.bothub.core.Approval
import com.bothub.core.GuardCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * BotHub Guard: the 6-digit code (new every 30 s) and the sign-ins that
 * wait for this phone (asked every 3 s while the screen is open).
 */
@Composable
fun GuardScreen(session: Session, modifier: Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    var guardOn by remember { mutableStateOf<Boolean?>(null) }
    var pending by remember { mutableStateOf<List<Approval>>(emptyList()) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val approved = stringResource(R.string.guard_approved)
    val denied = stringResource(R.string.guard_denied)

    LaunchedEffect(Unit) {
        while (true) { now = System.currentTimeMillis() / 1000; delay(1000) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            session.call { it.me() }.onSuccess { guardOn = it.device.guard }
            if (guardOn == true) session.call { it.approvals() }.onSuccess { pending = it }
            delay(3000)
        }
    }
    fun decide(a: Approval, ok: Boolean) = scope.launch {
        session.call { it.decide(a.id, ok) }
            .onSuccess { notice = if (ok) approved else denied; pending = pending - a }
            .onFailure { notice = session.errorText(it) }
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.guard_title), style = MaterialTheme.typography.headlineSmall)
        if (guardOn == false) Text(stringResource(R.string.guard_off), color = MaterialTheme.colorScheme.onSurfaceVariant)
        session.guardSecret?.let { secret ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.guard_code), style = MaterialTheme.typography.titleMedium)
                    val code = GuardCode.code(secret, now)
                    Text("${code.take(3)} ${code.drop(3)}", fontSize = 40.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    val left = GuardCode.secondsLeft(now)
                    LinearProgressIndicator(progress = { left / GuardCode.PERIOD.toFloat() }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.guard_code_hint, left.toInt()), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (guardOn == true) {
            Text(stringResource(R.string.guard_pending), style = MaterialTheme.typography.titleMedium)
            if (pending.isEmpty()) Text(stringResource(R.string.guard_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
            pending.forEach { a ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.guard_request, a.ip, a.userAgent.take(80)))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button({ decide(a, true) }) { Text(stringResource(R.string.guard_approve)) }
                            OutlinedButton({ decide(a, false) }) { Text(stringResource(R.string.guard_deny)) }
                        }
                    }
                }
            }
        }
        notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}

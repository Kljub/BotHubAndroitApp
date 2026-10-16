package com.bothub.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bothub.app.R
import com.bothub.app.Session
import com.bothub.core.Bot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun statusColor(status: String): Color = when (status) {
    "running" -> Online
    "error" -> Problem
    "starting", "stopping" -> Brand
    else -> Offline
}

/** The user's bots with live status (every 10 s), start, stop and restart. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotsScreen(session: Session, modifier: Modifier, onOpen: (Long) -> Unit) {
    var bots by remember { mutableStateOf<List<Bot>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        session.call { it.bots() }.onSuccess { bots = it; error = null }.onFailure { error = session.errorText(it) }
    }
    LaunchedEffect(Unit) {
        while (true) { load(); delay(10_000) }
    }
    fun act(block: suspend () -> Unit) = scope.launch {
        runCatching { block() }.onFailure { error = session.errorText(it) }
        delay(800); load()
    }

    PullToRefreshBox(refreshing, { scope.launch { refreshing = true; load(); refreshing = false } }, modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                val list = bots.orEmpty()
                Text(stringResource(R.string.tab_bots), style = MaterialTheme.typography.headlineSmall)
                if (bots != null) Text(stringResource(R.string.bots_online, list.count { it.running }, list.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            if (bots?.isEmpty() == true) item { Text(stringResource(R.string.bots_empty)) }
            items(bots.orEmpty(), key = { it.id }) { bot ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(bot.id) }) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(statusColor(bot.status)))
                            Spacer(Modifier.width(8.dp))
                            Text(bot.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            AssistChip(onClick = {}, label = { Text(session.text("bot.status.${bot.status}")) })
                        }
                        Text(stringResource(R.string.bot_servers, bot.guildCount), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        bot.statusErrorKey?.let { Text(session.text(it), color = MaterialTheme.colorScheme.error) }
                        Row {
                            if (bot.running) {
                                TextButton({ act { session.call { it.stop(bot.id) }.getOrThrow() } }) { Text(stringResource(R.string.bot_stop)) }
                                TextButton({ act { session.call { it.restart(bot.id) }.getOrThrow() } }) { Text(stringResource(R.string.bot_restart)) }
                            } else {
                                TextButton({ act { session.call { it.start(bot.id) }.getOrThrow() } }) { Text(stringResource(R.string.bot_start)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

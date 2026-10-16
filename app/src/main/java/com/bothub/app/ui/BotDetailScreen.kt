package com.bothub.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.bothub.app.R
import com.bothub.app.Session
import com.bothub.core.Bot
import com.bothub.core.LogEntry
import com.bothub.core.ModuleState
import com.bothub.core.PluginState
import kotlinx.coroutines.launch

/** One bot: its log, and its modules and plugins switched on or off. */
@Composable
fun BotDetailScreen(session: Session, id: Long, modifier: Modifier, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var bot by remember { mutableStateOf<Bot?>(null) }
    var tab by rememberSaveable { mutableStateOf(0) }
    var logs by remember { mutableStateOf<List<LogEntry>?>(null) }
    var modules by remember { mutableStateOf<List<ModuleState>?>(null) }
    var plugins by remember { mutableStateOf<List<PluginState>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(id, tab) {
        session.call { it.bot(id) }.onSuccess { bot = it }
        val r = when (tab) {
            0 -> session.call { it.logs(id) }.onSuccess { logs = it }
            1 -> session.call { it.modules(id) }.onSuccess { modules = it.sortedBy { m -> session.text("module.${m.key}.name") } }
            else -> session.call { it.plugins(id) }.onSuccess { plugins = it }
        }
        error = r.exceptionOrNull()?.let { session.errorText(it) }
    }

    Column(modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            Text(bot?.name ?: "…", style = MaterialTheme.typography.titleLarge)
        }
        TabRow(tab) {
            listOf(R.string.bot_logs, R.string.bot_modules, R.string.bot_plugins).forEachIndexed { i, s ->
                Tab(tab == i, { tab = i }, text = { Text(stringResource(s)) })
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (tab) {
                0 -> {
                    if (logs?.isEmpty() == true) item { Text(stringResource(R.string.logs_empty), Modifier.padding(top = 16.dp)) }
                    items(logs.orEmpty(), key = { it.id }) { e ->
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text("${e.time.replace('T', ' ').take(19)}  ${e.level.uppercase()}${e.code?.let { "  $it" } ?: ""}",
                                style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                                color = if (e.level == "error") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(session.logText(e))
                        }
                        HorizontalDivider()
                    }
                }
                1 -> {
                    if (modules?.isEmpty() == true) item { Text(stringResource(R.string.modules_empty), Modifier.padding(top = 16.dp)) }
                    items(modules.orEmpty(), key = { it.key }) { m ->
                        Toggle(session.text("module.${m.key}.name"), m.enabled) { on ->
                            scope.launch {
                                session.call { it.setModule(id, m.key, on) }
                                    .onSuccess { modules = modules?.map { x -> if (x.key == m.key) x.copy(enabled = on) else x } }
                                    .onFailure { error = session.errorText(it) }
                            }
                        }
                    }
                }
                else -> {
                    if (plugins?.isEmpty() == true) item { Text(stringResource(R.string.plugins_empty), Modifier.padding(top = 16.dp)) }
                    items(plugins.orEmpty(), key = { it.id }) { p ->
                        Toggle("${p.icon} ${p.name}".trim(), p.enabled) { on ->
                            scope.launch {
                                session.call { it.setPlugin(id, p.id, on) }
                                    .onSuccess { plugins = plugins?.map { x -> if (x.id == p.id) x.copy(enabled = on) else x } }
                                    .onFailure { error = session.errorText(it) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(on, onChange)
    }
}

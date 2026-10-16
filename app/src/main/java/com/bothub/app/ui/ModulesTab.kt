package com.bothub.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bothub.app.R
import com.bothub.app.Session
import com.bothub.core.Catalog
import com.bothub.core.CommandCopy
import com.bothub.core.ModuleState
import kotlinx.coroutines.launch

fun groupColor(hex: String): Color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Brand)

/** A group of the dashboard with the modules of the bot in it. */
data class ModuleGroupView(val group: Catalog.Group, val modules: List<Pair<Catalog.Module, ModuleState>>)

/** Groups in the dashboard's order; modules the catalog does not know go to "other". */
fun groupModules(catalog: Catalog, states: List<ModuleState>): List<ModuleGroupView> {
    val byKey = states.associateBy { it.key }
    val known = mutableSetOf<String>()
    val out = catalog.groups.mapNotNull { g ->
        val mods = g.modules.mapNotNull { m -> byKey[m.key]?.let { known += m.key; m to it } }
        if (mods.isEmpty()) null else ModuleGroupView(g, mods)
    }
    val rest = states.filter { it.key !in known }
    return if (rest.isEmpty()) out else out + ModuleGroupView(Catalog.Group("other", "📦", "#8b8f98"), rest.map { Catalog.Module(it.key) to it })
}

/**
 * The modules of a bot like on the dashboard: one card per group in its
 * colour with "x/y on", a switch per module; tapping a module shows its
 * commands.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModulesTab(session: Session, botId: Long, states: List<ModuleState>, onToggle: (String, Boolean) -> Unit) {
    var open by remember { mutableStateOf<Pair<Catalog.Module, Color>?>(null) }
    val groups = remember(states, session.catalog) { groupModules(session.catalog, states) }
    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Spacer(Modifier.height(4.dp)) }
        if (states.isEmpty()) item { Text(stringResource(R.string.modules_empty)) }
        items(groups, key = { it.group.key }) { g ->
            val color = groupColor(g.group.color)
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
                    Spacer(Modifier.width(8.dp))
                    Text("${g.group.icon} ${session.text("module.category.${g.group.key}").takeUnless { it.startsWith("module.") } ?: g.group.key}".trim(),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.modules_on, g.modules.count { it.second.enabled }, g.modules.size), color = color, style = MaterialTheme.typography.labelMedium)
                }
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.height(IntrinsicSize.Min)) {
                        Box(Modifier.width(4.dp).fillMaxHeight().background(color))
                        Column(Modifier.weight(1f)) {
                            g.modules.forEachIndexed { i, (m, state) ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                Row(
                                    Modifier.fillMaxWidth().clickable { open = m to color }.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(m.icon.ifEmpty { "•" }, Modifier.width(30.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(session.text("module.${m.key}.name").takeUnless { it.startsWith("module.") } ?: m.key, style = MaterialTheme.typography.bodyLarge)
                                        if (m.commands.isNotEmpty()) Text(stringResource(R.string.module_commands_count, m.commands.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(state.enabled, { onToggle(m.key, it) },
                                        colors = SwitchDefaults.colors(checkedTrackColor = color, checkedThumbColor = Color.White))
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
    open?.let { (m, color) ->
        ModalBottomSheet(onDismissRequest = { open = null }) {
            ModuleCommandsSheet(session, botId, m, color)
        }
    }
}

/** The commands of one module: the switch of each is the bot's copy of it. */
@Composable
private fun ModuleCommandsSheet(session: Session, botId: Long, module: Catalog.Module, color: Color) {
    var copies by remember { mutableStateOf<Map<String, CommandCopy>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(module.key) {
        session.call { it.commandCopies(botId) }.onSuccess { copies = it }.onFailure { error = session.errorText(it) }
    }
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
            Spacer(Modifier.width(8.dp))
            Text("${module.icon} ${session.text("module.${module.key}.name").takeUnless { it.startsWith("module.") } ?: module.key}".trim(), style = MaterialTheme.typography.titleLarge)
        }
        session.text("module.${module.key}.description").takeUnless { it.startsWith("module.") }?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(stringResource(R.string.module_commands), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        when {
            module.commands.isEmpty() -> Text(stringResource(R.string.module_no_commands), color = MaterialTheme.colorScheme.onSurfaceVariant)
            copies == null && error == null -> CircularProgressIndicator(Modifier.padding(16.dp))
        }
        copies?.let { map ->
            module.commands.forEach { c ->
                val copy = map[c.preset]
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("/${c.name}", fontFamily = FontFamily.Monospace)
                        if (copy == null) Text(stringResource(R.string.module_command_missing), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(copy?.enabled == true, { on ->
                        copy ?: return@Switch
                        scope.launch {
                            session.call { it.setCommand(botId, copy.id, on) }
                                .onSuccess { copies = map + (c.preset to copy.copy(enabled = on)) }
                                .onFailure { error = session.errorText(it) }
                        }
                    }, enabled = copy != null, colors = SwitchDefaults.colors(checkedTrackColor = color, checkedThumbColor = Color.White))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

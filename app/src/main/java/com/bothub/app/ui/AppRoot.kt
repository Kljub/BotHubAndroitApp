package com.bothub.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.bothub.app.R
import com.bothub.app.Session
import com.bothub.app.Updater
import com.bothub.core.Updates

/** Pairing until the phone is paired; then Bots, Guard and Settings. */
@Composable
fun AppRoot(session: Session, deepLink: String?, onDeepLinkUsed: () -> Unit) {
    val pairing by session.pairing.collectAsState()
    var update by remember { mutableStateOf<Updates.Release?>(null) }
    LaunchedEffect(Unit) {
        update = Updater.check(session)
    }
    update?.let { UpdateDialog(it) { update = null } }

    if (pairing == null) {
        PairScreen(session, deepLink, onDeepLinkUsed)
        return
    }
    LaunchedEffect(pairing) { session.loadTexts() }
    var tab by rememberSaveable { mutableStateOf(0) }
    var openBot by rememberSaveable { mutableStateOf<Long?>(null) }
    Scaffold(bottomBar = {
        NavigationBar {
            NavigationBarItem(tab == 0, { tab = 0; openBot = null }, { Icon(Icons.Filled.SmartToy, null) }, label = { Text(stringResource(R.string.tab_bots)) })
            NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Filled.Security, null) }, label = { Text(stringResource(R.string.tab_guard)) })
            NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Filled.Settings, null) }, label = { Text(stringResource(R.string.tab_settings)) })
        }
    }) { inner ->
        val m = Modifier.padding(inner)
        when (tab) {
            0 -> openBot?.let { BotDetailScreen(session, it, m) { openBot = null } } ?: BotsScreen(session, m) { openBot = it }
            1 -> GuardScreen(session, m)
            else -> SettingsScreen(session, m) { update = it }
        }
    }
}

package com.bothub.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Brand = Color(0xFF7C5CFF)
val Online = Color(0xFF22C55E)
val Offline = Color(0xFF8B8F98)
val Problem = Color(0xFFEF4444)

@Composable
fun BotHubTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = Brand, secondary = Brand, background = Color(0xFF0F1115), surface = Color(0xFF171A21), surfaceVariant = Color(0xFF1F232C))
    } else {
        lightColorScheme(primary = Brand, secondary = Brand)
    }
    MaterialTheme(colorScheme = colors, content = content)
}

package com.bothub.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import com.bothub.app.ui.BotHubTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Text on the app's surface takes the theme's onBackground, never plain black on dark. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "night")
class ContrastTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `dark mode text is light`() {
        var text = Color.Unspecified
        var onBackground = Color.Unspecified
        compose.setContent {
            BotHubTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    text = LocalContentColor.current
                    onBackground = MaterialTheme.colorScheme.onBackground
                    Text("x")
                }
            }
        }
        compose.waitForIdle()
        assertEquals(onBackground, text)
        assert(text.red > 0.8f) { "text colour $text is not light" }
    }
}

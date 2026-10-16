package com.bothub.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.bothub.app.ui.BotHubTheme
import com.bothub.app.ui.Online
import com.bothub.app.ui.Problem
import com.bothub.app.ui.UpdateDialog
import com.bothub.app.ui.statusColor
import com.bothub.core.Updates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Compose screens on the JVM (Robolectric): no emulator needed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class UiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `update dialog shows version and notes and can be dismissed`() {
        var dismissed = false
        val release = Updates.Release(tag = "v1.3.0", body = "New Guard screen", assets = listOf(Updates.Asset("BotHub-1.3.0.apk", 10, "https://x/a.apk")))
        compose.setContent { BotHubTheme { UpdateDialog(release) { dismissed = true } } }
        compose.onNodeWithText("Update 1.3.0").assertIsDisplayed()
        compose.onNodeWithText("New Guard screen").assertIsDisplayed()
        compose.onNodeWithText("Later").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun `status colors`() {
        assertEquals(Online, statusColor("running"))
        assertEquals(Problem, statusColor("error"))
    }
}

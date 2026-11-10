package com.eithonx.rejoin

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class ConnectionScreensTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun homeShowsActivityBelowAutoSignInAndSettingsOpens() {
        var settingsOpened = false
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.setContent {
            RejoinTheme(dark = true) {
                HomeScreen(
                    HomeUi(HomeStatus("Online", tone = ConnectionTone.ONLINE, actionLabel = "Check connection"),
                        network = "Residence Wi-Fi", automation = "On"),
                    onSettings = { settingsOpened = true }, onAutomation = {}, onActivity = {}
                )
            }
        }

        compose.onNodeWithText("Online").assertIsDisplayed()
        compose.onNodeWithText("Auto sign-in").assertIsDisplayed()
        compose.onNodeWithText("Activity").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.runOnIdle { assertTrue(settingsOpened) }
        screenshot("home-dark")
    }

    @Test
    fun settingsRowsRemainReachableWithLargeText() {
        var loginsOpened = false
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.4f)) {
                RejoinTheme(dark = false) {
                    SettingsScreen(SettingsUi(1, 1, "On", "2.4.0-beta3"), onBack = {},
                        onNetworks = {}, onLogins = { loginsOpened = true }, onAutomation = {},
                        onHelp = {}, onDeveloper = {})
                }
            }
        }

        compose.onNodeWithText("Saved Wi-Fi").assertIsDisplayed()
        compose.onNodeWithText("Saved logins").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(loginsOpened) }
        screenshot("settings-large-text")
        compose.onNodeWithText("Developer").performScrollTo().assertIsDisplayed()
    }

    private fun screenshot(name: String) {
        val file = File(compose.activity.filesDir, "$name.png")
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val command = "run-as com.eithonx.rejoin cat files/$name.png > /data/local/tmp/rejoin-$name.png"
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use {
            it.readBytes()
        }
    }
}

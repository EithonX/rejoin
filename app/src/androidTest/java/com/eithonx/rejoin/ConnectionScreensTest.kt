package com.eithonx.rejoin

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

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
                    updateVersion = null, onSettings = { settingsOpened = true },
                    onAutomation = {}, onActivity = {}, onIssue = {}, onUpdate = {}
                )
            }
        }

        compose.onNodeWithText("Online").assertIsDisplayed()
        compose.onNodeWithText("Auto sign-in").assertIsDisplayed()
        compose.onNodeWithText("Activity").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.runOnIdle { assertTrue(settingsOpened) }
    }

    @Test
    fun settingsRowsRemainReachableWithLargeText() {
        var loginsOpened = false
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.4f)) {
                RejoinTheme(dark = false) {
                    SettingsScreen(SettingsUi(1, 1, "On"), "System default", onBack = {},
                        onNetworks = {}, onLogins = { loginsOpened = true }, onAutomation = {},
                        onAppearance = {}, onHelp = {}, onAbout = {})
                }
            }
        }

        compose.onNodeWithText("Saved Wi-Fi").assertIsDisplayed()
        compose.onNodeWithText("Saved logins").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(loginsOpened) }
        compose.onNodeWithText("About").performScrollTo().assertIsDisplayed()
    }
}

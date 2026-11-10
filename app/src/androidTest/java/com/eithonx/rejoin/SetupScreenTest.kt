package com.eithonx.rejoin

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SetupScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<SetupActivity>()

    @Test
    fun firstStepShowsPermissionActionAndSkip() {
        compose.onNodeWithText("1 of 3").assertIsDisplayed()
        compose.onNodeWithText("Save your Wi-Fi").assertIsDisplayed()
        compose.onNodeWithText("Allow Wi-Fi access").assertIsDisplayed()
        compose.onNodeWithText("Not now").assertIsDisplayed()
    }
}

package com.eithonx.rejoin

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class SetupScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<SetupActivity>()

    @Test
    fun firstStepShowsPermissionActionAndSkip() {
        compose.onNodeWithText("1 of 3").assertIsDisplayed()
        compose.onNodeWithText("Save your Wi-Fi").assertIsDisplayed()
        compose.onNodeWithText("Allow Wi-Fi access").assertIsDisplayed()
        compose.onNodeWithText("Not now").assertIsDisplayed()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(compose.activity.filesDir, "setup-first-step.png").outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}

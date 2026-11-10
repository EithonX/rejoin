package com.eithonx.rejoin

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.util.concurrent.Executors

class DiagnosticsActivity : RejoinActivity() {
    private enum class Test(val title: String) {
        CHECK("Checking connection…"), LOGIN("Signing in…"), LOGOUT("Signing out…")
    }

    private val worker = Executors.newSingleThreadExecutor()
    private var running by mutableStateOf<Test?>(null)

    @Composable
    override fun Screen() {
        Page("Troubleshooting") {
            val current = running
            if (current != null) {
                ScreenIntro(current.title)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                SectionLabel("Connection")
                ListGroup {
                    ActionRow("Check internet", onClick = { test(Test.CHECK) })
                    HorizontalDivider()
                    ActionRow("Try sign-in once", "Use the saved login", onClick = ::confirmSignIn)
                    HorizontalDivider()
                    ActionRow("Open sign-in page", "Continue in your browser", onClick = ::openSignInPage)
                }
                SectionLabel("Advanced")
                ListGroup {
                    ActionRow("Log out of Wi-Fi", "May disconnect other devices", onClick = ::confirmLogout)
                    HorizontalDivider()
                    ActionRow("Erase saved logins", "Delete passwords from this device", onClick = ::confirmErase)
                }
            }
        }
    }

    private fun confirmSignIn() {
        confirm("Try signing in?", "Rejoin will try your saved login once over unencrypted HTTP.",
            "Try sign-in", "Cancel") { test(Test.LOGIN) }
    }

    private fun confirmLogout() {
        confirm("Log out of Wi-Fi?", "Other devices on the same router may lose internet access.",
            "Log out", "Cancel") {
            getSharedPreferences("profile", MODE_PRIVATE).edit()
                .putLong("manual_test_until", System.currentTimeMillis() + 600_000L).commit()
            test(Test.LOGOUT)
        }
    }

    private fun confirmErase() {
        confirm("Erase saved logins?", "This also turns off auto sign-in.", "Erase", "Cancel") {
            getSharedPreferences("profile", MODE_PRIVATE).edit().putBoolean("auto_enabled", false).commit()
            RecoveryScheduler.disable(this)
            CredentialVault.clear(this)
            ActivityHistory.record(this, "Saved logins erased")
            notice("Saved logins erased", "You can add a login again in Settings.")
        }
    }

    private fun test(kind: Test) {
        if (running != null) return
        running = kind
        worker.execute {
            val result = try {
                when (kind) {
                    Test.LOGIN -> SessionEngine.run(applicationContext, userInitiated = true,
                        explicitlyLoggedOut = true).message
                    Test.LOGOUT -> LiveSessionTest.logout(applicationContext)
                    Test.CHECK -> InternetVerifier.check(applicationContext).details
                }
            } catch (_: Exception) { "Couldn’t complete the check." }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    running = null
                    notice("Connection result", result)
                }
            }
        }
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }
}

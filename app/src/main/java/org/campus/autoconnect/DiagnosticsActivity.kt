package org.campus.autoconnect

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Diagnostic tasks stay outside the everyday experience. */
class DiagnosticsActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val running = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val body = RejoinUi.screen(this, "Troubleshooting")
        RejoinUi.section(body, "Connection", 0)
        val connection = RejoinUi.inset(this)
        RejoinUi.add(body, connection, 12)
        connection.addView(RejoinUi.navRow(this, "Check internet",
            "See whether your Wi-Fi has access") { test("check") })
        RejoinUi.add(connection, RejoinUi.rule(this), 2)
        connection.addView(RejoinUi.navRow(this, "Try sign-in once",
            "Use the login saved for this Wi-Fi") { confirmSignIn() })
        RejoinUi.add(connection, RejoinUi.rule(this), 2)
        connection.addView(RejoinUi.navRow(this, "Open campus sign-in",
            "Use the browser instead") { RejoinUi.openCampusSignIn(this) })

        RejoinUi.section(body, "Advanced", 30)
        val advanced = RejoinUi.inset(this)
        RejoinUi.add(body, advanced, 12)
        advanced.addView(RejoinUi.navRow(this, "Log out shared Wi-Fi",
            "Can interrupt everyone using your router") { confirmLogout() })
        RejoinUi.add(advanced, RejoinUi.rule(this), 2)
        advanced.addView(RejoinUi.navRow(this, "Erase saved logins",
            "Delete passwords from this device") { confirmErase() })
    }

    private fun confirmSignIn() {
        AlertDialog.Builder(this).setTitle("Try a campus sign-in?")
            .setMessage("Uses your saved account once. CU's sign-in page uses unencrypted HTTP.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Try sign-in") { _, _ -> test("login") }.show()
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this).setTitle("Log out shared Wi-Fi?")
            .setMessage("Other devices connected to the same router may lose internet.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Log out") { _, _ ->
                getSharedPreferences("profile", MODE_PRIVATE).edit()
                    .putLong("manual_test_until", System.currentTimeMillis() + 600_000L)
                    .commit()
                test("logout")
            }.show().also { RejoinUi.destructive(it) }
    }

    private fun confirmErase() {
        AlertDialog.Builder(this).setTitle("Erase saved passwords?")
            .setMessage("This stops automatic recovery. Your trusted Wi-Fi networks will remain.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Erase") { _, _ ->
                getSharedPreferences("profile", MODE_PRIVATE).edit()
                    .putBoolean("auto_enabled", false).commit()
                RecoveryScheduler.disable(this)
                CredentialVault.clear(this)
                ActivityHistory.record(this, "Saved logins erased")
                message("Saved logins erased", "You can set them up again whenever you're ready.")
            }.show().also { RejoinUi.destructive(it) }
    }

    private fun test(kind: String) {
        if (!running.compareAndSet(false, true)) return
        val progress = AlertDialog.Builder(this).setTitle(
            if (kind == "login") "Signing in…" else if (kind == "logout") "Signing out…" else "Checking…")
            .setMessage("Checking the current Wi-Fi.")
            .setCancelable(false).create()
        progress.show()
        worker.execute {
            val result = try {
                when (kind) {
                    "login" -> SessionEngine.run(applicationContext, userInitiated = true,
                        explicitlyLoggedOut = true).message
                    "logout" -> LiveSessionTest.logout(applicationContext)
                    else -> InternetVerifier.check(applicationContext).let {
                        it.state.name + ": " + it.details
                    }
                }
            } catch (e: Exception) { "Couldn't complete the test (" + e.javaClass.simpleName + ")" }
            runOnUiThread {
                running.set(false)
                progress.dismiss()
                if (!isFinishing) message("Connection result", result)
            }
        }
    }

    private fun message(title: String, detail: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(detail)
            .setPositiveButton("OK", null).show()
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }
}

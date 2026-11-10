package org.campus.autoconnect

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

class LoginEditorActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(EXTRA_ID)
        val existing = id?.let { CredentialVault.list(this).find { login -> login.id == it } }
        if (id != null && existing == null) {
            finish()
            return
        }
        val title = if (existing == null) "Add campus login" else "Edit campus login"
        val body = RejoinUi.screen(this, title)
        val consent = getSharedPreferences("profile", MODE_PRIVATE).getBoolean("http_opt_in", false)
        val form = LoginForm(this, existing, includeName = true, consentGranted = consent)
        RejoinUi.add(body, form.view)
        RejoinUi.add(body, RejoinUi.action(this, "Save login") {
            if (form.save()) {
                ActivityHistory.record(this, if (existing == null) "Campus login saved" else "Saved login updated")
                Toast.makeText(this, "Login saved on this phone", Toast.LENGTH_SHORT).show()
                finish()
            }
        }, 16)
    }

    companion object {
        const val EXTRA_ID = "login_id"
    }
}

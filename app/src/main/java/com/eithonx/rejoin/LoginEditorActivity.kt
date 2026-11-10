package com.eithonx.rejoin

import android.os.Bundle
import android.widget.Toast
import androidx.compose.runtime.Composable

class LoginEditorActivity : RejoinActivity() {
    private var existing: CredentialVault.SavedLogin? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val id = intent.getStringExtra(EXTRA_ID)
        existing = id?.let { key -> CredentialVault.list(this).find { it.id == key } }
        super.onCreate(savedInstanceState)
        if (id != null && existing == null) finish()
    }

    @Composable
    override fun Screen() {
        Page(if (existing == null) "Add login" else "Edit login") {
            val consent = getSharedPreferences("profile", MODE_PRIVATE).getBoolean("http_opt_in", false)
            LoginForm(existing, includeName = true, consentGranted = consent) {
                ActivityHistory.record(this, if (existing == null) "Login saved" else "Saved login updated")
                Toast.makeText(this, "Login saved on this device", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    companion object {
        const val EXTRA_ID = "login_id"
    }
}

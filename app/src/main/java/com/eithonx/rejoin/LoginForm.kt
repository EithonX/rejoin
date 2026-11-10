package com.eithonx.rejoin

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
internal fun LoginForm(
    existing: CredentialVault.SavedLogin? = null,
    includeName: Boolean = false,
    consentOnly: Boolean = false,
    consentGranted: Boolean = false,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var name by rememberSaveable { mutableStateOf(existing?.label.orEmpty()) }
    var username by rememberSaveable { mutableStateOf(existing?.username.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var consent by rememberSaveable { mutableStateOf(consentGranted) }
    var usernameError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }

    if (!consentOnly) {
        if (includeName) {
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(48) },
                label = { Text("Label (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
        }
        OutlinedTextField(
            value = username, onValueChange = { username = it; usernameError = null },
            label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, autoCorrectEnabled = false),
            isError = usernameError != null,
            supportingText = usernameError?.let { { Text(it) } }
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it; passwordError = null },
            label = { Text(if (existing == null) "Password" else "New password") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { passwordVisible = !passwordVisible }) {
                    Text(if (passwordVisible) "Hide" else "Show")
                }
            },
            isError = passwordError != null,
            supportingText = passwordError?.let { { Text(it) } } ?: existing?.let {
                { Text("Leave blank to keep your saved password.") }
            }
        )
    }

    if (!consentGranted) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sign-in uses HTTP", style = MaterialTheme.typography.titleMedium)
                BodyText("Your password is encrypted on this device. During sign-in, it travels over the network without encryption.")
                Row(
                    Modifier.fillMaxWidth().toggleable(value = consent, role = Role.Checkbox,
                        onValueChange = { consent = it; saveError = null }).padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = consent, onCheckedChange = null)
                    Text("Allow sign-in over HTTP")
                }
            }
        }
    }

    saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    PageButton(if (consentOnly) "Save permission" else "Save login") {
        val uid = username.trim()
        usernameError = when {
            consentOnly -> null
            uid.isBlank() -> "Enter your username."
            uid.length > 128 -> "Use 128 characters or fewer."
            else -> null
        }
        passwordError = when {
            consentOnly -> null
            existing == null && password.isBlank() -> "Enter your password."
            password.length > 512 -> "Use 512 characters or fewer."
            else -> null
        }
        if (usernameError == null && passwordError == null) {
            val prefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
            saveError = when {
                !consent -> "Allow sign-in to continue."
                !consentOnly && !CredentialVault.upsert(context, existing?.id,
                    name.ifBlank { "Wi-Fi login" }, uid, password) -> "Couldn’t save your login. Try again."
                !prefs.getBoolean("http_opt_in", false) &&
                    !prefs.edit().putBoolean("http_opt_in", true).commit() ->
                    "Couldn’t save your sign-in permission. Try again."
                else -> null
            }
            if (saveError == null) {
                password = ""
                keyboard?.hide()
                onSaved()
            }
        }
    }
}

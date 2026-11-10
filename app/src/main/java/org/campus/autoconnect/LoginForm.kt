package org.campus.autoconnect

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.StateListDrawable
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

internal class LoginForm(
    private val context: Context,
    private val existing: CredentialVault.SavedLogin? = null,
    private val includeName: Boolean = false,
    private val consentOnly: Boolean = false,
    consentGranted: Boolean = false
) {
    val view = RejoinUi.column(context).apply { isFocusableInTouchMode = true }
    private val name = if (includeName) {
        field("Login name (optional)", "Campus login", existing?.label.orEmpty(),
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
    } else null
    private val username = if (!consentOnly) {
        field("Campus username", "Enter your username", existing?.username.orEmpty(),
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS).apply {
            imeOptions = EditorInfo.IME_ACTION_NEXT
            setAutofillHints(View.AUTOFILL_HINT_USERNAME)
        }
    } else null
    private val password = if (!consentOnly) passwordField() else null
    private val consentError = RejoinUi.label(context, "Allow sign-in to continue.", 14f, RejoinUi.rose)
    private val saveError = RejoinUi.label(context, "", 14f, RejoinUi.rose)
    private val consent = CheckBox(context).apply {
        text = "Allow Rejoin to sign in over HTTP"
        textSize = 15f
        setTextColor(RejoinUi.ink)
        minimumHeight = RejoinUi.dp(context, 48)
        buttonTintList = ColorStateList.valueOf(RejoinUi.accent)
        isChecked = consentGranted
        setOnCheckedChangeListener { _, checked ->
            if (checked) consentError.visibility = View.GONE
        }
    }

    init {
        val disclosure = RejoinUi.inset(context)
        RejoinUi.add(disclosure, RejoinUi.label(context, "Campus sign-in uses HTTP",
            15f, RejoinUi.amber, true))
        RejoinUi.add(disclosure, RejoinUi.label(context,
            "Your password is encrypted on this phone. The campus network can see it during sign-in because the portal uses unencrypted HTTP.",
            14f, RejoinUi.muted), 4)
        RejoinUi.add(disclosure, consent, 8)
        RejoinUi.add(view, disclosure, if (consentOnly) 0 else 16)
        for (error in listOf(consentError, saveError)) {
            error.visibility = View.GONE
            error.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            RejoinUi.add(view, error, 8)
        }
    }

    fun save(): Boolean {
        val uid = username?.text?.toString()?.trim().orEmpty()
        val secret = password?.text?.toString().orEmpty()
        val problem = when {
            username != null && uid.isBlank() -> username to "Enter your campus username."
            username != null && uid.length > 128 -> username to "Use 128 characters or fewer."
            password != null && existing == null && secret.isBlank() -> password to "Enter your password."
            password != null && secret.length > 512 -> password to "Use 512 characters or fewer."
            else -> null
        }
        if (problem != null) {
            problem.first.error = problem.second
            problem.first.requestFocus()
            return false
        }
        if (!consent.isChecked) {
            consentError.visibility = View.VISIBLE
            consent.requestFocus()
            return false
        }
        val prefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
        if (!consentOnly && !CredentialVault.upsert(context, existing?.id,
                name?.text?.toString() ?: "Campus login", uid, secret)) {
            showSaveError("Couldn’t save this login. Check your details or remove an unused login.")
            return false
        }
        if (!prefs.edit().putBoolean("http_opt_in", true).commit()) {
            showSaveError("Couldn’t save your sign-in permission. Try again.")
            return false
        }
        password?.text?.clear()
        return true
    }

    private fun showSaveError(message: String) {
        saveError.text = message
        saveError.visibility = View.VISIBLE
    }

    private fun field(label: String, hint: String, value: String, type: Int): EditText {
        val input = input(hint, type).apply { setText(value) }
        fieldLabel(label, input, if (view.childCount == 0) 0 else 12)
        RejoinUi.add(view, input, 4)
        return input
    }

    private fun fieldLabel(label: String, input: EditText, spacing: Int) {
        RejoinUi.add(view, RejoinUi.label(context, label, 14f, bold = true).apply {
            labelFor = input.id
        }, spacing)
    }

    private fun input(hintText: String, type: Int) = EditText(context).apply {
        id = View.generateViewId()
        hint = hintText
        setSingleLine(true)
        inputType = type
        textSize = 16f
        setTextColor(RejoinUi.ink)
        setHintTextColor(RejoinUi.muted)
        val padding = RejoinUi.dp(context, 12)
        setPadding(padding, padding, padding, padding)
        minHeight = RejoinUi.dp(context, 52)
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused),
                RejoinUi.surface(context, RejoinUi.panel, 12, RejoinUi.accent))
            addState(intArrayOf(), RejoinUi.surface(context, RejoinUi.panel, 12, RejoinUi.line))
        }
    }

    private fun passwordField(): EditText {
        val input = input(if (existing == null) "Enter your password" else "Leave blank to keep it",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD).apply {
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            setAutofillHints(View.AUTOFILL_HINT_PASSWORD)
            isSaveEnabled = false
        }
        fieldLabel(if (existing == null) "Password" else "New password", input, 12)
        val row = RejoinUi.row(context)
        row.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        val show = RejoinUi.smallAction(context, "Show") { }
        show.contentDescription = "Show password"
        show.setOnClickListener {
            val hidden = input.transformationMethod is PasswordTransformationMethod
            input.transformationMethod = if (hidden) null else PasswordTransformationMethod.getInstance()
            input.setSelection(input.length())
            show.text = if (hidden) "Hide" else "Show"
            show.contentDescription = if (hidden) "Hide password" else "Show password"
        }
        row.addView(show, LinearLayout.LayoutParams(-2, -2).apply {
            marginStart = RejoinUi.dp(context, 4)
        })
        RejoinUi.add(view, row, 4)
        return input
    }
}

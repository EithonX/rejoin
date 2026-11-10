package org.campus.autoconnect

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

object RejoinUi {
    val canvas = Color.rgb(17, 21, 30)
    val panel = Color.rgb(26, 33, 48)
    val raised = Color.rgb(35, 44, 62)
    val ink = Color.rgb(242, 244, 253)
    val accent = Color.rgb(189, 201, 255)
    val muted = Color.rgb(170, 181, 203)
    val line = Color.rgb(53, 66, 90)
    val amber = Color.rgb(255, 199, 130)
    val success = Color.rgb(139, 227, 194)
    val rose = Color.rgb(255, 173, 169)

    fun dp(ctx: Context, px: Int): Int =
        (px * ctx.resources.displayMetrics.density + .5f).toInt()

    fun surface(ctx: Context, color: Int, radius: Int = 16, stroke: Int? = null) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(ctx, radius).toFloat()
            if (stroke != null) setStroke(dp(ctx, 1), stroke)
        }

    fun label(ctx: Context, value: String, sp: Float = 14f,
              color: Int = ink, bold: Boolean = false): TextView =
        TextView(ctx).apply {
            text = value
            textSize = sp
            setTextColor(color)
            typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
            setLineSpacing(dp(ctx, 2).toFloat(), 1f)
        }

    fun column(ctx: Context, padding: Int = 0) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        val inset = dp(ctx, padding)
        setPadding(inset, inset, inset, inset)
    }

    fun row(ctx: Context) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun add(parent: LinearLayout, child: View, top: Int = 0, bottom: Int = 0) {
        parent.addView(child, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(parent.context, top)
            bottomMargin = dp(parent.context, bottom)
        })
    }

    fun inset(ctx: Context, color: Int = panel) = column(ctx, 16).apply {
        background = surface(ctx, color, 20, if (color == panel) line else null)
    }

    private fun ripple(ctx: Context, radius: Int, base: Int? = null, border: Int? = null) =
        RippleDrawable(
            ColorStateList.valueOf(if (base == accent) 0x3011151E else 0x30BDC9FF),
            base?.let { surface(ctx, it, radius, border) },
            surface(ctx, Color.WHITE, radius)
        )

    private fun button(ctx: Context, value: String, color: Int) = Button(ctx, null, 0).apply {
        text = value
        textSize = 15f
        setTextColor(color)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        isAllCaps = false
        gravity = Gravity.CENTER
        minimumHeight = dp(ctx, 48)
        minimumWidth = dp(ctx, 48)
        isFocusable = true
    }

    fun action(ctx: Context, value: String, filled: Boolean = true,
               onClick: () -> Unit): TextView =
        button(ctx, value, if (filled) canvas else ink).apply {
            minimumHeight = dp(ctx, 54)
            background = ripple(ctx, 16, if (filled) accent else panel, if (filled) null else line)
            setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 16), dp(ctx, 12))
            setOnClickListener { onClick() }
        }

    fun smallAction(ctx: Context, value: String, onClick: () -> Unit): TextView =
        button(ctx, value, accent).apply {
            background = ripple(ctx, 12)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 8), dp(ctx, 8), dp(ctx, 8), dp(ctx, 8))
            setOnClickListener { onClick() }
        }

    fun rule(ctx: Context) = View(ctx).apply {
        minimumHeight = dp(ctx, 1)
        setBackgroundColor(line)
    }

    fun icon(ctx: Context, id: Int, size: Int, tint: Int? = null): ImageView =
        ImageView(ctx).apply {
            setImageResource(id)
            if (tint != null) imageTintList = ColorStateList.valueOf(tint)
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = LinearLayout.LayoutParams(dp(ctx, size), dp(ctx, size))
        }

    fun iconAction(ctx: Context, id: Int, description: String, onClick: () -> Unit) =
        icon(ctx, id, 48, ink).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val padding = dp(ctx, 12)
            setPadding(padding, padding, padding, padding)
            background = ripple(ctx, 14)
            contentDescription = description
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            isFocusable = true
            setOnClickListener { onClick() }
            accessibilityDelegate = buttonSemantics
        }

    fun applySystemBars(activity: Activity, view: View) {
        if (Build.VERSION.SDK_INT >= 30) {
            @Suppress("DEPRECATION")
            activity.window.setDecorFitsSystemWindows(false)
            view.setOnApplyWindowInsetsListener { v, insets ->
                val safe = insets.getInsets(WindowInsets.Type.systemBars() or
                    WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
                v.setPadding(safe.left, safe.top, safe.right, safe.bottom)
                insets
            }
            view.requestApplyInsets()
        } else {
            view.fitsSystemWindows = true
        }
    }

    fun screen(activity: Activity, title: String): LinearLayout {
        val page = column(activity).apply { setBackgroundColor(canvas) }
        val top = column(activity).apply {
            setPadding(dp(activity, 12), dp(activity, 4), dp(activity, 16), dp(activity, 4))
        }
        header(activity, top, title)
        add(page, top)
        val body = scrollBody(page)
        activity.setContentView(page)
        applySystemBars(activity, page)
        return body
    }

    fun scrollBody(parent: LinearLayout): LinearLayout {
        val ctx = parent.context
        val body = column(ctx).apply {
            setPadding(dp(ctx, 24), dp(ctx, 16), dp(ctx, 24), dp(ctx, 24))
            isFocusableInTouchMode = true
        }
        val scroll = ScrollView(ctx).apply {
            addView(body)
            isFillViewport = true
        }
        parent.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return body
    }

    fun footer(parent: LinearLayout) = column(parent.context).apply {
        val side = dp(context, 24)
        setPadding(side, dp(context, 8), side, dp(context, 12))
        add(parent, this)
    }

    fun header(ctx: Activity, parent: LinearLayout, title: String) {
        val bar = row(ctx)
        bar.addView(iconAction(ctx, R.drawable.ic_arrow_back, "Go back") { ctx.finish() })
        bar.addView(label(ctx, title, 22f, ink, true).apply { heading(this) },
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(ctx, 8) })
        add(parent, bar)
    }

    fun heading(view: View) {
        if (Build.VERSION.SDK_INT >= 28) view.isAccessibilityHeading = true
    }

    fun section(parent: LinearLayout, text: String, marginTop: Int = 24) {
        add(parent, label(parent.context, text, 14f, muted, true).apply { heading(this) }, marginTop)
    }

    fun navRow(ctx: Context, heading: String, detail: String,
               iconId: Int? = null, onTap: () -> Unit): LinearLayout =
        row(ctx).apply {
            setPadding(dp(ctx, 8), dp(ctx, 12), dp(ctx, 8), dp(ctx, 12))
            minimumHeight = dp(ctx, 64)
            isFocusable = true
            contentDescription = if (detail.isBlank()) heading else "$heading, $detail"
            background = ripple(ctx, 12)
            accessibilityDelegate = buttonSemantics
            setOnClickListener { onTap() }
            if (iconId != null) {
                val leading = icon(ctx, iconId, 24, muted)
                addView(leading, LinearLayout.LayoutParams(dp(ctx, 24), dp(ctx, 24))
                    .apply { marginEnd = dp(ctx, 16) })
            }
            val stack = column(ctx).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
            addView(stack, LinearLayout.LayoutParams(0, -2, 1f))
            add(stack, label(ctx, heading, 16f, ink, true))
            if (detail.isNotBlank()) add(stack, label(ctx, detail, 14f, muted), 2)
            addView(icon(ctx, R.drawable.ic_chevron_right, 20, muted),
                LinearLayout.LayoutParams(dp(ctx, 20), dp(ctx, 20)).apply { marginStart = dp(ctx, 8) })
        }

    private val buttonSemantics = object : View.AccessibilityDelegate() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            info.className = Button::class.java.name
        }
    }

    fun hasWifiAccess(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun requestWifiAccess(activity: Activity, requestCode: Int) {
        activity.requestPermissions(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
        ), requestCode)
    }

    fun openAppSettings(activity: Activity) {
        activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", activity.packageName, null)))
    }

    fun explainWifiAccess(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle("Allow precise Location")
            .setMessage("Android uses Location permission to let Rejoin identify your Wi-Fi. Open Permissions → Location and turn on precise location.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Open app settings") { _, _ -> openAppSettings(activity) }
            .show()
    }

    fun openCampusSignIn(activity: Activity) {
        val error = if (!LiveSessionTest.selectedWifiMatches(activity)) {
            "Connect to a saved campus Wi-Fi first."
        } else {
            try {
                activity.startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("http://172.16.2.1:1000/login?")))
                return
            } catch (_: ActivityNotFoundException) {
                "Install or enable a browser to open the campus sign-in page."
            }
        }
        AlertDialog.Builder(activity).setTitle("Campus sign-in")
            .setMessage(error).setPositiveButton("OK", null).show()
    }

    fun destructive(dialog: AlertDialog) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(rose)
    }
}

package org.campus.autoconnect

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView

/**
 * Rejoin design system — warm canvas, botanical ink and a single jade accent.
 * Uses only platform views; no WebView or third-party UI runtime.
 */
object RejoinUi {
    val canvas = Color.rgb(246, 248, 245)      // F6F8F5
    val ink = Color.rgb(25, 48, 43)           // 19302B
    val jade = Color.rgb(31, 105, 84)         // 1F6954
    val jadeDeep = Color.rgb(19, 70, 56)      // 134638
    val mint = Color.rgb(215, 239, 227)       // D7EFE3
    val muted = Color.rgb(108, 128, 119)      // 6C8077
    val line = Color.rgb(223, 232, 225)       // DFE8E1
    val white = Color.WHITE
    val amber = Color.rgb(176, 105, 36)
    val rose = Color.rgb(165, 69, 62)

    fun dp(context: Context, n: Int) = (n * context.resources.displayMetrics.density + .5f).toInt()

    fun surface(ctx: Context, color: Int, radius: Int = 21, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(ctx, radius).toFloat()
            if (stroke != null) setStroke(dp(ctx, 1), stroke)
        }

    fun label(ctx: Context, value: String, sp: Float = 14f, color: Int = ink,
              bold: Boolean = false): TextView =
        TextView(ctx).apply {
            text = value
            textSize = sp
            setTextColor(color)
            typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif",
                if (bold) Typeface.BOLD else Typeface.NORMAL)
            includeFontPadding = false
        }

    fun column(ctx: Context, padding: Int = 0): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            if (padding > 0) setPadding(dp(ctx, padding), dp(ctx, padding),
                dp(ctx, padding), dp(ctx, padding))
        }

    fun row(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun add(parent: LinearLayout, child: View, top: Int = 0, bottom: Int = 0, weight: Float = 0f) {
        val params = LinearLayout.LayoutParams(
            if (weight > 0f) 0 else LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT, weight)
        params.topMargin = dp(parent.context, top)
        params.bottomMargin = dp(parent.context, bottom)
        parent.addView(child, params)
    }

    fun inset(ctx: Context, color: Int = white): LinearLayout =
        column(ctx, 20).apply { background = surface(ctx, color, 22, if (color == white) line else null) }

    fun action(ctx: Context, value: String, filled: Boolean = true, onClick: () -> Unit): TextView {
        val base = if (filled) jade else white
        val border = if (filled) null else line
        val mask = surface(ctx, Color.WHITE, 16)
        val drawable = RippleDrawable(ColorStateList.valueOf(
            if (filled) Color.rgb(94, 151, 133) else mint
        ), surface(ctx, base, 16, border), mask)
        return label(ctx, value, 14f, if (filled) white else ink, true).apply {
            gravity = Gravity.CENTER
            minHeight = dp(ctx, 52)
            setPadding(dp(ctx, 14), dp(ctx, 14), dp(ctx, 14), dp(ctx, 14))
            background = drawable
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    fun smallAction(ctx: Context, value: String, onClick: () -> Unit): TextView =
        label(ctx, value, 13f, jade, true).apply {
            setPadding(dp(ctx, 8), dp(ctx, 10), dp(ctx, 8), dp(ctx, 10))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    fun space(ctx: Context, dpSize: Int): View = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(ctx, dpSize))
    }

    fun rule(ctx: Context): View = View(ctx).apply {
        setBackgroundColor(line)
        layoutParams = LinearLayout.LayoutParams(-1, dp(ctx, 1))
    }

    fun icon(ctx: Context, id: Int, size: Int, tint: Int? = null): ImageView =
        ImageView(ctx).apply {
            setImageResource(id)
            if (tint != null) imageTintList = ColorStateList.valueOf(tint)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(ctx, size), dp(ctx, size))
        }

    fun applySystemBars(activity: Activity, view: View) {
        activity.window.statusBarColor = canvas
        activity.window.navigationBarColor = canvas
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                (if (Build.VERSION.SDK_INT >= 27) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
        }
        if (Build.VERSION.SDK_INT >= 30) {
            view.setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(0, bars.top, 0, bars.bottom)
                insets
            }
        } else {
            view.fitsSystemWindows = true
        }
    }
}

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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Rejoin visual foundations.
 *
 * Dark-first, restrained contrast and an 8dp spacing rhythm. The mint accent
 * is reserved for actions and positive states, rather than decoration.
 */
object RejoinUi {
    val canvas: Int = Color.rgb(11, 16, 21)       // #0B1015
    val panel: Int = Color.rgb(20, 28, 33)        // #141C21
    val raised: Int = Color.rgb(27, 37, 43)       // #1B252B
    val ink: Int = Color.rgb(236, 243, 240)       // #ECF3F0
    val jade: Int = Color.rgb(117, 220, 180)      // #75DCB4
    val jadeDeep: Int = Color.rgb(21, 48, 39)     // #153027
    val mint: Int = Color.rgb(194, 240, 218)      // #C2F0DA
    val muted: Int = Color.rgb(148, 165, 160)     // #94A5A0
    val line: Int = Color.rgb(43, 56, 62)         // #2B383E
    val white: Int = Color.WHITE
    val amber: Int = Color.rgb(230, 188, 112)    // #E6BC70
    val rose: Int = Color.rgb(235, 143, 133)     // #EB8F85

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
            typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif",
                if (bold) Typeface.BOLD else Typeface.NORMAL)
            includeFontPadding = false
        }

    fun column(ctx: Context, padding: Int = 0) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        if (padding != 0) setPadding(dp(ctx, padding), dp(ctx, padding),
            dp(ctx, padding), dp(ctx, padding))
    }

    fun row(ctx: Context) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun add(parent: LinearLayout, child: View, top: Int = 0,
            bottom: Int = 0, weight: Float = 0f) {
        val params = LinearLayout.LayoutParams(
            if (weight > 0f) 0 else LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT, weight)
        params.topMargin = dp(parent.context, top)
        params.bottomMargin = dp(parent.context, bottom)
        parent.addView(child, params)
    }

    fun inset(ctx: Context, color: Int = panel) = column(ctx, 18).apply {
        background = surface(ctx, color, 17,
            if (color == panel) line else null)
    }

    fun action(ctx: Context, value: String, filled: Boolean = true,
               onClick: () -> Unit): TextView {
        val base = if (filled) jade else raised
        val text = if (filled) canvas else ink
        val drawable = RippleDrawable(
            ColorStateList.valueOf(if (filled) Color.rgb(165, 244, 211) else line),
            surface(ctx, base, 13, if (filled) null else line),
            surface(ctx, Color.WHITE, 13)
        )
        return label(ctx, value, 15f, text, true).apply {
            gravity = Gravity.CENTER
            minHeight = dp(ctx, 52)
            setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 16), dp(ctx, 12))
            background = drawable
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    fun smallAction(ctx: Context, value: String, onClick: () -> Unit) =
        label(ctx, value, 13f, jade, true).apply {
            minHeight = dp(ctx, 42)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 8), dp(ctx, 8), dp(ctx, 8), dp(ctx, 8))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    fun rule(ctx: Context) = View(ctx).apply {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // No LIGHT_STATUS_BAR or LIGHT_NAVIGATION_BAR: light symbols on dark.
            @Suppress("DEPRECATION")
            activity.window.decorView.systemUiVisibility = 0
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

    fun header(ctx: Activity, parent: LinearLayout,
               title: String, subtitle: String? = null) {
        val bar = row(ctx)
        val back = ImageView(ctx).apply {
            setImageResource(R.drawable.ic_arrow_back)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(ctx, 12), dp(ctx, 12), dp(ctx, 12), dp(ctx, 12))
            background = RippleDrawable(ColorStateList.valueOf(raised), null,
                surface(ctx, Color.WHITE, 12))
            contentDescription = "Go back"
            isClickable = true
            isFocusable = true
            setOnClickListener { ctx.finish() }
        }
        bar.addView(back, LinearLayout.LayoutParams(dp(ctx, 48), dp(ctx, 48)))
        bar.addView(label(ctx, title, 24f, ink, true),
            LinearLayout.LayoutParams(0, -2, 1f).apply {
                leftMargin = dp(ctx, 12)
            })
        add(parent, bar)
        if (subtitle != null) add(parent, label(ctx, subtitle, 13f, muted), 13)
    }

    fun section(parent: LinearLayout, text: String, marginTop: Int = 28) {
        add(parent, label(parent.context, text.uppercase(), 11f, muted, true)
            .apply { letterSpacing = .12f }, marginTop)
    }

    fun navRow(ctx: Context, heading: String, detail: String,
               iconId: Int? = null, onTap: () -> Unit): LinearLayout {
        val content = row(ctx).apply {
            setPadding(dp(ctx, 4), dp(ctx, 15), dp(ctx, 4), dp(ctx, 15))
            minimumHeight = dp(ctx, 68)
            isClickable = true
            isFocusable = true
            contentDescription = if (detail.isBlank()) heading else "$heading, $detail"
            background = RippleDrawable(
                ColorStateList.valueOf(raised), null, surface(ctx, Color.WHITE, 10))
            setOnClickListener { onTap() }
        }
        if (iconId != null) {
            val leading = icon(ctx, iconId, 24)
            content.addView(leading, LinearLayout.LayoutParams(dp(ctx, 24), dp(ctx, 24))
                .apply { rightMargin = dp(ctx, 15) })
        }
        val stack = column(ctx)
        content.addView(stack, LinearLayout.LayoutParams(0, -2, 1f))
        add(stack, label(ctx, heading, 15f, ink, true))
        if (detail.isNotBlank()) {
            add(stack, label(ctx, detail, 12f, muted).apply { maxLines = 2 }, 7)
        }
        val arrow = icon(ctx, R.drawable.ic_chevron_right, 21)
        content.addView(arrow, LinearLayout.LayoutParams(dp(ctx, 24), dp(ctx, 24)).apply {
            leftMargin = dp(ctx, 12)
        })
        return content
    }
}

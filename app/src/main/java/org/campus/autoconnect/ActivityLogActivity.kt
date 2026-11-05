package org.campus.autoconnect

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import java.text.DateFormat
import java.util.Date

/** Local, privacy-safe activity history with no passwords or session tokens. */
class ActivityLogActivity : Activity() {
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val outer = RejoinUi.column(this).apply { setBackgroundColor(RejoinUi.canvas) }
        val scroll = ScrollView(this).apply { setBackgroundColor(RejoinUi.canvas) }
        val body = RejoinUi.column(this).apply {
            setPadding(RejoinUi.dp(this@ActivityLogActivity, 23), RejoinUi.dp(this@ActivityLogActivity, 24),
                RejoinUi.dp(this@ActivityLogActivity, 23), RejoinUi.dp(this@ActivityLogActivity, 30))
        }
        scroll.addView(body)
        outer.addView(scroll, LinearLayout.LayoutParams(-1, -1))
        setContentView(outer)
        RejoinUi.applySystemBars(this, outer)

        val header = RejoinUi.row(this)
        header.addView(RejoinUi.smallAction(this, "←") { finish() },
            LinearLayout.LayoutParams(RejoinUi.dp(this, 38), -2))
        header.addView(RejoinUi.label(this, "Activity", 27f, RejoinUi.ink, true),
            LinearLayout.LayoutParams(0, -2, 1f))
        RejoinUi.add(body, header)
        RejoinUi.add(body, RejoinUi.label(this,
            "A small record of what Rejoin has done on this phone.",
            13f, RejoinUi.muted), 8)

        val panel = RejoinUi.inset(this)
        RejoinUi.add(body, panel, 28)
        val heading = RejoinUi.row(this)
        heading.addView(RejoinUi.label(this, "Recent events", 16f, RejoinUi.ink, true),
            LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(RejoinUi.smallAction(this, "Clear") {
            AlertDialog.Builder(this).setTitle("Clear activity history?")
                .setMessage("This only clears local connection event summaries. Your account settings stay unchanged.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear") { _, _ ->
                    ActivityHistory.clear(this)
                    refresh()
                }.show()
        })
        RejoinUi.add(panel, heading)
        list = RejoinUi.column(this)
        RejoinUi.add(panel, list, 11)

        RejoinUi.add(body, RejoinUi.label(this,
            "No accounts, passwords or FortiGate tokens are kept in this log.",
            12f, RejoinUi.muted), 19)
        refresh()
    }

    private fun refresh() {
        list.removeAllViews()
        val entries = ActivityHistory.latest(this, 24)
        if (entries.isEmpty()) {
            RejoinUi.add(list, RejoinUi.label(this,
                "Nothing to report yet. You're all set.", 14f, RejoinUi.muted), 20, 20)
        }
        for ((i, entry) in entries.withIndex()) {
            if (i > 0) RejoinUi.add(list, RejoinUi.rule(this), 14)
            val row = RejoinUi.row(this)
            val mark = RejoinUi.label(this, "●", 12f, RejoinUi.jade)
            row.addView(mark, LinearLayout.LayoutParams(RejoinUi.dp(this, 22), -2))
            val contents = RejoinUi.column(this)
            row.addView(contents, LinearLayout.LayoutParams(0, -2, 1f))
            RejoinUi.add(contents, RejoinUi.label(this, entry.event, 14f, RejoinUi.ink, true))
            RejoinUi.add(contents, RejoinUi.label(this,
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(Date(entry.time)), 12f, RejoinUi.muted), 6)
            RejoinUi.add(list, row, 14)
        }
    }
}

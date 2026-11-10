package org.campus.autoconnect

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import java.text.DateFormat
import java.util.Date

/** A private activity trail, not an analytics dashboard. */
class ActivityLogActivity : Activity() {
    private lateinit var events: LinearLayout
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroller = ScrollView(this).apply {
            setBackgroundColor(RejoinUi.canvas)
        }
        val body = RejoinUi.column(this).apply {
            val side = RejoinUi.dp(this@ActivityLogActivity, 24)
            setPadding(side, RejoinUi.dp(this@ActivityLogActivity, 24),
                side, side)
        }
        scroller.addView(body)
        setContentView(scroller)
        RejoinUi.applySystemBars(this, scroller)
        RejoinUi.header(this, body, "Activity")
        val top = RejoinUi.row(this)
        top.addView(RejoinUi.label(this, "ON THIS DEVICE", 11f, RejoinUi.muted, true)
            .apply { letterSpacing = .13f },
            LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(RejoinUi.smallAction(this, "Clear") {
            AlertDialog.Builder(this)
                .setTitle("Clear activity?")
                .setMessage("This won't change your saved networks or logins.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear") { _, _ ->
                    ActivityHistory.clear(this)
                    refresh()
                }.show()
        })
        RejoinUi.add(body, top, 25)
        events = RejoinUi.column(this)
        RejoinUi.add(body, events, 9)
        refresh()
    }

    private fun refresh() {
        if (!::events.isInitialized) return
        events.removeAllViews()
        val recent = ActivityHistory.latest(this, 24)
        if (recent.isEmpty()) {
            RejoinUi.add(events,
                RejoinUi.label(this, "No events yet.", 16f, RejoinUi.ink, true), 32)
            RejoinUi.add(events,
                RejoinUi.label(this, "When Rejoin reconnects, you'll see it here.",
                    13f, RejoinUi.muted), 10)
            return
        }
        recent.forEachIndexed { index, event ->
            if (index > 0) RejoinUi.add(events, RejoinUi.rule(this), 15)
            val row = RejoinUi.row(this)
            row.setPadding(0, RejoinUi.dp(this, 17), 0, RejoinUi.dp(this, 2))
            row.addView(RejoinUi.label(this, "●", 10f, RejoinUi.jade),
                LinearLayout.LayoutParams(RejoinUi.dp(this, 25), -2))
            val copy = RejoinUi.column(this)
            row.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
            RejoinUi.add(copy, RejoinUi.label(this, event.event, 15f, RejoinUi.ink, true))
            RejoinUi.add(copy, RejoinUi.label(this,
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(Date(event.time)), 12f, RejoinUi.muted), 7)
            events.addView(row)
        }
    }
}

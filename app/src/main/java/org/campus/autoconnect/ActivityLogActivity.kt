package org.campus.autoconnect

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.LinearLayout
import java.text.DateFormat
import java.util.Date

class ActivityLogActivity : Activity() {
    private lateinit var body: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        body = RejoinUi.screen(this, "Activity")
        refresh()
    }

    private fun refresh() {
        body.removeAllViews()
        val recent = ActivityHistory.latest(this, 24)
        if (recent.isEmpty()) {
            RejoinUi.add(body, RejoinUi.label(this, "No activity yet", 25f, bold = true))
            RejoinUi.add(body, RejoinUi.label(this,
                "Rejoin will show sign-ins and setup changes here.", 15f, RejoinUi.muted), 6)
            return
        }
        recent.forEachIndexed { index, event ->
            if (index > 0) RejoinUi.add(body, RejoinUi.rule(this), 12)
            RejoinUi.add(body, RejoinUi.label(this, event.event, 16f, bold = true), if (index == 0) 0 else 12)
            RejoinUi.add(body, RejoinUi.label(this,
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(Date(event.time)), 14f, RejoinUi.muted), 2)
        }
        RejoinUi.add(body, RejoinUi.smallAction(this, "Clear activity") { confirmClear() }, 16)
    }

    private fun confirmClear() {
        AlertDialog.Builder(this).setTitle("Clear activity?")
            .setMessage("This removes the connection history stored on this phone.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear") { _, _ ->
                ActivityHistory.clear(this)
                refresh()
            }.show().also { RejoinUi.destructive(it) }
    }
}

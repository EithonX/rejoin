package com.eithonx.rejoin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

class ActivityLogActivity : RejoinActivity() {
    private var revision by mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        revision++
    }

    @Composable
    override fun Screen() {
        val events = remember(revision) { ActivityHistory.latest(this, 24) }
        val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
        Page("Activity") {
            if (events.isEmpty()) ScreenIntro("No activity yet", "Sign-ins and changes appear here.")
            else {
                ListGroup {
                    events.forEachIndexed { index, event ->
                        if (index > 0) HorizontalDivider()
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(event.event, style = MaterialTheme.typography.titleSmall)
                            BodyText(formatter.format(Date(event.time)))
                        }
                    }
                }
                TextButton(onClick = {
                    confirm("Clear activity?", "This deletes the history on this device.", "Clear", "Cancel") {
                        ActivityHistory.clear(this)
                        revision++
                    }
                }) { Text("Clear activity") }
            }
        }
    }
}

package org.campus.autoconnect

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Small on-device event history, without usernames, passwords or network tokens. */
object ActivityHistory {
    data class Entry(val time: Long, val event: String)
    private const val STORE = "activity_history"
    private const val KEY = "events"

    @Synchronized
    fun record(context: Context, event: String) {
        val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val old = try { JSONArray(prefs.getString(KEY, "[]")) } catch (_: Exception) { JSONArray() }
        val entries = ArrayList<Entry>()
        for (i in 0 until old.length()) {
            val o = old.optJSONObject(i) ?: continue
            val time = o.optLong("time")
            val name = o.optString("event")
            if (time > 0L && name.isNotBlank()) entries.add(Entry(time, name))
        }
        val sanitized = event.take(92).replace('\n', ' ')
        if (entries.firstOrNull()?.let { it.event == sanitized && now - it.time < 300000L } == true)
            return
        entries.add(0, Entry(now, sanitized))
        val out = JSONArray()
        for (e in entries.take(24)) {
            out.put(JSONObject().put("time", e.time).put("event", e.event))
        }
        prefs.edit().putString(KEY, out.toString()).apply()
    }

    @Synchronized
    fun latest(context: Context, limit: Int = 4): List<Entry> {
        val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        val values = try { JSONArray(prefs.getString(KEY, "[]")) } catch (_: Exception) { JSONArray() }
        return (0 until minOf(values.length(), limit)).mapNotNull { i ->
            val o = values.optJSONObject(i) ?: return@mapNotNull null
            Entry(o.optLong("time"), o.optString("event"))
        }.filter { it.time > 0L && it.event.isNotBlank() }
    }
    @Synchronized
    fun clear(context: Context) {
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .edit().remove(KEY).apply()
    }
}

package org.campus.autoconnect

/** Pure selection rules for the optional per-router login feature. */
object LoginSelection {
    fun choose(linkedId: String?, defaultId: String?, availableIds: List<String>): String? {
        // Explicit assignment is never silently substituted after an account disappears.
        if (linkedId != null) return linkedId.takeIf { it in availableIds }
        return defaultId?.takeIf { it in availableIds } ?: availableIds.firstOrNull()
    }
}

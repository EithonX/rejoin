package com.eithonx.rejoin

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal enum class AppAppearance(val label: String) {
    SYSTEM("System default"), LIGHT("Light"), DARK("Dark");

    fun isDark(systemDark: Boolean) = this == DARK || (this == SYSTEM && systemDark)
}

internal object Appearance {
    var current by mutableStateOf(AppAppearance.SYSTEM)
        private set

    fun load(context: Context) {
        val name = context.getSharedPreferences("appearance", Context.MODE_PRIVATE).getString("theme", null)
        current = AppAppearance.entries.firstOrNull { it.name == name } ?: AppAppearance.SYSTEM
    }

    fun set(context: Context, appearance: AppAppearance) {
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit()
            .putString("theme", appearance.name).apply()
        current = appearance
    }
}

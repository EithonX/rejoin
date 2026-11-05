package org.campus.autoconnect

import android.content.Context
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/**
 * Each profile is an explicitly accepted Wi-Fi access point, not a wildcard SSID.
 * A profile may use the default saved login or one particular saved login.
 */
object TrustedNetworks {
    data class Profile(
        val id: String,
        val label: String,
        val ssid: String,
        val bssid: String,
        val loginId: String? = null
    )
    private const val PREF = "trusted_networks"
    private const val STORE = "profiles_json"
    private const val MIGRATED = "migrated_from_v1"
    private const val LIMIT = 32

    fun matches(profile: Profile, ssid: String, bssid: String): Boolean =
        profile.ssid == ssid && profile.bssid.equals(bssid, true)

    @Synchronized
    fun all(context: Context): List<Profile> {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(MIGRATED, false)) migrate(context)
        return parse(prefs.getString(STORE, null))
    }

    private fun parse(raw: String?): List<Profile> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val json = JSONArray(raw)
            val result = ArrayList<Profile>()
            for (i in 0 until minOf(json.length(), LIMIT)) {
                val item = json.optJSONObject(i) ?: continue
                val ssid = item.optString("ssid")
                val bssid = item.optString("bssid")
                val id = item.optString("id")
                if (ssid.isBlank() || bssid.isBlank() || id.isBlank()) continue
                result.add(Profile(id, item.optString("label", ssid).ifBlank { ssid },
                    ssid, bssid, item.optString("loginId").ifBlank { null }))
            }
            result
        } catch (_: Exception) { emptyList() }
    }

    private fun persist(context: Context, profiles: List<Profile>): Boolean {
        val array = JSONArray()
        for (network in profiles.take(LIMIT)) {
            array.put(JSONObject()
                .put("id", network.id)
                .put("label", network.label)
                .put("ssid", network.ssid)
                .put("bssid", network.bssid.lowercase())
                .put("loginId", network.loginId ?: ""))
        }
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(STORE, array.toString()).putBoolean(MIGRATED, true).commit()
    }

    private fun migrate(context: Context) {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val prior = parse(p.getString(STORE, null))
        if (prior.isNotEmpty()) {
            p.edit().putBoolean(MIGRATED, true).commit()
            return
        }
        val legacy = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
        val ssid = legacy.getString("ssid", null)
        val bssid = legacy.getString("bssid", null)
        val migrated = if (!ssid.isNullOrBlank() && !bssid.isNullOrBlank())
            listOf(Profile(UUID.randomUUID().toString(), ssid, ssid, bssid.lowercase()))
        else emptyList()
        persist(context, migrated)
    }

    @Synchronized
    fun find(context: Context, ssid: String, bssid: String): Profile? =
        all(context).firstOrNull { matches(it, ssid, bssid) }

    @Synchronized
    fun find(context: Context, snapshot: WifiProbe.Snapshot): Profile? =
        find(context, snapshot.ssid, snapshot.bssid)

    @Synchronized
    fun saveCurrent(context: Context, snapshot: WifiProbe.Snapshot): Profile? {
        val entries = all(context).toMutableList()
        entries.firstOrNull { matches(it, snapshot.ssid, snapshot.bssid) }?.let { return it }
        if (entries.size >= LIMIT) return null
        val created = Profile(UUID.randomUUID().toString(), snapshot.ssid,
            snapshot.ssid, snapshot.bssid.lowercase())
        entries.add(created)
        return if (persist(context, entries)) created else null
    }

    @Synchronized
    fun rename(context: Context, id: String, displayName: String): Boolean {
        val name = displayName.trim().take(48)
        if (name.isEmpty()) return false
        val entries = all(context)
        if (entries.none { it.id == id }) return false
        return persist(context, entries.map { if (it.id == id) it.copy(label = name) else it })
    }

    @Synchronized
    fun assignLogin(context: Context, profileId: String, loginId: String?): Boolean {
        val entries = all(context)
        if (entries.none { it.id == profileId }) return false
        if (loginId != null && CredentialVault.list(context).none { it.id == loginId }) return false
        return persist(context, entries.map {
            if (it.id == profileId) it.copy(loginId = loginId) else it
        })
    }

    @Synchronized
    fun unassignDeletedLogin(context: Context, loginId: String) {
        val entries = all(context)
        if (entries.any { it.loginId == loginId }) {
            persist(context, entries.map {
                if (it.loginId == loginId) it.copy(loginId = null) else it
            })
        }
    }

    @Synchronized
    fun remove(context: Context, id: String): Boolean {
        val entries = all(context)
        if (entries.none { it.id == id }) return false
        return persist(context, entries.filterNot { it.id == id })
    }
}

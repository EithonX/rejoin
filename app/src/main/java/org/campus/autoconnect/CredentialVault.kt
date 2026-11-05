package org.campus.autoconnect

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Local, encrypted multi-login vault. A single default account is sufficient
 * for most users. Router-specific assignment is entirely optional.
 *
 * Migrates the previous working v1 encrypted credential record in place.
 */
object CredentialVault {
    data class Credentials(val username: String, val password: String)
    data class SavedLogin(val id: String, val label: String, val username: String)

    private data class Record(val id: String, val label: String, val user: String, val password: String)
    private data class Vault(val defaultId: String?, val items: List<Record>)

    private const val ALIAS = "campus_autoconnect_aes_v1"
    private const val PREF = "private_auth"
    private const val LEGACY = "encrypted_credentials"
    private const val KEY = "encrypted_accounts_v2"
    private const val MAX_ACCOUNTS = 16

    private fun secretKey(create: Boolean): SecretKey? {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        if (!ks.containsAlias(ALIAS)) {
            if (!create) return null
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
            generator.generateKey()
        }
        return (ks.getEntry(ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    private fun encrypt(clear: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey(true))
        val packed = cipher.iv + cipher.doFinal(clear)
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): ByteArray? {
        return try {
            val data = Base64.decode(encoded, Base64.DEFAULT)
            if (data.size < 29) return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(false),
                GCMParameterSpec(128, data.copyOfRange(0, 12)))
            cipher.doFinal(data.copyOfRange(12, data.size))
        } catch (_: Exception) { null }
    }

    private fun parse(encoded: String): Vault? {
        val bytes = decrypt(encoded) ?: return null
        return try {
            val json = JSONObject(String(bytes, Charsets.UTF_8))
            val data = json.optJSONArray("accounts") ?: return null
            val items = mutableListOf<Record>()
            for (i in 0 until minOf(data.length(), MAX_ACCOUNTS)) {
                val obj = data.optJSONObject(i) ?: continue
                val id = obj.optString("id")
                val user = obj.optString("user")
                val pass = obj.optString("password")
                if (id.isBlank() || user.isBlank() || pass.isBlank()) continue
                items.add(Record(id, obj.optString("label").ifBlank { "Campus login" },
                    user, pass))
            }
            Vault(json.optString("defaultId").takeIf { it.isNotBlank() }, items)
        } catch (_: Exception) { null }
        finally { bytes.fill(0) }
    }

    private fun store(context: Context, state: Vault): Boolean {
        val json = JSONObject().put("version", 2).put("defaultId", state.defaultId ?: "")
        val accounts = JSONArray()
        for (a in state.items) {
            accounts.put(JSONObject().put("id", a.id).put("label", a.label)
                .put("user", a.user).put("password", a.password))
        }
        json.put("accounts", accounts)
        val clear = json.toString().toByteArray(Charsets.UTF_8)
        val packed = try { encrypt(clear) } catch (_: Exception) { return false }
        finally { clear.fill(0) }
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY, packed).commit()
    }

    private fun load(context: Context): Vault? {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val new = prefs.getString(KEY, null)
        if (new != null) return parse(new) // Never overwrite a corrupt vault.

        val legacy = prefs.getString(LEGACY, null) ?: return Vault(null, emptyList())
        val raw = decrypt(legacy) ?: return null
        val creds = try {
            val json = JSONObject(String(raw, Charsets.UTF_8))
            Credentials(json.getString("u"), json.getString("p"))
        } catch (_: Exception) { return null }
        finally { raw.fill(0) }
        val migrated = Vault(null, listOf(Record(UUID.randomUUID().toString(),
            "Campus login", creds.username, creds.password)))
        val newState = migrated.copy(defaultId = migrated.items[0].id)
        if (!store(context, newState)) return null
        prefs.edit().remove(LEGACY).commit()
        return newState
    }

    @Synchronized
    fun list(context: Context): List<SavedLogin> = load(context)?.items?.map {
        SavedLogin(it.id, it.label, it.user)
    } ?: emptyList()

    @Synchronized
    fun defaultId(context: Context): String? = load(context)?.let { state ->
        state.defaultId?.takeIf { candidate -> state.items.any { it.id == candidate } }
            ?: state.items.firstOrNull()?.id
    }

    @Synchronized
    fun setDefault(context: Context, id: String): Boolean {
        val state = load(context) ?: return false
        if (state.items.none { it.id == id }) return false
        return store(context, state.copy(defaultId = id))
    }

    @Synchronized
    fun upsert(context: Context, id: String?, name: String, user: String, newPassword: String): Boolean {
        val current = load(context) ?: return false
        val label = name.trim().take(48).ifEmpty { "Campus login" }
        val uid = user.trim()
        if (uid.isEmpty() || uid.length > 128 || newPassword.length > 512) return false
        val existing = current.items.firstOrNull { it.id == id }
        val password = newPassword.ifBlank { existing?.password ?: "" }
        if (password.isEmpty()) return false
        if (existing == null && current.items.size >= MAX_ACCOUNTS) return false
        val entry = Record(existing?.id ?: UUID.randomUUID().toString(), label, uid, password)
        val next = if (existing != null)
            current.items.map { if (it.id == existing.id) entry else it }
        else current.items + entry
        val selected = current.defaultId ?: next.first().id
        return store(context, Vault(selected, next))
    }

    @Synchronized
    fun remove(context: Context, id: String): Boolean {
        val state = load(context) ?: return false
        if (state.items.none { it.id == id }) return false
        val items = state.items.filterNot { it.id == id }
        val next = items.firstOrNull { it.id == state.defaultId }?.id ?: items.firstOrNull()?.id
        if (!store(context, Vault(next, items))) return false
        TrustedNetworks.unassignDeletedLogin(context, id)
        return true
    }

    @Synchronized
    fun get(context: Context, id: String): Credentials? =
        load(context)?.items?.firstOrNull { it.id == id }?.let {
            Credentials(it.user, it.password)
        }

    @Synchronized
    fun resolve(context: Context, profile: TrustedNetworks.Profile): Credentials? {
        val state = load(context) ?: return null
        val id = profile.loginId ?: state.defaultId ?: state.items.firstOrNull()?.id
        // An explicitly linked account that has gone missing must never silently
        // submit a different account's password.
        return state.items.firstOrNull { it.id == id }?.let { Credentials(it.user, it.password) }
    }

    fun hasLoginFor(context: Context, profile: TrustedNetworks.Profile): Boolean =
        resolve(context, profile) != null

    // Small compatibility surface for older beta code.
    @Synchronized
    fun save(context: Context, username: String, password: String): Boolean =
        upsert(context, defaultId(context), "Campus login", username, password)

    @Synchronized
    fun read(context: Context): Credentials? {
        val selected = defaultId(context) ?: return null
        return get(context, selected)
    }

    @Synchronized
    fun hasCredentials(context: Context): Boolean = load(context)?.items?.isNotEmpty() == true

    @Synchronized
    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .remove(KEY).remove(LEGACY).commit()
        context.getSharedPreferences("profile", Context.MODE_PRIVATE)
            .edit().putBoolean("auto_enabled", false).commit()
        TrustedNetworks.resetAllAssignments(context)
        try {
            val store = KeyStore.getInstance("AndroidKeyStore")
            store.load(null)
            if (store.containsAlias(ALIAS)) store.deleteEntry(ALIAS)
        } catch (_: Exception) {}
    }
}

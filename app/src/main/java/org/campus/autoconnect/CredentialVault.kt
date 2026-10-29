package org.campus.autoconnect

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stored only on this Android device; not included in app backups. */
object CredentialVault {
    data class Credentials(val username: String, val password: String)
    private const val ALIAS = "campus_autoconnect_aes_v1"
    private const val PREF = "private_auth"
    private const val ITEM = "encrypted_credentials"

    private fun secretKey(create: Boolean): SecretKey? {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        if (!ks.containsAlias(ALIAS)) {
            if (!create) return null
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).build()
            )
            generator.generateKey()
        }
        return (ks.getEntry(ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    fun save(context: Context, username: String, password: String): Boolean {
        if (username.isBlank() || password.isBlank() || username.length > 128 || password.length > 512) return false
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(true))
            val bytes = JSONObject().put("u", username.trim()).put("p", password)
                .toString().toByteArray(Charsets.UTF_8)
            val packed = cipher.iv + cipher.doFinal(bytes)
            bytes.fill(0)
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putString(ITEM, Base64.encodeToString(packed, Base64.NO_WRAP)).commit()
        } catch (_: Exception) { false }
    }

    fun read(context: Context): Credentials? {
        return try {
            val encoded = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getString(ITEM, null) ?: return null
            val data = Base64.decode(encoded, Base64.DEFAULT)
            if (data.size < 29) return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(false), GCMParameterSpec(128, data.copyOfRange(0, 12)))
            val clear = cipher.doFinal(data.copyOfRange(12, data.size))
            val json = JSONObject(String(clear, Charsets.UTF_8))
            clear.fill(0)
            Credentials(json.getString("u"), json.getString("p"))
        } catch (_: Exception) { null }
    }

    fun hasCredentials(context: Context): Boolean =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).contains(ITEM)

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("profile", Context.MODE_PRIVATE)
            .edit().putBoolean("auto_enabled", false).commit()
        try {
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)
            if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS)
        } catch (_: Exception) {}
    }
}

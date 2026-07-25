package dev.mtrp.core.crypto

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * MTRP-SPEC-v0.1 Section 3.2 — Key Pair Storage
 * Android Keystore backed via EncryptedSharedPreferences
 * Author: K. Bhanutej
 */
class AndroidIdentityStore(private val context: Context) : IdentityStore {

    private val keyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val prefs by lazy {
        EncryptedSharedPreferences.create(
            "mtrp_identity",
            keyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override suspend fun load(): NodeIdentity? {
        val pubB64  = prefs.getString("pub_key",  null) ?: return null
        val privB64 = prefs.getString("priv_key", null) ?: return null
        val pub  = Base64.decode(pubB64,  Base64.NO_WRAP)
        val priv = Base64.decode(privB64, Base64.NO_WRAP)
        return NodeIdentity.fromStoredKeys(pub, priv)
    }

    override suspend fun save(identity: NodeIdentity) {
        prefs.edit()
            .putString("pub_key",  Base64.encodeToString(identity.publicKey,  Base64.NO_WRAP))
            .putString("priv_key", Base64.encodeToString(identity.privateKey, Base64.NO_WRAP))
            .apply()
    }

    override suspend fun clear() {
        prefs.edit().clear().apply()
    }

    override fun isHardwareBacked(): Boolean {
        return try {
            val ks = java.security.KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)
            true
        } catch (e: Exception) { false }
    }
}

package li.velvetchat.core.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import li.velvetchat.core.model.Session
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * the session encrypted with a key from the android keystore
 *
 * the key is generated inside the keystore and never comes out, not extractable, us included. on a
 * device with a secure element it is protected by hardware too. copying the preferences file to
 * another machine therefore yields nothing usable
 *
 * `EncryptedSharedPreferences` is deliberately not used: the library is deprecated, and the job is
 * simple enough not to justify one more dependency
 */
class AndroidSessionStore(
    context: Context,
    private val alias: String = DEFAULT_ALIAS,
) : SessionStore {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun load(): Session? {
        val blob = prefs.getString(KEY_PAYLOAD, null) ?: return null
        val iv = prefs.getString(KEY_IV, null) ?: return null

        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(
                    Cipher.DECRYPT_MODE,
                    secretKey(),
                    GCMParameterSpec(TAG_BITS, Base64.decode(iv, Base64.NO_WRAP)),
                )
            }
            SessionCodec.decode(cipher.doFinal(Base64.decode(blob, Base64.NO_WRAP)))
        }.getOrElse {
            // key gone or data corrupted, typically after a device restore. purge rather than
            // keep a state that will fail on every launch without ever repairing itself
            clear()
            null
        }
    }

    override fun save(session: Session) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, secretKey())
        }
        val payload = cipher.doFinal(SessionCodec.encode(session))

        prefs.edit()
            .putString(KEY_PAYLOAD, Base64.encodeToString(payload, Base64.NO_WRAP))
            // the cipher generates the iv on every encryption and it has to be kept. it is not
            // secret. reusing one, on the other hand, would break gcm
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY_PAYLOAD).remove(KEY_IV).apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    // no user authentication required, because the background notification
                    // pipeline has to be able to read the session back with the device locked.
                    // an explicit trade: requiring it would make encrypted notifications
                    // impossible
                    .setUserAuthenticationRequired(false)
                    .build()
            )
        }.generateKey()
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val DEFAULT_ALIAS = "matriscord.session"
        const val PREFS = "matriscord_session"
        const val KEY_PAYLOAD = "payload"
        const val KEY_IV = "iv"
    }
}

package li.velvetchat.core.session

import kotlinx.serialization.json.Json
import li.velvetchat.core.model.Session

/**
 * where the session gets stored, safely
 *
 * do nothing and what sits on disk is an access token in the clear, which is enough to take over
 * the account with no password and no second factor
 *
 * the encryption is platform specific, and this, rather than the sdk layer, is where
 * `expect`/`actual` earns its keep:
 *
 * | platform | mechanism | what protects the key |
 * |---|---|---|
 * | android | aes-gcm, key in the android keystore | secure hardware, key not extractable |
 * | windows | dpapi (`CryptProtectData`) | the windows user account |
 *
 * either way the key never leaves the platform and is never written by us
 */
interface SessionStore {

    /** null when there is no session, or when the decryption fails (see [load]) */
    fun load(): Session?

    fun save(session: Session)

    fun clear()
}

/**
 * shared serialisation, so both platforms store exactly the same format. not cosmetic: it is what
 * lets you diagnose a problem on one platform by reading the other one's file
 */
internal object SessionCodec {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(session: Session): ByteArray =
        json.encodeToString(session).encodeToByteArray()

    /**
     * @return null when the content is unusable
     *
     * a failure here is not harmless and must not be quietly treated as "no session". it means
     * either corruption or a platform key that changed, a device restore, a keystore reset, a
     * different windows account. the caller takes the person back to the login screen rather than
     * failing
     */
    fun decode(bytes: ByteArray): Session? = runCatching {
        json.decodeFromString<Session>(bytes.decodeToString())
    }.getOrNull()
}

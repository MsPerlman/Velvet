package li.velvetchat.core.session

import com.sun.jna.platform.win32.Crypt32Util
import li.velvetchat.core.model.Session
import java.io.File

/**
 * the session encrypted by dpapi, windows' answer to the android keystore
 *
 * `CryptProtectData` encrypts with a key derived from the windows user account, so the file is
 * only decryptable by that account on that machine. copied anywhere else it is inert, the same
 * guarantee as on android through a different mechanism
 *
 * jna is already needed to load the sdk's native lib, and `jna-platform` supplies the crypt32
 * binding without writing any native code
 *
 * @param dataDir the app's data directory
 */
class WindowsSessionStore(
    dataDir: File,
    fileName: String = "session.dpapi",
) : SessionStore {

    private val file = File(dataDir, fileName)

    override fun load(): Session? {
        if (!file.exists()) return null
        return runCatching {
            SessionCodec.decode(Crypt32Util.cryptUnprotectData(file.readBytes()))
        }.getOrElse {
            // a different windows account, a restored profile, or a corrupt file. purge rather
            // than keep a state that will fail on every launch without ever repairing itself
            clear()
            null
        }
    }

    override fun save(session: Session) {
        file.parentFile?.mkdirs()
        file.writeBytes(Crypt32Util.cryptProtectData(SessionCodec.encode(session)))
    }

    override fun clear() {
        runCatching { file.delete() }
    }

    // an acl hardening was tried here and taken back out
    //
    // the idea was to restrict the file to its owner alone, as defence in depth. what it actually
    // did was make the session unreadable: on windows a file's owner is frequently the
    // administrators group rather than the current user, so replacing the acl with an owner only
    // entry locks out the very account that has to read it
    //
    // dpapi already provides the guarantee that matters, the content is only decryptable by this
    // windows account on this machine. another account can read the bytes and get nothing from
    // them. the hardening added a real functional risk for a marginal gain
}

package li.velvetchat.core.session

import li.velvetchat.core.model.Session
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.io.File
import java.nio.file.Files

/**
 * checks that dpapi actually encrypts and decrypts, rather than assuming it because the code
 * compiles. a test that does not take the real path proves nothing
 *
 * these only run on windows, dpapi existing nowhere else
 */
class WindowsSessionStoreTest {

    private val onWindows =
        System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    private fun sample(userId: String = "@test:exemple.org") = Session(
        userId = userId,
        deviceId = "ABCDEFGHIJ",
        homeserverUrl = "https://exemple.org",
        accessToken = "syt_jeton_tres_secret_0123456789",
        refreshToken = "syr_rafraichissement",
        slidingSyncNative = true,
    )

    private fun tempDir(): File =
        Files.createTempDirectory("velvet-session-test").toFile().apply { deleteOnExit() }

    @Test
    fun `aller-retour complet`() {
        if (!onWindows) return
        val store = WindowsSessionStore(tempDir())

        assertNull(store.load(), "aucune session au départ")

        val original = sample()
        store.save(original)
        assertEquals(original, store.load(), "la session relue doit être identique")
    }

    @Test
    fun `le jeton n'apparait pas en clair sur le disque`() {
        if (!onWindows) return
        val dir = tempDir()
        val store = WindowsSessionStore(dir)
        val session = sample()
        store.save(session)

        val raw = File(dir, "session.dpapi").readBytes()
        val asText = raw.decodeToString()

        // the point that matters: an access token takes over the account with no password and no
        // second factor. if it ends up readable in the file, this whole mechanism was for nothing
        assertFalse(
            asText.contains(session.accessToken),
            "le jeton d'accès ne doit JAMAIS apparaître en clair",
        )
        assertFalse(
            asText.contains(session.userId),
            "l'identifiant utilisateur ne doit pas apparaître en clair",
        )
        assertTrue(raw.isNotEmpty())
    }

    @Test
    fun `un fichier corrompu est purge plutot que de bloquer chaque lancement`() {
        if (!onWindows) return
        val dir = tempDir()
        val store = WindowsSessionStore(dir)
        store.save(sample())

        val file = File(dir, "session.dpapi")
        file.writeBytes(byteArrayOf(0, 1, 2, 3, 4, 5))

        // do not throw, take the person back to the login screen
        assertNull(store.load())
        // and repair itself, otherwise the failure repeats on every launch
        assertFalse(file.exists(), "le fichier illisible doit être purgé")
    }

    @Test
    fun `clear efface la session`() {
        if (!onWindows) return
        val dir = tempDir()
        val store = WindowsSessionStore(dir)
        store.save(sample())

        store.clear()

        assertNull(store.load())
        assertFalse(File(dir, "session.dpapi").exists())
    }

    @Test
    fun `une seconde session remplace la premiere`() {
        if (!onWindows) return
        val store = WindowsSessionStore(tempDir())
        store.save(sample(userId = "@premier:exemple.org"))
        store.save(sample(userId = "@second:exemple.org"))

        assertEquals("@second:exemple.org", store.load()?.userId)
    }

    @Test
    fun `le format serialise est bien du JSON lisible avant chiffrement`() {
        // on the shared encoder, so it runs anywhere. this is the format both platforms encrypt,
        // and being able to read it back is what lets you diagnose one platform's problem from the
        // other
        val encoded = SessionCodec.encode(sample()).decodeToString()
        assertContains(encoded, "accessToken")
        assertEquals(sample(), SessionCodec.decode(encoded.encodeToByteArray()))
    }
}

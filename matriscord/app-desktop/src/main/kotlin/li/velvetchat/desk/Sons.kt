package li.velvetchat.desk

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.concurrent.thread

/**
 * velvetdesk's sounds: "message" when a notifying message arrives, "ring" for an incoming call,
 * and the seven short ones a call makes as you press its buttons. opus in `resources/sons`,
 * decoded once by [decoderVocal], the same decoder as the voice messages
 *
 * why a call needs them at all: every one of these buttons does something you cannot see the
 * result of. you press mute and nothing on your screen proves that the others stopped hearing
 * you. the sound is the receipt
 */
object Sons {
    private val message by lazy { charger("message") }
    private val sonnerie by lazy { charger("sonnerie") }
    @Volatile private var sonnerieJeton = 0

    private val vocalEntree by lazy { charger("vocal-entree") }
    private val vocalSortie by lazy { charger("vocal-sortie") }
    private val microCoupe by lazy { charger("micro-coupe") }
    private val microOuvert by lazy { charger("micro-ouvert") }
    private val sonCoupeSfx by lazy { charger("son-coupe") }
    private val sonOuvert by lazy { charger("son-ouvert") }
    private val partage by lazy { charger("partage") }

    private fun charger(nom: String): SonDecode? = runCatching {
        Sons::class.java.getResourceAsStream("/sons/$nom.ogg")!!.use { decoderVocal(it.readBytes()) }
    }.getOrNull()

    fun message() {
        if (Voix.sonCoupe) return
        val son = message ?: return
        thread(name = "son-message", isDaemon = true) { jouer(son) { true } }
    }

    /** loops until [arreterSonnerie] */
    fun sonner() {
        val moi = ++sonnerieJeton
        thread(name = "sonnerie", isDaemon = true) {
            val son = sonnerie ?: return@thread
            while (sonnerieJeton == moi && !Voix.sonCoupe) jouer(son) { sonnerieJeton == moi }
        }
    }

    fun arreterSonnerie() { sonnerieJeton++ }

    fun vocalEntree() = effet(vocalEntree)
    fun vocalSortie() = effet(vocalSortie)
    fun microCoupe() = effet(microCoupe)
    fun microOuvert() = effet(microOuvert)
    fun sonCoupe() = effet(sonCoupeSfx)
    fun sonOuvert() = effet(sonOuvert)
    fun partageLance() = effet(partage)

    /**
     * a short one, played over whatever else is going on
     *
     * two exceptions to the deafened rule, and they are the point of the whole thing: the sounds
     * that *say* you went deaf, and the one that says you came back. refusing to play the first
     * because you are now deaf would mean the only button whose effect you cannot see is also the
     * only one that stays silent
     */
    private fun effet(son: SonDecode?) {
        val s = son ?: return
        thread(name = "son-effet", isDaemon = true) { jouer(s) { true } }
    }

    private fun jouer(son: SonDecode, continuer: () -> Boolean) {
        val format = AudioFormat(48_000f, 16, son.canaux, true, false)
        val ligne = runCatching { AudioSystem.getSourceDataLine(format).apply { open(format) } }.getOrNull() ?: return
        val pas = 2 * son.canaux
        ligne.start()
        try {
            var i = 0
            while (i < son.pcm.size && continuer()) {
                val n = minOf(2_400 * pas, son.pcm.size - i)
                ligne.write(son.pcm, i, n)
                i += n
            }
            if (continuer()) ligne.drain()
        } finally {
            ligne.stop()
            ligne.close()
        }
    }
}

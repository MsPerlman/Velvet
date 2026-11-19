package li.velvetchat.core

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException

/**
 * the whole point of [essayer] is the one behaviour `runCatching` gets wrong, so that is what is
 * tested. the cancellation case is the only one that matters and the only one that would
 * regress silently if somebody swapped it back
 */
class EssayerTest {

    /**
     * runs a suspending block and gives back what it produced
     *
     * no `runTest`, because `kotlinx-coroutines-test` is not a dependency here and one assertion
     * does not justify adding it. nothing under test actually suspends: [essayer] calls its block
     * straight away, so the coroutine finishes before `startCoroutine` returns, and a failure
     * comes back out on this thread
     */
    private fun <T> lancer(bloc: suspend () -> T): T {
        var resultat: Result<T>? = null
        bloc.startCoroutine(Continuation(EmptyCoroutineContext) { resultat = it })
        return checkNotNull(resultat) { "le bloc a suspendu, ce que ce coureur ne sait pas faire" }
            .getOrThrow()
    }

    @Test
    fun `une valeur passe telle quelle`() {
        assertEquals(7, lancer { essayer { 7 } }.getOrNull())
    }

    @Test
    fun `une panne ordinaire devient un echec`() {
        val r = lancer { essayer { error("serveur muet") } }
        assertTrue(r.isFailure)
        assertEquals("serveur muet", r.exceptionOrNull()?.message)
    }

    /**
     * the one that justifies the file
     *
     * `runCatching` captures this and hands back a `Result.failure`, which is how a cancelled
     * coroutine goes on believing it may continue
     */
    @Test
    fun `une annulation ressort au lieu d etre capturee`() {
        assertFailsWith<CancellationException> {
            lancer { essayer { throw CancellationException("ecran ferme") } }
        }
    }

    /** the contrast, written as a test so the difference is not only prose in a comment */
    @Test
    fun `runCatching lui avale l annulation`() {
        val r = runCatching { throw CancellationException("ecran ferme") }
        assertTrue(r.isFailure, "si ceci casse, kotlin a change et le fichier peut disparaitre")
        assertTrue(r.exceptionOrNull() is CancellationException)
    }
}

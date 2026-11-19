package li.velvetchat.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * what these tests protect: a badly decoded code does not fail here, it fails after the
 * registration, when joining the space. the worst possible place, since the account already exists
 * and the person lands in exactly the empty screen we were trying to avoid
 */
class InvitationTest {

    @Test
    fun `l aller-retour rend le meme jeton et le meme espace`() {
        val code = CodeInvitation.encoder("aBc123", "!xyz:exemple.org")
        assertEquals("aBc123!!xyz:exemple.org", code)
        val invitation = CodeInvitation.decoder(code)!!
        assertEquals("aBc123", invitation.jeton)
        assertEquals("!xyz:exemple.org", invitation.idEspace)
    }

    @Test
    fun `les espaces autour du code colle ne genent pas`() {
        val code = CodeInvitation.encoder("aBc123", "!xyz:exemple.org")
        assertEquals("!xyz:exemple.org", CodeInvitation.decoder("  $code\n")!!.idEspace)
    }

    @Test
    fun `un code sans separateur est refuse`() {
        assertNull(CodeInvitation.decoder("pasdeseparateur"))
    }

    @Test
    fun `un espace qui ne commence pas par le point d exclamation est refuse`() {
        assertNull(CodeInvitation.decoder("jeton!xyz:exemple.org"))
    }

    @Test
    fun `un espace sans deux-points est refuse`() {
        // isolates the colon check precisely: the leading `!` is there, only the colon is
        // missing, which is what copying the code by hand and truncating it would produce
        assertNull(CodeInvitation.decoder("jeton!!xyzexemplefr"))
    }

    @Test
    fun `un jeton vide avant le separateur est refuse`() {
        assertNull(CodeInvitation.decoder("!xyz:exemple.org"))
    }

    @Test
    fun `un code vide ou juste le separateur ne plante pas`() {
        assertNull(CodeInvitation.decoder(""))
        assertNull(CodeInvitation.decoder("!"))
    }
}

class LienInvitationTest {
    @Test
    fun `le lien velvet rend l espace`() {
        val lien = LienInvitation.encoder("!abc:exemple.org")
        assertEquals("!abc:exemple.org", LienInvitation.cible("  $lien \n"))
    }

    @Test
    fun `matrix to et les identifiants nus passent aussi`() {
        assertEquals("!abc:exemple.org", LienInvitation.cible("https://matrix.to/#/%21abc%3Aexemple.org?via=exemple.org"))
        assertEquals("#famille:exemple.org", LienInvitation.cible("#famille:exemple.org"))
        assertEquals("!abc:exemple.org", LienInvitation.cible("!abc:exemple.org"))
    }
}

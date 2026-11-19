package li.velvetchat.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

/**
 * the mentions badge and the empty states
 *
 * two tiny surfaces, but each carrying a bound: the one where the number stops being written as is,
 * and the one where a screen with no content has to say it is empty rather than stay blank
 */
class BadgeEtVideTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun unePetiteValeurEstEcriteTelleQuelle() {
        compose.setContent { VelvetTheme { Badge(7) } }
        compose.onNodeWithText("7").assertIsDisplayed()
    }

    /**
     * the bound, on both sides. 99 is still written in full, and 100 switches to "99+"
     *
     * testing both and not only one: a bound written `>=` instead of `>` would show "99+" for
     * exactly 99, which a test at 100 alone would never see
     */
    @Test
    fun quatreVingtDixNeufSecritEnEntier() {
        compose.setContent { VelvetTheme { Badge(99) } }
        compose.onNodeWithText("99").assertIsDisplayed()
    }

    @Test
    fun auDelaDeQuatreVingtDixNeufOnPlafonne() {
        compose.setContent { VelvetTheme { Badge(100) } }
        compose.onNodeWithText("99+").assertIsDisplayed()
    }

    @Test
    fun unTresGrandNombreResteLisible() {
        compose.setContent { VelvetTheme { Badge(4321) } }
        compose.onNodeWithText("99+").assertIsDisplayed()
    }

    /**
     * an empty screen has to justify itself. the main message and its detail both show: the short
     * version says there is nothing, and the detail says why or what to do
     */
    @Test
    fun unEtatVideDitCeQuiManqueEtPourquoi() {
        compose.setContent {
            VelvetTheme {
                Vide("Aucune photo ni vidéo.", "Les images partagées apparaîtront ici.")
            }
        }
        compose.onNodeWithText("Aucune photo ni vidéo.").assertIsDisplayed()
        compose.onNodeWithText("Les images partagées apparaîtront ici.").assertIsDisplayed()
    }

    @Test
    fun unEtatVideSansPrecisionSAfficheQuandMeme() {
        compose.setContent { VelvetTheme { Vide("Rien à voir.") } }
        compose.onNodeWithText("Rien à voir.").assertIsDisplayed()
    }
}

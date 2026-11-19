package li.velvetchat.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * the app's icon vocabulary
 *
 * the interface used emoji as icons — a bell for notifications, two heads for members, a mic for
 * the mic. it worked, and it was wrong four times over, none of them cosmetic. an emoji is not
 * drawn by us, so it changes between phones, android versions and manufacturers, which means the
 * interface has their visual identity and not its own. it is coloured, and not in our colours: a
 * yellow bell takes neither the accent nor the grey of an inactive control, which is exactly what
 * every icon in the reference does. its optical weight is wrong, being drawn for body size, so at
 * 20 sp beside a 20 sp heading it always looks heavier — that is what made the header bars look
 * unfinished. and it has one shape only, where the reference switches outline to fill to say "you
 * are here"
 *
 * the set is phosphor (MIT). it has a filled variant for every icon, so the outline and fill pair
 * comes free, where lucide and tabler are stroke only and the filled state would have to be
 * invented. its geometry is round with softened corners, which is the family resemblance we were
 * after — lucide descends from feather, thinner and more angular, very good and for a different app
 * than this one. and each icon is a single filled path on a 256 grid, so the conversion is
 * mechanical and checkable, where a stroke set means carrying widths and line caps, every one a
 * chance to drift
 *
 * the noun project is out for a reason that is not about drawing: different authors, different
 * licences, most wanting named attribution. it is a bank and not a set, and nothing guarantees two
 * neighbouring icons came from the same hand
 *
 * the svgs live in `ICONS/phosphor/`, with `ICONS/lucide/` kept for comparison if it reopens
 *
 * this replaces no emoji that is an emoji — picker, reactions, messages — which go through the font
 *
 * an earlier claim here was wrong twice over: that system rendering is what we want because it is
 * what the other person sees. every client renders emoji with its own set, so "what the other
 * person sees" does not exist, only characters in transit. and since one must be picked anyway it
 * may as well be the reference's, so twemoji is bundled as a font fallback, see [VelvetTheme]
 */
object Icones {
    val messages = icone("ic_chat_circle_dots")
    val messagesActif = icone("ic_chat_circle_dots_fill")
    val salon = icone("ic_hash")
    val salonVocal = icone("ic_speaker_high")
    val retour = icone("ic_caret_left")

    /**
     * the back arrow in conversation headers, distinct from the [retour] chevron
     *
     * the chevron means "collapse" and "see more" all over the place, and at the top of a room it
     * is a back arrow, which is what the reference puts there. the same glyph for two different
     * meanings is what made the bar look cobbled together
     */
    val retourFleche = icone("ic_arrow_left")
    val suite = icone("ic_caret_right")
    val fermer = icone("ic_x")

    // header actions
    val recherche = icone("ic_magnifying_glass")
    val membres = icone("ic_users_three")
    val inviter = icone("ic_user_plus")
    val evenements = icone("ic_calendar_blank")
    /** browse channels, a shortcut at the top of the desktop's channel column */
    val chercherSalons = icone("ic_list_magnifying_glass")
    val notifications = icone("ic_bell")
    val notificationsActif = icone("ic_bell_fill")
    val reglages = icone("ic_gear")
    /** the gamepad, for a current activity, in the desktop's mini bar */
    val manette = icone("ic_game_controller")
    /** the headset, in the desktop's mini bar */
    val casque = icone("ic_headphones")

    // Composer
    val joindre = icone("ic_plus_circle")
    val ajouter = icone("ic_plus")
    val emoji = icone("ic_smiley")
    val clavier = icone("ic_keyboard")
    val micro = icone("ic_microphone")
    val envoyer = icone("ic_paper_plane_tilt")
    val effacer = icone("ic_backspace")

    // Messages
    val repondre = icone("ic_arrow_bend_up_left")
    val modifier = icone("ic_pencil_simple")
    val supprimer = icone("ic_trash")
    val epingle = icone("ic_push_pin")
    /** threads, in the desktop's room header */
    val fils = icone("ic_chats_circle")
    /** forward a message, in the hover bar and the desktop's overflow menu */
    val transferer = icone("ic_arrow_bend_up_right")
    /** the overflow menu of a message, at the end of the desktop's hover bar */
    val troisPoints = icone("ic_dots_three")
    /** the inbox, far right of the desktop header */
    val boiteReception = icone("ic_tray")
    /** discover servers, at the bottom of the desktop rail */
    val boussole = icone("ic_compass")

    // the desktop's profile cards: the reference draws filled icons and bold carets there
    val modifierPlein = icone("ic_pencil_simple_fill")
    val amiValide = icone("ic_user_check_fill")
    val amiAjouter = icone("ic_user_plus_fill")
    val bouclier = icone("ic_shield_fill")
    val bullePleine = icone("ic_chat_circle_fill")
    val smileyPlein = icone("ic_smiley_fill")
    val groupePlein = icone("ic_users_three_fill")
    val doubleChevron = icone("ic_caret_double_right_bold")
    val chevronBas = icone("ic_caret_down_bold")
    val chevronDroit = icone("ic_caret_right_bold")
    val chevronGauche = icone("ic_caret_left_bold")
    val plusGras = icone("ic_plus_bold")
    val croixGrasse = icone("ic_x_bold")

    // the picker's emoji categories (people reuses `emoji`, activities `manette`)
    val nature = icone("ic_leaf")
    val nourriture = icone("ic_hamburger")
    val voyages = icone("ic_car")
    val objets = icone("ic_lightbulb")
    val symboles = icone("ic_heart")
    val drapeaux = icone("ic_flag")
    val epingleActif = icone("ic_push_pin_fill")
    val mention = icone("ic_at")
    val toutLeSalon = icone("ic_megaphone")
    val lecture = icone("ic_play")
    val pause = icone("ic_pause")
    val plusDActions = icone("ic_dots_three_outline_vertical")

    // attachments and content
    val photo = icone("ic_image_square")
    val fichier = icone("ic_folder")
    val sondage = icone("ic_chart_bar")
    val applications = icone("ic_squares_four")
    val lien = icone("ic_link_simple")
    val gif = icone("ic_film_strip")
    val autocollant = icone("ic_sticker")

    // the room's chronicle, the system events
    val arrivee = icone("ic_sign_in")
    val depart = icone("ic_sign_out")
    val exclusion = icone("ic_prohibit")
    val chiffrement = icone("ic_lock_key")
    val chronique = icone("ic_info")

    // actions on a message
    val telechargement = icone("ic_download_simple")
    val copier = icone("ic_copy")
    val copierLien = icone("ic_link_simple")
    val epinglerSimple = icone("ic_push_pin_simple")
    val silence = icone("ic_bell_slash")
    val replier = icone("ic_caret_down")

    // voice, the six that were missing and used to be emoji
    //
    // `raccrocher` is not a cross. a cross closes a window, and here you are leaving a call, and
    // the gap costs: the lowest red button on a call screen has to be recognised without being
    // read. `phone-disconnect` is the handset put down
    val appeler = icone("ic_phone")
    val raccrocher = icone("ic_phone_disconnect")
    val microCoupe = icone("ic_microphone_slash")
    val camera = icone("ic_video_camera")
    val cameraCoupee = icone("ic_video_camera_slash")
    val partageEcran = icone("ic_monitor_arrow_up")
    val soundboard = icone("ic_music_notes")

    // the emoji picker's sections
    val favoris = icone("ic_star")
    val favorisActif = icone("ic_star_fill")
    val recents = icone("ic_clock_counter_clockwise")
    val valide = icone("ic_check")
}

/**
 * one icon from the set, tinted
 *
 * `tint` is mandatory with no default: the resource is white, so an untinted icon is white,
 * readable on the dark background and therefore perfectly capable of going unnoticed on a reread
 * while breaking the hierarchy. making it mandatory forces an answer to "which grey?" at every
 * site, which is the only question that matters here
 */
@Composable
fun Icone(
    ressource: ImageVector,
    tint: Color,
    taille: Dp = 20.dp,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Icon(
        imageVector = ressource,
        contentDescription = description,
        tint = tint,
        modifier = modifier.size(taille),
    )
}

/**
 * an `ImageVector` built from the path generated by `tools/icones-vers-kotlin.py`
 *
 * built once, on first access. `Icones` is an object, so its properties initialise when it loads,
 * and all sixty five vectors would be built at once by the first screen showing a single one. a
 * `lazy` per icon would cost one more class each, and the cache below does the same thing in one
 * map entry
 *
 * the fill colour is white and irrelevant: `Icon` replaces it with its `tint`, which is the only
 * channel an icon takes its colour through in this app. writing it black would give the same
 * result, the tint decides and never the path
 */
private val cache = HashMap<String, ImageVector>(80)

private fun icone(nom: String): ImageVector = cache.getOrPut(nom) {
    val chemin = CHEMINS_ICONES[nom] ?: error("icone inconnue : $nom")
    ImageVector.Builder(
        name = nom,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        // the phosphor svg viewport, checked across all sixty five by the generator, which refuses
        // to produce anything if one of them falls outside it
        viewportWidth = 256f,
        viewportHeight = 256f,
    ).addPath(addPathNodes(chemin), fill = SolidColor(Color.White)).build()
}

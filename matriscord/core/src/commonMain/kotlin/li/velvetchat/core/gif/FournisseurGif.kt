package li.velvetchat.core.gif

/**
 * a gif provider, and the trade off it carries
 *
 * searching for a gif sends what you type to a third party. it is the only thing in this app
 * that goes beyond its first users without the protocol forcing it, which is why this is an
 * interface rather than a direct call: choosing the provider, including choosing none, belongs
 * to the person typing
 *
 * the trade off has three layers, not one.
 *
 * first, what you type. "birthday" goes to the third party, and if the search is live it is not
 * one request but five, b, bi, bir. hence [DELAI_FRAPPE_MS], which is not a ui comfort but a
 * reduction in what gets disclosed
 *
 * second, what you look at. thumbnails load from their host, so browsing without sending
 * anything still tells that host what you looked at
 *
 * third, and this is the one that lasts, what you send. the message carries the third party's
 * url, so every recipient who displays that gif tells the host they opened it. a one off
 * disclosure by the sender becomes a repeated disclosure by everyone else
 *
 * the call: we send the link, we do not rehost. the other road, downloading the gif and
 * republishing it as an `mxc://`, would give a message encrypted like the rest that outlives its
 * provider, which is worth something. every tenor gif ever sent has been a broken image since
 * june 2026. it was still rejected, because it turns "sharing a link" into "republishing a file"
 * without anyone asking, and fills the server's media store with copies of public images
 *
 * what makes layer three bearable: displaying is never automatic for an arbitrary host. see
 * `LienGif`, where only a closed list of hosts loads, so the sender does not get to choose who
 * learns you read it, and `PrefsGif.gifsEnLigne`, which lets you load nothing at all
 *
 * what this interface deliberately does not expose: any identifier for a person. klipy offers a
 * "recents" endpoint that wants a `customer_id`, described as a unique identifier for the user
 * in your system. that is the heaviest decision in the whole feature, because it turns "an ip
 * made some requests" into "this person made these requests for two years". it is optional, so
 * it is not taken. recents are kept locally from what was actually sent, which is better anyway:
 * the list survives changing provider
 */
interface FournisseurGif {

    /** what the screen shows to name this provider */
    val nom: String

    /**
     * trending, with no keyword
     *
     * the least revealing request available: it says nothing beyond "this address opened the
     * gif tab". that is what makes the tab usable without typing, at the lowest price, and why
     * it is separate from [chercher] rather than being a search with an empty keyword
     */
    suspend fun tendances(page: Int = 1): List<Gif>

    /** a keyword search. read [DELAI_FRAPPE_MS] before calling it on every keystroke */
    suspend fun chercher(requete: String, page: Int = 1): List<Gif>

    companion object {
        /**
         * how long to wait for typing to stop before it becomes a request
         *
         * 450 ms: above the rhythm of continuous typing, below where the wait gets noticed.
         * "pizza" becomes one request instead of five, so one data point at the third party
         * instead of five
         */
        const val DELAI_FRAPPE_MS = 450L

        /** gifs per page. enough to fill the screen, not enough to make it crawl */
        const val PAR_PAGE = 24
    }
}

/**
 * a gif, cut down to what the screen and the send need
 *
 * @param apercu the thumbnail address, small, for the grid
 * @param complet the file address, and this is what goes into the message. the message body is
 *   that address and nothing else, which is exactly what `LienGif.dans` recognises at the other
 *   end
 * @param largeur, hauteur in pixels when the provider gives them. used to reserve space in the
 *   grid before the load, without which the grid jumps every time an image arrives and you tap
 *   whatever just moved
 * @param description alt text. rarely filled in, but the only thing that makes a gif accessible
 *   to someone who cannot see it
 */
data class Gif(
    val id: String,
    val apercu: String,
    val complet: String,
    val largeur: Int? = null,
    val hauteur: Int? = null,
    val description: String? = null,
)

/**
 * the chosen provider, as it sits in the settings
 *
 * [AUCUN] is the default, and not out of timidity: a picker that queried a third party the first
 * time it opened would have made the decision on the person's behalf. a choice is only real if
 * the "no" position is real too
 */
enum class ChoixFournisseur {
    /** nothing gets queried. the tab offers the device gallery, and says so */
    AUCUN,

    /** klipy. self service, free, paid for by ads mixed into the results */
    KLIPY,
}

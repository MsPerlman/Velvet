package li.velvetchat.core.reseau

import li.velvetchat.core.tr

import java.net.HttpURLConnection
import java.net.URI

/**
 * following a redirect without carrying the token out of the house
 *
 * ## what this guarantees, and what it does not fix
 *
 * **there was no leak.** that was the starting assumption — `HttpURLConnection` follows
 * redirects on its own, so it must be resending the `Authorization` header to the host it
 * lands on — and it is **wrong**: measured with two local servers, the third party is indeed
 * contacted and receives nothing. the jvm strips the header on a cross host redirect
 *
 * the file stays anyway, for two honest reasons:
 *
 *   · **the guarantee becomes explicit and tested.** it rested on an implementation detail of
 *     the jvm that nothing in the code named and nothing checked. another http client — okhttp,
 *     `java.net.http` — does not necessarily behave the same, and the leak would then be
 *     silent. `SuiviRedirectionsTest` stands up two servers and looks at what the third party
 *     received
 *   · **the token's right is judged again at every hop** rather than once on the first address.
 *     that is the rule we want, written as the rule
 *
 * hops are bounded by [SAUTS_MAX]. `HttpURLConnection` already has its own limit, so this is
 * one belt more, not a discovery
 *
 * ## what actually protects the update
 *
 * the sha-256, published in the matrix state event and checked against the bytes received
 * before anything is installed. a redirect therefore cannot substitute the apk, wherever it
 * leads. a token that does not follow is a precaution; a hash that does not match is a refusal
 */
object SuiviRedirections {

    const val SAUTS_MAX = 5

    private val REDIRECTIONS = setOf(301, 302, 303, 307, 308)

    /**
     * @param autorisationPour gives the `Authorization` header **that address** deserves, or
     *   null. asked again at every hop: that is the whole point
     * @param preparer applied to each connection, for the timeouts
     * @return a connection open on the final resource, the caller closes it
     */
    fun ouvrir(
        url: String,
        autorisationPour: (String) -> String?,
        preparer: (HttpURLConnection) -> Unit = {},
    ): HttpURLConnection {
        var adresse = url
        var saut = 0
        while (true) {
            val cnx = (URI.create(adresse).toURL().openConnection() as HttpURLConnection).apply {
                // **the point of the file**: the following is not delegated, because
                // delegating it means resending the header without rechecking to whom
                instanceFollowRedirects = false
                autorisationPour(adresse)?.let { setRequestProperty("Authorization", it) }
                preparer(this)
            }
            if (cnx.responseCode !in REDIRECTIONS) return cnx

            val suivante = cnx.getHeaderField("Location")
            cnx.disconnect()
            if (suivante.isNullOrBlank() || ++saut > SAUTS_MAX) {
                throw java.io.IOException(tr("Trop de redirections, ou redirection sans adresse", "Too many redirects, or a redirect without an address"))
            }
            // relative or absolute: `resolve` handles both, and a relative address
            // necessarily stays on the same host, so on the same rights
            adresse = runCatching { URI.create(adresse).resolve(suivante).toString() }
                .getOrElse { throw java.io.IOException(tr("Redirection illisible : $suivante", "Unreadable redirect: $suivante")) }
        }
    }
}

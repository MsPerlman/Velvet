package li.velvetchat.core.gif

/**
 * when a message is nothing but a gif link, and we agree to load it
 *
 * a gif goes out as a link, without being rehosted. the immediate and not very pretty
 * consequence: the message is then text, and draws as a blue underlined link. the feature works
 * and cannot be seen
 *
 * to display it, an image has to be loaded from a third party host at read time. which is
 * exactly what `extraireEmojis` refuses to do, for a reason that has not changed: loading a url
 * because a message asked you to tells whoever hosts it that you read the message. an `<img>`
 * placed by the sender is a tracker, and the sender picks the host
 *
 * this file is what makes the two compatible: the sender no longer picks the host
 *
 * a link only loads when all of these hold.
 *
 * one, the message is nothing else. not a sentence with a link in it, the link alone. a message
 * that says something and carries an address is a message you read, not an image you look at,
 * and accepting links buried in text would reopen the door to a tracker slipped under a
 * harmless sentence
 *
 * two, `https://` and nothing else. in the clear, the request tells everything along the path
 * which gif you are looking at and when. we agree to tell the host, not the network
 *
 * three, the host is in [HOTES]. this is the heart of it: the sender cannot name their own
 * machine. the worst they can do is make you load an image from a gif provider, which learns an
 * ip and a moment, which is what whoever opened the picker already told it
 *
 * four, no `@` in the authority. `https://klipy.com@example.invalid/x.gif` reads like a klipy
 * link and connects to `example.invalid`. that is the classic spoof, and a host list that does
 * not test for it protects nothing
 *
 * five, the path ends in an image extension. belt and braces: even at an allowed host we only
 * fetch things that decode as an image
 *
 * what the rule cannot do: guess hosts. if klipy serves its files from a cdn whose name is not
 * derived from its own, the link will not be recognised and the message falls back on its text
 * display. degraded, never broken, like everywhere else. adding the host is then one line in
 * [HOTES]
 */
object LienGif {

    /**
     * the hosts we agree to load an image from
     *
     * one entry covers its subdomains: `klipy.com` allows `media.klipy.com` and `cdn.klipy.com`
     * but not `klipy.com.example.invalid`, see [hoteAutorise], where the dot is what decides
     *
     * the provider the app knows how to query, plus three historical hosts. nobody queries those
     * from the app, but a tenor or giphy link pasted by hand is common enough and there is no
     * reason to show it worse than any other
     */
    val HOTES: Set<String> = setOf(
        "klipy.com",
        "klipy.co",
        "tenor.com",
        "giphy.com",
        "gfycat.com",
    )

    /**
     * the extensions we agree to go and fetch
     *
     * `mp4` is in there because most providers have served their "gifs" as video for years. a
     * real gif weighs ten times as much. the android decoder reads both
     */
    private val EXTENSIONS = setOf("gif", "webp", "mp4", "gifv", "apng", "png", "jpg", "jpeg")

    /**
     * the link to load, or null when this message is not one
     *
     * null is the normal case and not an error: the overwhelming majority of messages are text,
     * and the caller simply falls back on ordinary display
     */
    fun dans(corps: String): String? {
        val lien = corps.trim()
        if (lien.isEmpty()) return null

        // the message has to be ONLY the link. a space, a newline or a control character is
        // enough to say this message contains something else
        if (lien.any { it.isWhitespace() || it.code < 0x20 }) return null

        val schema = "https://"
        if (!lien.regionMatches(0, schema, 0, schema.length, ignoreCase = true)) return null

        val apres = lien.substring(schema.length)
        val finAutorite = apres.indexOfFirst { it == '/' || it == '?' || it == '#' }
        if (finAutorite <= 0) return null
        val autorite = apres.substring(0, finAutorite)

        // an authority containing `@` carries userinfo, and that is the spoof: what looks like
        // the host is not one. refuse rather than try to untangle it
        if (autorite.contains('@')) return null

        val hote = autorite.substringBefore(':').lowercase()
        if (!hoteAutorise(hote)) return null

        if (!ressembleAUnFichier(lien)) return null

        return lien
    }

    /**
     * does the address name a file rather than a page
     *
     * separate from [dans] because this test is also used when sending, not only when
     * displaying, see `Klipy.versGif`. the distinction is not theoretical: a
     * `klipy.com/gifs/<slug>` page is a site behind cloudflare. it returns "just a moment" to
     * anything that is not a browser, so no matrix client and no preview server can get an image
     * out of it. sending an address like that as if it were a gif produces a message that will
     * never display, for anyone
     */
    fun ressembleAUnFichier(url: String): Boolean {
        val chemin = url.substringBefore('?').substringBefore('#')
        // the filename only. a dot in the domain is not an extension, and
        // `https://klipy.com/gifs/blair` would otherwise be credited with one
        val nom = chemin.substringAfterLast('/', "")
        val extension = nom.substringAfterLast('.', "").lowercase()
        return extension in EXTENSIONS
    }

    /**
     * is the host allowed, itself or one of its subdomains
     *
     * the dot is what stops the suffix attack. testing `hote.endsWith(entry)` on its own would
     * accept `klipy.com.example.invalid`, which belongs to klipy not at all. so it has to be
     * either equality or an ending of `.` plus the entry
     */
    internal fun hoteAutorise(hote: String): Boolean =
        HOTES.any { autorise -> hote == autorise || hote.endsWith(".$autorise") }
}

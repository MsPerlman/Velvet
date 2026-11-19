package li.velvetchat.desk

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import li.velvetchat.core.model.Activite

/**
 * turns discord's activity into ours, mostly by guessing the name
 *
 * ## the name is not in the payload. at all
 *
 * discord looks it up from the handshake's `client_id` through its own api, which we are
 * obviously not going to call. so we ask the operating system instead: every `SET_ACTIVITY`
 * carries a `pid`, and windows knows what executable is behind a pid
 *
 * works beautifully for games. `Celeste.exe` is, astonishingly, Celeste
 *
 * ## when the process means nothing, and how we know
 *
 * the caller passes null for [processus] when the sender's pid describes nothing — a websocket
 * client is a web page, and the process behind it is a browser. resolving it would put "chrome"
 * on somebody's profile, which is true and completely useless. see `RichPresence.Transport`
 *
 * that is the case for **PreMiD**, which is what puts youtube and netflix on a profile. it used
 * to ship a desktop app, whose process really was called PreMiD whatever you were watching, and
 * the branch below exists for it. since 2026 there is no app at all, only a browser extension,
 * so that branch is kept for older installs and does nothing for a current one
 *
 * with no process to ask about, the name falls to `name` and then to the large asset's text,
 * which is where PreMiD puts the service. that ordering was a lucky accident before it was a
 * decision, and it is pinned by tests now that it is one
 *
 * none of this has been **exercised against a running PreMiD**: the extension is closed source
 * (its own repository says so), so it could not be read either. the deduction that it must use
 * the websocket is solid — browsers have no other local transport — but what it actually sends
 * is still unverified
 *
 * this lives apart from [RichPresence] for one reason: that object cannot be touched without
 * loading kernel32, and a rule this fiddly deserves tests that do not need a named pipe, a
 * process id, or windows
 *
 * @param processus the executable behind the pid, without path or extension. null when asking
 *   would be meaningless (anything off a websocket) or impossible (a process that died between
 *   sending and our looking)
 */
internal fun activiteDepuisDiscord(a: JsonObject, processus: String?): Activite? {
    fun texte(cle: String) = (a[cle] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    val assets = a["assets"] as? JsonObject
    fun asset(cle: String) =
        (assets?.get(cle) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    val viaPremid = processus != null && processus.contains("premid", ignoreCase = true)
    val nom = when {
        viaPremid -> asset("large_text") ?: texte("name") ?: processus
        else -> texte("name") ?: processus ?: asset("large_text")
    } ?: return null

    val horodatages = a["timestamps"] as? JsonObject
    val debut = (horodatages?.get("start") as? JsonPrimitive)?.longOrNull

    return Activite(
        nom = nom,
        details = texte("details"),
        etat = texte("state"),
        // half the clients send seconds, half send milliseconds, nobody says which. if the
        // number would put you in 1970-something when read as milliseconds, it is seconds. this
        // heuristic will break in approximately the year 33658, at which point it is someone
        // else's problem
        depuis = debut?.takeIf { it > 0 }?.let { if (it < 1_000_000_000_000L) it * 1000 else it },
        grandeImage = asset("large_image"),
        petiteImage = asset("small_image"),
        applicationId = texte("application_id"),
    )
}

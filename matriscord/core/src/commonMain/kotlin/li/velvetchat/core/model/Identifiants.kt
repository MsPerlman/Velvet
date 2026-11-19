package li.velvetchat.core.model

/**
 * the reverse dns prefix our own matrix data is written under, and the ones it used before
 *
 * ## why a prefix at all
 *
 * matrix reserves `m.` for the specification and requires everything else to be namespaced with a
 * domain you control, written backwards like a java package. a bio kept as a custom profile field
 * therefore cannot be called `bio`: two clients would both claim the name and overwrite each
 * other. so the name of the key carries the project's domain, and the project's domain has
 * changed twice
 *
 * ## why the old ones are still listed
 *
 * a key is not a variable name. it is the name of a drawer on somebody's homeserver, and there is
 * already something in it. rename the constant alone and the app opens a drawer that was never
 * filled: bios, decorations, notes, nicknames, the server order and the soundboard all come back
 * empty, while the data sits untouched under the old name. nothing is lost and everything looks
 * lost, which is the worst of both
 *
 * so a read tries the current name first, then each earlier one, newest first. a write only ever
 * uses the current one, so a value migrates by itself the next time it is touched
 *
 * ## when a prefix can leave this list
 *
 * when nothing on the server answers to it any more. that is not a date, it is a check: read the
 * old keys on the homeserver and see that they are gone. until then removing one silently drops
 * whatever still lives there
 */
object Identifiants {

    /** what everything written from now on is called */
    const val PREFIXE = "li.velvetchat."

    /**
     * the earlier prefixes, newest first — **empty, and that is a decision**
     *
     * two used to sit here, and they worked: a read tried the new name then the old ones, so
     * nothing already on the server went missing. they were emptied on 2026-09-30 on the
     * owner's call, the data under them being test bios and test decorations nobody minds
     * losing, and their presence being the last place the personal domain appeared in a
     * shipped binary
     *
     * **that reading of what sat under them was wrong, and it cost the cosmetics catalogue.**
     * found on 2026-10-06: the global catalogue room held its fourteen state events under the
     * old prefix, 1344 ornaments between decorations, effects, nameplates and frames, so every
     * picker in the app went empty while the data sat untouched on the homeserver. nothing was
     * logged, since reading a type nobody published is indistinguishable from an empty room
     *
     * it was fixed where the data was, not here: the fourteen events were **copied** under the
     * current prefix, so the catalogue reads again and the personal domain stays out of the
     * binary. the old ones were left in place, apks from before the rename still reading them.
     * `cataloguePagine` now says out loud when a room holds a catalogue under a name it does
     * not read, which is the log line that was missing
     *
     * so what was written under the old names is **not** read any more. it is not deleted
     * either: it sits on the homeserver under keys nothing asks for, which is what makes the
     * decision reversible — putting a prefix back here brings it all back
     *
     * the machinery around it stays for the next rename. an empty list costs one loop
     * iteration and turns [anciennes] into "no other name", which is exactly right
     */
    val ANCIENS: List<String> = emptyList()

    /**
     * the same key under every name it has had, newest first
     *
     * a key that does not carry our prefix is not ours to translate — an `m.` type, or another
     * client's — and gets an empty list rather than a mangled guess
     */
    fun anciennes(cle: String): List<String> =
        if (!cle.startsWith(PREFIXE)) {
            emptyList()
        } else {
            val suffixe = cle.removePrefix(PREFIXE)
            ANCIENS.map { it + suffixe }
        }

    /** the current name and then the earlier ones, in the order a read should try them */
    fun toutesLesFormes(cle: String): List<String> = listOf(cle) + anciennes(cle)
}

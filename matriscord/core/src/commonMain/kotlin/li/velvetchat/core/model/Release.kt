package li.velvetchat.core.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * a published version of velvet
 *
 * velvet goes through no store, so nobody pushes updates on our behalf. this describes what an
 * admin publishes so an installed app can work out it's behind
 *
 * the manifest lives in matrix, the bytes come over https, and there are two reasons for that
 * split, neither of them convenience:
 *
 * 1. who may publish is already settled. a state event is governed by the room's power levels,
 *    so nobody has to invent an authorisation scheme: whoever can't rename the server can't
 *    publish a version either
 * 2. integrity is anchored here, by [VarianteRelease.sha256]. the apk itself is served over
 *    plain https, since it weighs 75 mb and no homeserver will take that as an upload, and
 *    those bytes are not trustworthy on their own. they become trustworthy when their digest
 *    matches the one published in matrix. swapping the apk on the web server isn't enough,
 *    you'd have to be able to write the state too
 *
 * this model installs nothing and decides nothing. android forbids silent installs outside
 * device owner mode, and that's the right default anyway: an app that replaces itself on
 * somebody's phone would be exactly what we hold against the stores
 */
data class Release(
    /**
     * the number that decides, and the only one
     *
     * [versionName] is for humans, and two versions can carry the same name or names that
     * compare badly ("0.9" against "0.10"). the code is a rising integer, compared as one
     */
    val versionCode: Long,
    val versionName: String,
    /** what changed, as we want it read. null when nothing was written */
    val notes: String?,
    /** by abi: `arm64-v8a`, `x86_64`. see [pourAbis] */
    val variantes: Map<String, VarianteRelease>,
) {

    /**
     * the variant to install on this device
     *
     * @param abis the supported abis in the device's own order of preference, which is what
     *   `Build.SUPPORTED_ABIS` gives you. we follow that order rather than ours: an arm64 device
     *   can run armeabi-v7a but would much rather not, and it's the one that knows why
     */
    fun pourAbis(abis: List<String>): VarianteRelease? =
        abis.firstNotNullOfOrNull { variantes[it] }

    /** is this newer than what's running */
    fun plusRecenteQue(codeInstalle: Long): Boolean = versionCode > codeInstalle

    companion object {
        /**
         * the state event type
         *
         * prefixed with the project's own domain and not `m.`, because it's a house
         * convention no other client will know how to read. borrowing the protocol's prefix
         * would suggest an interoperability that doesn't exist. matrix asks for exactly this:
         * a reverse dns namespace you control. see [Identifiants] for the earlier names and
         * why a read still tries them
         */
        const val TYPE = "li.velvetchat.release"

        /**
         * velvetdesk's version, announced separately: the msi installer's `packageVersion`
         * ("1.0.0") doesn't track the phone's, and comparing one to the other would offer
         * people nonsense. same content, same ordering, variant `windows-x64`
         */
        const val TYPE_BUREAU = "li.velvetchat.release.bureau"

        /**
         * "1.2.3" becomes 1002003, a version number you can compare as an integer. null when it
         * isn't three numbers each under 1000, which is what msi demands anyway
         */
        fun codeDeVersion(version: String): Long? {
            val parties = version.trim().split('.').map { it.toLongOrNull() ?: return null }
            if (parties.size != 3 || parties.any { it !in 0..999 }) return null
            return parties[0] * 1_000_000 + parties[1] * 1_000 + parties[2]
        }

        /**
         * reads a state event, null if there's nothing publishable in it
         *
         * anything doubtful is dropped rather than patched up. a variant with no digest, or
         * served in the clear, leaves the set: offering an update we can't verify is worth less
         * than offering none. and if no variant survives the sort, the whole version is ignored
         */
        fun depuisJson(racine: JsonObject?): Release? {
            if (racine == null) return null
            val code = (racine["versionCode"] as? JsonPrimitive)?.longOrNull ?: return null
            val nom = (racine["versionName"] as? JsonPrimitive)?.contentOrNull ?: return null

            val brutes = racine["variantes"] as? JsonObject ?: return null
            val variantes = brutes.mapNotNull { (abi, valeur) ->
                val objet = valeur as? JsonObject ?: return@mapNotNull null
                val url = texte(objet, "url") ?: return@mapNotNull null
                val empreinte = texte(objet, "sha256")?.lowercase() ?: return@mapNotNull null
                if (!url.startsWith("https://")) return@mapNotNull null
                if (!EMPREINTE_VALIDE.matches(empreinte)) return@mapNotNull null
                abi to VarianteRelease(
                    url = url,
                    sha256 = empreinte,
                    taille = (objet["taille"] as? JsonPrimitive)?.longOrNull ?: 0L,
                )
            }.toMap()

            if (variantes.isEmpty()) return null
            return Release(
                versionCode = code,
                versionName = nom,
                notes = texte(racine, "notes")?.takeIf { it.isNotBlank() },
                variantes = variantes,
            )
        }

        private fun texte(objet: JsonObject, cle: String): String? =
            (objet[cle] as? JsonPrimitive)?.contentOrNull

        /** sixty four hex characters and nothing else */
        private val EMPREINTE_VALIDE = Regex("^[0-9a-f]{64}$")
    }
}

/**
 * one published apk, for one abi
 *
 * @param sha256 the expected digest, lowercase. this is what authorises the install, not where
 *   the download came from: the bytes arrive from an ordinary web server, and it's this hash,
 *   published in matrix and therefore behind the power levels, that says they're the right ones
 * @param taille in bytes, so the progress bar can be honest. 0 when it wasn't published, and
 *   then we show a download with no percentage rather than a made up one
 */
data class VarianteRelease(
    val url: String,
    val sha256: String,
    val taille: Long,
) {
    /**
     * the `Authorization` header to send, or null
     *
     * the files live in the homeserver's matrix media, which wants the account token. that
     * token goes only to its own homeserver and never to some other address a manifest happened
     * to name
     */
    fun autorisation(homeserverUrl: String, jeton: String): String? =
        if (surLeHomeserver(url, homeserverUrl)) "Bearer $jeton" else null

    companion object {
        /**
         * does that address deserve the token
         *
         * pulled out of [autorisation] so it can be **asked again at every redirect**. the
         * question used to be asked once, on the first address; the jvm covered the rest on
         * its own, which worked without anyone having written it down. see `SuiviRedirections`
         */
        fun surLeHomeserver(adresse: String, homeserverUrl: String): Boolean =
            adresse.startsWith(homeserverUrl.trimEnd('/') + "/_matrix/")
    }
}

package li.velvetchat.app.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import li.velvetchat.core.model.Release
import li.velvetchat.core.model.VarianteRelease
import java.io.File
import java.security.MessageDigest

/**
 * updating velvet, with no store
 *
 * nobody pushes the updates for us. without this mechanism, updating ten phones would mean sending
 * a file by hand to ten people, and finding out six months later that half of them stayed on an old
 * version
 *
 * ## what makes the install acceptable
 *
 * three barriers, in this order, and none is decorative:
 *
 * 1. the manifest lives in matrix, under the server's power levels. anybody who cannot rename the
 * server cannot publish a version 2. the hash is checked before anything else. the bytes come from
 * an ordinary web server, and they are only worth anything because their sha-256 matches the one
 * published in matrix. an apk swapped on the web server does not install 3. android asks for
 * confirmation, and a silent install is impossible outside device owner. that is the right default:
 * an app that replaces itself on somebody's phone is exactly what stores get blamed for
 */
object MiseAJour {

    /**
     * downloads, checks, and hands the apk to the system
     *
     * the check happens during the download, not after: rereading 75 mb a second time to hash them
     * would double the wait on a phone, and reading as it streams costs nothing
     *
     * @return `null` when all went well, the system taking over and showing its confirmation.
     *   otherwise the reason, as it can be shown
     */
    suspend fun installer(
        contexte: Context,
        variante: VarianteRelease,
        /**
         * what it takes to judge the token's right **at every hop**, and not once
         *
         * we take the homeserver's address and the token rather than a header already built:
         * a ready made header does not know who it is for, and that is exactly what made it
         * follow a redirect out of the house
         */
        homeserverUrl: String,
        jeton: String,
        /** the download is finished, the system takes over, see [EtatMiseAJour.Installation] */
        onRemise: () -> Unit = {},
        onProgres: (Float) -> Unit,
    ): String? = withContext(Dispatchers.IO) {
        val fichier = File(contexte.cacheDir, "maj.apk")
        try {
            val empreinte = telecharger(variante, homeserverUrl, jeton, fichier, onProgres)
                ?: return@withContext horsUi.telechargementInterrompu

            // the refusal that counts. an apk whose hash differs is not "possibly corrupted", it is
            // a file nobody published. we do not offer it, and we do not keep it
            if (empreinte != variante.sha256) {
                fichier.delete()
                return@withContext horsUi.empreinteNonConforme
            }

            onRemise()
            remettreAuSysteme(contexte, fichier)
            null
        } catch (t: Throwable) {
            fichier.delete()
            t.message ?: horsUi.miseAJourImpossible
        }
    }

    /** @return the lowercase sha-256 hash, or `null` when the response was not usable */
    private fun telecharger(
        variante: VarianteRelease,
        homeserverUrl: String,
        jeton: String,
        vers: File,
        onProgres: (Float) -> Unit,
    ): String? {
        // redirects followed by hand, the token's right judged again at every hop. the jvm
        // already strips the header on a cross host redirect — verified — but it is alone in
        // knowing that: `SuiviRedirections` makes the rule explicit and tests it
        val cnx = li.velvetchat.core.reseau.SuiviRedirections.ouvrir(
            url = variante.url,
            autorisationPour = { adresse ->
                if (VarianteRelease.surLeHomeserver(adresse, homeserverUrl)) "Bearer $jeton" else null
            },
            preparer = {
                it.connectTimeout = 15_000
                it.readTimeout = 60_000
            },
        )
        try {
            if (cnx.responseCode != 200) return null
            val total = variante.taille.takeIf { it > 0 } ?: cnx.contentLengthLong
            val digest = MessageDigest.getInstance("SHA-256")
            var recus = 0L

            cnx.inputStream.use { entree ->
                vers.outputStream().use { sortie ->
                    val tampon = ByteArray(64 * 1024)
                    while (true) {
                        val lus = entree.read(tampon)
                        if (lus < 0) break
                        sortie.write(tampon, 0, lus)
                        digest.update(tampon, 0, lus)
                        recus += lus
                        // with no announced size we do not pretend to know the progress: a bar
                        // inventing its own progress is worse than no bar
                        if (total > 0) onProgres((recus.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        } finally {
            cnx.disconnect()
        }
    }

    /**
     * opens an install session and pours the apk into it
     *
     * `PackageInstaller` rather than a view `Intent` on a `content://`: it is the intended api, it
     * does not require a `FileProvider`, and it says what happened instead of leaving you to guess.
     * the `commit` triggers the system's confirmation, and nothing is installed before somebody has
     * tapped "install"
     */
    private fun remettreAuSysteme(contexte: Context, apk: File) {
        // the receiver has to exist before the commit, and that is what was missing
        //
        // `commit` shows nothing by itself. the system answers with a broadcast carrying
        // `STATUS_PENDING_USER_ACTION` and, inside it, the confirmation intent to launch. with no
        // receiver to catch it, the session is indeed created and the apk indeed written, and the
        // dialog never appears. nothing fails: simply nothing happens, which is the hardest kind of
        // breakage to read
        contexte.registerReceiver(
            object : android.content.BroadcastReceiver() {
                override fun onReceive(recu: Context, intention: Intent) {
                    val statut = intention.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
                    if (statut == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                        @Suppress("DEPRECATION")
                        val confirmation =
                            intention.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                        confirmation?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        confirmation?.let { recu.startActivity(it) }
                        // we do not unsubscribe here: the system will send a second status (success
                        // or failure) once the person has decided
                        return
                    }
                    runCatching { recu.unregisterReceiver(this) }
                }
            },
            android.content.IntentFilter(ACTION_RESULTAT),
            // required since android 13 for a broadcast that has no reason to come from anywhere
            // but the system
            Context.RECEIVER_NOT_EXPORTED,
        )

        val installateur = contexte.packageManager.packageInstaller
        val parametres = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        )
        val idSession = installateur.createSession(parametres)
        installateur.openSession(idSession).use { session ->
            session.openWrite("velvet", 0, apk.length()).use { sortie ->
                apk.inputStream().use { it.copyTo(sortie) }
                session.fsync(sortie)
            }
            val intention = android.app.PendingIntent.getBroadcast(
                contexte,
                idSession,
                Intent(ACTION_RESULTAT).setPackage(contexte.packageName),
                android.app.PendingIntent.FLAG_MUTABLE or
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT,
            )
            session.commit(intention.intentSender)
        }
    }

    /**
     * does the device allow velvet to install?
     *
     * since android 8, "unknown sources" is per app and not global. without this permission,
     * `commit` fails silently from the user's point of view: the confirmation simply never appears.
     * so it is better to ask first
     */
    fun autorisee(contexte: Context): Boolean =
        contexte.packageManager.canRequestPackageInstalls()

    /** the settings screen where that permission is granted */
    fun intentionAutorisation(contexte: Context): Intent =
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(android.net.Uri.parse("package:${contexte.packageName}"))

    private const val ACTION_RESULTAT = "li.velvetchat.INSTALL_RESULT"
}

/**
 * the banner announcing a version, and what it refuses to be
 *
 * neither modal nor repeated. an update is not an emergency: cutting off the reading of a
 * conversation to announce it would be a show of force, not information. the banner sits above the
 * list, closes, and does not come back for the same version
 */
@Composable
fun BandeauMiseAJour(
    release: Release?,
    etat: EtatMiseAJour,
    onInstaller: () -> Unit,
    onPlusTard: () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    AnimatedVisibility(visible = release != null) {
        val version = release ?: return@AnimatedVisibility
        Row(
            Modifier.fillMaxWidth()
                .background(tokens.accent.copy(alpha = 0.16f))
                // the banner is the very first element of the screen: without that, its text goes
                // under the clock and the battery. seen on screen, not foreseen
                .statusBarsPadding()
                .padding(horizontal = tokens.espL, vertical = tokens.espM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icone(Icones.telechargement, tint = tokens.accent, taille = 18.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    t.versionDisponible(version.versionName),
                    color = tokens.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val detail = when (etat) {
                    is EtatMiseAJour.Repos -> version.notes
                    is EtatMiseAJour.Telechargement ->
                        if (etat.progres > 0f) t.telechargementPourcent((etat.progres * 100).toInt())
                        else t.telechargementEnCours

                    EtatMiseAJour.Installation -> t.installationEnCours
                    is EtatMiseAJour.Echec -> etat.raison
                }
                detail?.let {
                    Text(
                        it,
                        color = if (etat is EtatMiseAJour.Echec) tokens.danger
                        else tokens.textMuted,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (etat !is EtatMiseAJour.Telechargement && etat !is EtatMiseAJour.Installation) {
                Spacer(Modifier.width(12.dp))
                Text(
                    t.plusTard,
                    color = tokens.textMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable(onClick = onPlusTard).padding(6.dp),
                )
                Text(
                    if (etat is EtatMiseAJour.Echec) t.reessayer else t.installer,
                    color = tokens.accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable(onClick = onInstaller).padding(6.dp),
                )
            }
        }
    }
}

/** where the update has got to. three states, because the failure has to show */
sealed interface EtatMiseAJour {
    data object Repos : EtatMiseAJour
    data class Telechargement(val progres: Float) : EtatMiseAJour

    /**
     * between the finished download and the system's dialog
     *
     * what happens there: ninety megabytes copied into an install session, then a `commit` the
     * system answers to, when it feels like it, with the confirmation intent. a few seconds during
     * which the app had nothing left to say: the bar disappeared and nothing happened. reported as
     * a pointless gap between tapping install and the phone offering anything
     *
     * with no percentage, because there is none: `PackageInstaller` returns no progress, and
     * inventing one would be lying about what we know
     */
    data object Installation : EtatMiseAJour
    data class Echec(val raison: String) : EtatMiseAJour
}

/**
 * the refused version, kept on the device
 *
 * "later" has to hold: showing the banner again at the next opening would make closing it a
 * pointless gesture, and the banner a nuisance. a newer version will bring it back, which is the
 * only case where insisting is justified
 */
object PrefsMaj {
    private const val FICHIER = "maj"
    private const val CLE_REFUSEE = "version_refusee"

    fun refusee(contexte: Context): Long =
        contexte.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)
            .getLong(CLE_REFUSEE, -1L)

    fun refuser(contexte: Context, versionCode: Long) {
        contexte.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)
            .edit().putLong(CLE_REFUSEE, versionCode).apply()
    }
}

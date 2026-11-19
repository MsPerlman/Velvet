package li.velvetchat.desk

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.model.Release
import li.velvetchat.app.ui.*
import java.io.File
import java.net.URI
import java.security.MessageDigest

/**
 * velvetdesk updates itself, the counterpart of the phone's `MiseAJour.kt`, with the same trust
 * rule: the announcement lives in matrix ([Release.TYPE_BUREAU], written by whoever has power on
 * the server), the installer arrives over ordinary https and is worth nothing unless its sha-256
 * matches the announcement's
 *
 * what changes from the phone: windows allows the silent install of a per user msi with no admin
 * prompt. so, at startup the announcement is read and the installer downloaded in the background,
 * verified as it goes, with a file that does not match deleted. a banner offers to restart and
 * update. with no click, the update installs on velvetdesk's exit, and serves at the next launch
 *
 * nothing happens in development (`gradlew run`): only an installed app knows its version
 * (`jpackage.app-version`), and only it has an installer to replace
 */
object MiseAJourBureau {
    /** the version downloaded and verified, ready to install, null otherwise */
    var prete by mutableStateOf<Release?>(null)
        private set

    private var installeur: File? = null

    /** the installed version, as a comparable code, null outside an installed app */
    private val codeInstalle: Long? =
        System.getProperty("jpackage.app-version")?.let(Release::codeDeVersion)

    suspend fun verifier(moteur: MatrixEngine) = withContext(Dispatchers.IO) {
        val installee = codeInstalle ?: return@withContext
        // the announcement can live on any server you are a member of: the most recent wins
        val ids = listOfNotNull(runCatching { moteur.salonInstallation() }.getOrNull()) + moteur.servers().map { it.id }
        val annonce = ids.mapNotNull { id ->
            runCatching { moteur.releasePubliee(id, Release.TYPE_BUREAU) }.getOrNull()
        }.maxByOrNull { it.versionCode }?.takeIf { it.plusRecenteQue(installee) } ?: return@withContext
        val variante = annonce.variantes[VARIANTE] ?: return@withContext

        val dossier = File(Donnees.cache, "mise-a-jour").apply { mkdirs() }
        val fichier = File(dossier, "VelvetDesk-${annonce.versionName}.msi")
        val provisoire = File(dossier, fichier.name + ".partiel")
        if (!fichier.exists() || empreinte(fichier) != variante.sha256) {
            fichier.delete()
            val sha = MessageDigest.getInstance("SHA-256")
            runCatching {
                val session = moteur.currentSession()
                // redirects followed by hand, the token's right judged again at every hop.
                // the jvm already strips the header across hosts — verified — but nothing
                // here said so nor checked it. see `SuiviRedirections`
                val cnx = li.velvetchat.core.reseau.SuiviRedirections.ouvrir(
                    url = variante.url,
                    autorisationPour = { adresse ->
                        if (li.velvetchat.core.model.VarianteRelease
                                .surLeHomeserver(adresse, session.homeserverUrl)
                        ) {
                            "Bearer ${session.accessToken}"
                        } else {
                            null
                        }
                    },
                )
                cnx.getInputStream().use { entree ->
                    provisoire.outputStream().use { sortie ->
                        val tampon = ByteArray(1 shl 16)
                        while (true) {
                            val lus = entree.read(tampon)
                            if (lus < 0) break
                            sha.update(tampon, 0, lus)
                            sortie.write(tampon, 0, lus)
                        }
                    }
                }
            }.onFailure { provisoire.delete(); return@withContext }
            // the barrier. an installer swapped on the site without the announcement does not get
            // past here
            if (sha.digest().hex() != variante.sha256) { provisoire.delete(); return@withContext }
            provisoire.renameTo(fichier)
        }
        // previously downloaded installers are no longer any use
        dossier.listFiles()?.filter { it != fichier }?.forEach { it.delete() }
        installeur = fichier
        prete = annonce
    }

    /**
     * launches the silent install after velvetdesk exits, since the msi otherwise replaces files in
     * use, then relaunches the app if asked. a small script in the cache rather than a command
     * line: `cmd`'s nested quotes break on the first path with a space in it
     */
    fun installer(relancer: Boolean) {
        val msi = installeur?.takeIf { it.exists() } ?: return
        val exe = System.getProperty("jpackage.app-path")
        val script = File(msi.parentFile, "installer.cmd")
        script.writeText(
            buildString {
                appendLine("@echo off")
                appendLine("timeout /t 3 /nobreak >nul")
                appendLine("msiexec /i \"${msi.absolutePath}\" /qn /norestart")
                if (relancer && exe != null) appendLine("start \"\" \"$exe\"")
                // `start` opens a `.cmd` under `cmd /K`, and without `exit` the minimised console
                // stayed open
                appendLine("exit")
            },
        )
        runCatching {
            ProcessBuilder("cmd", "/c", "start", "\"\"", "/min", script.absolutePath).start()
        }
        installeur = null
    }

    private fun empreinte(fichier: File): String {
        val sha = MessageDigest.getInstance("SHA-256")
        fichier.inputStream().use { entree ->
            val tampon = ByteArray(1 shl 16)
            while (true) {
                val lus = entree.read(tampon)
                if (lus < 0) break
                sha.update(tampon, 0, lus)
            }
        }
        return sha.digest().hex()
    }

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }

    /** the windows installer's key in the announcement */
    const val VARIANTE = "windows-x64"
}

/**
 * the "new version ready" banner at the bottom of the window, and nothing while there is nothing
 */
@Composable
fun BandeauMiseAJour(moteur: MatrixEngine, modifier: Modifier = Modifier) {
    val t = textes()
    // at startup, then every six hours: velvetdesk often stays open for days
    LaunchedEffect(moteur) {
        while (true) {
            runCatching { MiseAJourBureau.verifier(moteur) }
            kotlinx.coroutines.delay(6 * 3_600_000L)
        }
    }
    val prete = MiseAJourBureau.prete ?: return
    val tokens = LocalTokens.current
    Row(
        modifier.padding(bottom = 76.dp).clip(RoundedCornerShape(8.dp)).background(tokens.surfaceRaised)
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            textes().versionPreteALaFermeture(prete.versionName),
            color = tokens.textPrimary,
            fontSize = 14.sp,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            t.redemarrerMaintenant,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(tokens.accent).curseurMain()
                .clickable {
                    MiseAJourBureau.installer(relancer = true)
                    // the normal close, like the cross button: the install is already started, and
                    // the second call to `installer` on close does nothing
                    java.awt.Window.getWindows().forEach {
                        it.dispatchEvent(java.awt.event.WindowEvent(it, java.awt.event.WindowEvent.WINDOW_CLOSING))
                    }
                }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

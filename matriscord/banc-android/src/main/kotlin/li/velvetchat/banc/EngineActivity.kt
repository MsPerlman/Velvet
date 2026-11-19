package li.velvetchat.banc

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.model.EncryptionState
import li.velvetchat.core.model.MessageBody
import li.velvetchat.core.rust.RustMatrixEngine
import li.velvetchat.core.session.AndroidSessionStore
import java.io.File

class EngineActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var output: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        output = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTextIsSelectable(true)
            setPadding(20, 20, 20, 20)
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@EngineActivity).apply {
                text = "Moteur Matriscord — Android"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTypeface(typeface, Typeface.BOLD)
                setPadding(20, 20, 20, 0)
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(
                ScrollView(this@EngineActivity).apply { addView(output) },
                LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f),
            )
        })

        scope.launch { run() }
    }

    private suspend fun run() {
        val log: (String) -> Unit = { line ->
            Log.i(TAG, line)
            runOnUiThread { output.append(line + "\n") }
        }

        try {
            val dataDir = File(filesDir, "engine").apply { mkdirs() }
            val cacheDir = File(cacheDir, "engine").apply { mkdirs() }
            val engine: MatrixEngine = RustMatrixEngine(
                dataPath = dataDir.absolutePath,
                cachePath = cacheDir.absolutePath,
                insecureTlsForDevelopment = BuildConfig.INSECURE_TLS,
                trustAnchors = li.velvetchat.core.tls.AndroidTrustAnchors.load(),
            )
            if (BuildConfig.INSECURE_TLS) {
                log("⚠ TLS NON VÉRIFIÉ — contournement de développement, jamais en production")
                log("")
            }
            val sessions = AndroidSessionStore(applicationContext)

            log("① Initialisation de la plateforme…")
            engine.initPlatform()
            log("   ✓ runtime tokio + vérificateur TLS")
            log("")

            log("①bis Contrôle du TLS par le magasin Android…")
            val tls = runCatching {
                val c = java.net.URL("${BuildConfig.HOMESERVER}/_matrix/client/versions")
                    .openConnection() as javax.net.ssl.HttpsURLConnection
                c.connectTimeout = 15_000
                c.readTimeout = 15_000
                val code = c.responseCode
                c.disconnect()
                code
            }
            tls.fold(
                onSuccess = { log("   ✓ magasin Android : HTTP $it — le certificat est accepté") },
                onFailure = { log("   ✗ magasin Android : ${it::class.simpleName} — ${it.message}") },
            )

            log("   contrôle du SDK contre un serveur tiers…")
            val third = runCatching {
                org.matrix.rustcomponents.sdk.ClientBuilder()
                    .homeserverUrl("https://matrix.org")
                    .sessionPaths(
                        File(cacheDir, "probe").apply { mkdirs() }.absolutePath,
                        File(cacheDir, "probe-c").apply { mkdirs() }.absolutePath,
                    )
                    .build()
            }
            third.fold(
                onSuccess = { log("   ✓ SDK vs matrix.org : OK → le problème est propre à notre serveur") },
                onFailure = { log("   ✗ SDK vs matrix.org : ${it.cause?.message ?: it.message} → le problème est général") },
            )
            log("")

            log("①ter Reproduction de la validation du vérificateur…")
            log("   — notre serveur —")
            diagnoseChain(BuildConfig.HOMESERVER.removePrefix("https://"), log)
            log("   — serveur de référence —")
            diagnoseChain("matrix.org", log)
            log("")

            val stored = sessions.load()
            if (stored != null) {
                log("② Session restaurée depuis le Keystore Android")
                log("   AUCUN mot de passe utilisé")
                engine.restore(stored)
            } else {
                log("② Aucune session → connexion")
                val s = engine.login(
                    homeserverUrl = BuildConfig.HOMESERVER,
                    username = BuildConfig.USERNAME,
                    password = BuildConfig.PASSWORD,
                )
                sessions.save(s)
                log("   session chiffrée et enregistrée")
            }

            val session = engine.currentSession()
            log("   ${session.userId} / ${session.deviceId}")
            log("   sliding sync natif : ${session.slidingSyncNative}")
            log("")

            log("③ Synchronisation…")
            engine.startSync()
            log("   ✓ opérationnelle")
            log("")

            log("   chiffrement : ${engine.verification.verificationState()}")
            log("   sauvegarde  : ${engine.recovery.recoveryState()}")
            log("")

            val servers = engine.servers()
            log("④ ${servers.size} serveur(s) :")
            servers.forEach { server ->
                log("   ▸ ${server.name} (${server.channels.size})")
                server.channels.forEach { c ->
                    val lock = if (c.encryption == EncryptionState.ENCRYPTED) "🔒" else "  "
                    val badge = if (c.unread.hasUnread) " [${c.unread.notifications}]" else ""
                    log("      $lock #${c.name}$badge")
                }
            }
            log("")

            val encrypted = servers.flatMap { it.channels }
                .firstOrNull { it.encryption == EncryptionState.ENCRYPTED }
            if (encrypted == null) {
                log("⑤ Aucun salon chiffré à lire.")
                return
            }

            log("⑤ #${encrypted.name} — salon chiffré")
            val messages = engine.messages(encrypted.id, limit = 15)
            var readable = 0
            messages.forEach { m ->
                val who = m.senderDisplayName ?: m.sender
                when (val b = m.body) {
                    is MessageBody.Text -> { readable++; log("   $who : ${b.text}") }
                    is MessageBody.Undecryptable -> log("   $who : 🔒 (${b.reason})")
                    else -> Unit
                }
            }
            log("")
            log("   $readable message(s) lisible(s)")
            log("")
            if (readable > 0) {
                log("✓ LE MOTEUR TOURNE SUR ANDROID")
                log("  Même code que sur desktop, session chiffrée par le Keystore,")
                log("  contenu chiffré déchiffré localement.")
            } else {
                log("⚠ Aucun message lisible — cet appareil est neuf pour le chiffrement.")
                log("  Attendu : ses clés arrivent par récupération (A-bis.1) ou avec")
                log("  les messages reçus après son enregistrement.")
            }

            engine.stopSync()
        } catch (t: Throwable) {
            Log.e(TAG, "Le moteur a échoué sur Android", t)
            log("")
            log("✗ ÉCHEC : ${t::class.qualifiedName}")
            log("  ${t.message}")
            log("  cause : ${t.cause?.message}")
        }
    }

    private fun diagnoseChain(host: String, log: (String) -> Unit) {
        try {
            val trustAll = arrayOf<javax.net.ssl.TrustManager>(
                object : javax.net.ssl.X509TrustManager {
                    override fun checkClientTrusted(c: Array<java.security.cert.X509Certificate>, a: String) = Unit
                    override fun checkServerTrusted(c: Array<java.security.cert.X509Certificate>, a: String) = Unit
                    override fun getAcceptedIssuers() = arrayOf<java.security.cert.X509Certificate>()
                }
            )
            val ctx = javax.net.ssl.SSLContext.getInstance("TLS").apply {
                init(null, trustAll, java.security.SecureRandom())
            }
            val socket = ctx.socketFactory.createSocket(host, 443) as javax.net.ssl.SSLSocket
            socket.startHandshake()
            @Suppress("UNCHECKED_CAST")
            val sent = socket.session.peerCertificates.toList() as List<java.security.cert.X509Certificate>
            socket.close()
            log("   chaîne reçue : ${sent.size} certificat(s)")
            sent.forEach { log("     • ${it.subjectX500Principal.name.substringAfter("CN=").substringBefore(",")}") }

            val ks = java.security.KeyStore.getInstance("AndroidCAStore").apply { load(null) }
            val anchors = mutableSetOf<java.security.cert.TrustAnchor>()
            for (alias in ks.aliases()) {
                (ks.getCertificate(alias) as? java.security.cert.X509Certificate)?.let {
                    anchors += java.security.cert.TrustAnchor(it, null)
                }
            }
            log("   ancres système : ${anchors.size}")
            val hasX1 = anchors.any { it.trustedCert.subjectX500Principal.name.contains("ISRG Root X1") }
            val hasX2 = anchors.any { it.trustedCert.subjectX500Principal.name.contains("ISRG Root X2") }
            log("   ISRG Root X1 présente : $hasX1   |   ISRG Root X2 présente : $hasX2")

            val cf = java.security.cert.CertificateFactory.getInstance("X.509")
            val path = cf.generateCertPath(sent)
            val validator = java.security.cert.CertPathValidator.getInstance("PKIX")
            val revocation = validator.revocationChecker as java.security.cert.PKIXRevocationChecker
            revocation.options = java.util.EnumSet.of(
                java.security.cert.PKIXRevocationChecker.Option.SOFT_FAIL,
                java.security.cert.PKIXRevocationChecker.Option.ONLY_END_ENTITY,
            )
            val params = java.security.cert.PKIXParameters(anchors).apply {
                isRevocationEnabled = true
                addCertPathChecker(revocation)
            }

            runCatching { validator.validate(path, params) }.fold(
                onSuccess = {
                    log("   ✓ validation réussie ici — le rejet du SDK vient d'ailleurs")
                },
                onFailure = { e ->
                    log("   ✗ LE VRAI MOTIF, que le SDK réétiquette « Revoked » :")
                    log("     ${e::class.simpleName} : ${e.message}")
                    (e as? java.security.cert.CertPathValidatorException)?.let {
                        log("     raison  : ${it.reason}")
                        log("     index   : ${it.index}")
                    }
                    revocation.softFailExceptions.forEach {
                        log("     soft-fail : ${it.message}")
                    }
                },
            )
        } catch (t: Throwable) {
            log("   diagnostic impossible : ${t::class.simpleName} — ${t.message}")
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "MatriscordEngine"
    }
}

package li.velvetchat.app

import kotlinx.coroutines.flow.first

import li.velvetchat.core.tr

import android.content.Context
import li.velvetchat.app.ui.PrefsStatut
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.rust.RustMatrixEngine
import li.velvetchat.core.session.AndroidSessionStore
import li.velvetchat.core.tls.AndroidTrustAnchors
import java.io.File

/**
 * the engine, built once for the process
 *
 * deliberately an object and not an injection: at this stage there is one account and one session,
 * and one more abstraction would bring nothing checkable. the day it is justified, this is the only
 * place to change
 *
 * what counts here: the app only knows `MatrixEngine`. no screen imports anything from
 * `org.matrix.rustcomponents.sdk`, and that is the property this vertical slice is meant to test
 */
object Engine {

    @Volatile
    private var instance: MatrixEngine? = null

    fun get(context: Context): MatrixEngine = instance ?: synchronized(this) {
        instance ?: create(context.applicationContext).also { instance = it }
    }

    private fun create(app: Context): MatrixEngine = run {
        // what the install adds to the shared model, once for the process
        li.velvetchat.core.model.LienInvitation.base = BuildConfig.BASE_INVITATION
        creerMoteur(app)
    }

    private fun creerMoteur(app: Context): MatrixEngine = RustMatrixEngine(
        dataPath = File(app.filesDir, "engine").apply { mkdirs() }.absolutePath,
        cachePath = File(app.cacheDir, "engine").apply { mkdirs() }.absolutePath,
        // the system store's anchors: without them the sdk's verifier rejects a certificate android
        // itself accepts
        trustAnchors = AndroidTrustAnchors.load(),
        // the lan switch on the home network when the local url answers. empty means never
        localHomeserverUrl = BuildConfig.HOMESERVER_LOCAL,
        // the global cosmetics catalogue room. empty means none, and the app makes do with the
        // server catalogues, see `catalogueGlobal`
        aliasCatalogue = BuildConfig.CATALOGUE_COSMETIQUES,
        // only one account goes in: opening the shop used to walk everybody into a room nobody had
        // asked for
        intendantCatalogue = BuildConfig.INTENDANT,
        // the voice token service. empty and voice says it is not configured, which is a
        // supported state: the homeserver cannot advertise this address, so an install that
        // has no sfu has no way to guess one
        urlJetonVocal = BuildConfig.JETON_VOCAL,
    ).also { moteur ->
        moteur.initPlatform()
        // an oauth session's tokens change every few minutes, and the refresh token is single use
        moteur.surSessionRenouvelee { AndroidSessionStore(app).save(it) }
    }

    /**
     * restores the saved session, when there is one
     *
     * @return `true` when a session was found. `false` means "you have to log in", not "failure",
     *   and it is the normal state of a first install
     *
     * what changed: the previous version fell back on hard coded credentials when no session
     * existed. so the app had one single possible user, whoever had compiled it. that was the
     * project's real ceiling, higher even than the verification, which already assumes you are in
     */
    suspend fun restaurer(context: Context, engine: MatrixEngine): Boolean {
        val stored = AndroidSessionStore(context.applicationContext).load() ?: return false
        engine.restore(stored)

        // the chosen status is reapplied on every restore, and that is a fixed fault, not a
        // precaution
        //
        // every `/sync` announces a presence, and a client that says nothing announces "online".
        // and this function is called by every background wake up, the pushed notification, the
        // reply from the panel, the bubble, which open a session long enough to read an event.
        // without this line, every message received put the person back online although they had
        // declared themselves away. they declared themselves away, a message arrived, they went
        // back online: the app contradicted itself, once per notification
        //
        // no request: `annoncerPresence` only sets what the next sync will say
        runCatching {
            engine.annoncerPresence(PrefsStatut.choisi(context.applicationContext).first)
        }
        return true
    }

    /**
     * logs in and saves the session: without that you would have to start again on every launch,
     * which would make the login screen more of a nuisance than the shortcut it replaces
     */
    /** login through MAS, see [RetourOauth] */
    suspend fun connecterNavigateur(context: Context, engine: MatrixEngine, serveur: String) {
        val session = engine.loginNavigateur(serveur, RetourOauth.ADRESSE, creation = false) {
            RetourOauth.ouvrir(context, it)
        }
        AndroidSessionStore(context.applicationContext).save(session)
    }

    /** account creation on MAS's page, then the space the invite code carries */
    suspend fun inscrireNavigateur(context: Context, engine: MatrixEngine, serveur: String, code: String) {
        val invitation = li.velvetchat.core.model.CodeInvitation.decoder(code)
            ?: throw IllegalArgumentException(tr("Code d'invitation illisible.", "Unreadable invite code."))
        val session = engine.loginNavigateur(serveur, RetourOauth.ADRESSE, creation = true) {
            RetourOauth.ouvrir(context, it)
        }
        AndroidSessionStore(context.applicationContext).save(session)
        runCatching { engine.joinServer(invitation.idEspace) }
        rejoindreLeCanalDeMiseAJour(engine)
    }

    /** the admin scopes, asked through the browser on the same device */
    suspend fun activerAdministration(context: Context, engine: MatrixEngine) {
        val session = engine.activerAdministration(RetourOauth.ADRESSE) { RetourOauth.ouvrir(context, it) }
        AndroidSessionStore(context.applicationContext).save(session)
    }

    suspend fun connecter(
        context: Context,
        engine: MatrixEngine,
        serveur: String,
        identifiant: String,
        motDePasse: String,
    ) {
        val session = engine.login(serveur, identifiant, motDePasse)
        AndroidSessionStore(context.applicationContext).save(session)
    }

    /**
     * signs up with an invite code, then joins the space it carries
     *
     * two protocol calls, one single operation from the point of view of whoever is signing up: an
     * account created without the space promised by the link would be a partial failure presented
     * as a success. but they do not fail for the same reasons, and are therefore not handled the
     * same way:
     *
     *     - when the sign up fails (token refused, id taken), nothing was created: the call throws, and the
     *       screen has to say so for the right field to be fixed
     *     - when the sign up succeeds but joining the space fails, the account already exists. failing the
     *       whole call would invite retrying the sign up, and would land on "id already taken", for a problem
     *       that has nothing to do with it. so we swallow that failure: the person ends up with an account
     *       and no space, which the shell's empty screen now knows how to welcome rather than letting them
     *       think something is broken
     */
    suspend fun inscrire(
        context: Context,
        engine: MatrixEngine,
        serveur: String,
        identifiant: String,
        motDePasse: String,
        codeInvitation: String,
    ) {
        val invitation = li.velvetchat.core.model.CodeInvitation.decoder(codeInvitation)
            ?: throw IllegalArgumentException(tr("Code d'invitation illisible.", "Unreadable invite code."))
        val session = engine.register(serveur, identifiant, motDePasse, invitation.jeton)
        AndroidSessionStore(context.applicationContext).save(session)
        runCatching { engine.joinServer(invitation.idEspace) }
        rejoindreLeCanalDeMiseAJour(engine)
    }

    /**
     * entering the space where versions are announced, when the account is created
     *
     * a version announcement is a space's state event: you only see it if you are in that space.
     * nobody was entering it, so the automatic update could never have worked for anybody. the
     * client was indeed looking everywhere, and the announcement was nowhere it could see
     *
     * we join, we are not invited. an invitation has to be sent by somebody who already has the
     * right to invite, and a newborn account cannot invite itself, and putting a privileged account
     * in the client for that would amount to putting a token in it. so the space is public, and we
     * join it, same result, nothing to accept, no secret in the app
     *
     * the failure is swallowed: an account with no update channel is an account that works, and
     * failing the sign up over it would be disproportionate. the person can join it by hand, it is
     * a space like any other
     */
    suspend fun rejoindreLeCanalDeMiseAJour(engine: MatrixEngine) {
        // announcements now live in the installation's hidden room, which the engine joins by
        // itself. a separate channel is only joined when the server still names one
        val canal = li.velvetchat.core.Installation.config.value.misesAJour.takeIf { it.isNotBlank() } ?: return
        runCatching { engine.joinServer(canal) }
    }

    /**
     * logs out, erases everything belonging to the account, and restarts the app
     *
     * three inseparable gestures:
     *     - the engine revokes the token and stops
     *     - the encrypted session and the sdk's database (history, keys, media) are erased, keeping them
     *       would open the next session on the previous one's conversations
     *     - the process starts from scratch, because the watch service, the pusher and the screens all hold
     *       a reference to the engine we have just closed
     *
     * never returns: the app is relaunched on its login screen
     */
    suspend fun deconnecter(activite: android.app.Activity, engine: MatrixEngine) {
        val app = activite.applicationContext
        runCatching { ServiceVeille.arreter(app) }
        runCatching { engine.deconnecter() }
        AndroidSessionStore(app).clear()
        listOf(File(app.filesDir, "engine"), File(app.cacheDir, "engine"))
            .forEach { runCatching { it.deleteRecursively() } }
        val depart = app.packageManager.getLaunchIntentForPackage(app.packageName)
            ?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        depart?.let { activite.startActivity(it) }
        Runtime.getRuntime().exit(0)
    }

    /** the server offered by default on the login screen, the common case and not a limit */
    fun serveurParDefaut(): String = BuildConfig.HOMESERVER
}

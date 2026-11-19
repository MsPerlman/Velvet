package li.velvetchat.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import li.velvetchat.app.ui.warning
import li.velvetchat.app.ui.surfaceRaised
import li.velvetchat.app.ui.espL
import li.velvetchat.app.ui.espM
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import li.velvetchat.app.ui.LocalTokens
import li.velvetchat.app.ui.EcouteVerification
import li.velvetchat.app.ui.EcranConnexion
import li.velvetchat.app.ui.VelvetTheme
import li.velvetchat.app.ui.Shell
import li.velvetchat.app.ui.VerificationScreen
import li.velvetchat.app.ui.bg
import li.velvetchat.app.ui.danger
import li.velvetchat.app.ui.textMuted
import li.velvetchat.app.ui.textPrimary
import li.velvetchat.app.ui.textSecondary
import li.velvetchat.core.AccessState
import li.velvetchat.core.ConnectionState
import li.velvetchat.core.essayer
import li.velvetchat.core.model.Server
import li.velvetchat.app.ui.horsUi
import li.velvetchat.app.ui.textes

/**
 * the vertical slice
 *
 * its aim is not to deliver a finished interface, but to get `MatrixEngine` consumed by a real
 * screen, because that is where you find out what the contract does not say
 *
 * what it establishes or disproves: - the engine's flows feed a compose ui with no intermediate
 * layer - the theme tokens are enough to dress a real screen - `MatrixEngine` holds as the single
 * boundary, with no sdk import in this module
 */
class VelvetActivity : ComponentActivity() {

    /**
     * the `POST_NOTIFICATIONS` request
     *
     * registered here and fired once the session is found again, and not on the first screen: a
     * permission asked before people know what it is about gets refused, and a refusal is far
     * harder to recover from than a delayed request. we do not read the result, refusing being a
     * legitimate choice, the service runs anyway and the messages arrive, they just do not announce
     * themselves
     */
    private val demanderNotifications = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }

    // see `Veille.auPremierPlan`: that is what tells "i am reading this room" from "i had it open
    // before putting the phone down"
    override fun onStart() {
        super.onStart()
        Veille.auPremierPlan = true
    }

    // back from the browser with no login: see `RetourOauth.auRetour`
    override fun onResume() {
        super.onResume()
        RetourOauth.auRetour()
    }

    override fun onStop() {
        Veille.auPremierPlan = false
        super.onStop()
    }

    /**
     * what finds main thread reads on its own
     *
     * a `SharedPreferences` or file read from the composition does not show: it costs three
     * milliseconds on the development machine and thirty on a loaded phone, and nobody looks for it
     * until it causes an anr. `StrictMode` flags it the moment it happens, with the stack, and it
     * is the only way of finding them other than by hand
     *
     * log and not crash. `penaltyDeath` would turn every existing read into an abrupt stop, so into
     * an unusable app until they were all fixed, and we want the list, not the punishment. debug
     * only: in release, the watch would cost and bring nobody anything
     */
    private fun veillerAuFilPrincipal() {
        if (!BuildConfig.DEBUG) return
        android.os.StrictMode.setThreadPolicy(
            android.os.StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build(),
        )
        android.os.StrictMode.setVmPolicy(
            android.os.StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build(),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        veillerAuFilPrincipal()
        val engine = Engine.get(this)
        ServiceVeille.creerCanaux(this)

        setContent {
            // the language, reread on every change: `PrefsLangue.revision` is the key
            @Suppress("UNUSED_EXPRESSION") li.velvetchat.app.ui.PrefsLangue.revision
            val langue = li.velvetchat.app.ui.PrefsLangue.choisie(this@VelvetActivity)
            androidx.compose.runtime.CompositionLocalProvider(
                li.velvetchat.app.ui.LocalLangue provides langue,
            ) {
            val t = textes()

            // **the system bar icons follow the theme**
            //
            // nothing took care of it, and it did not show while every theme was dark: android's
            // white icons were fine. the day "Blossom" became reachable from the settings, the
            // clock and the battery turned white on pale pink — unreadable. seen on screen
            //
            // `isAppearanceLightStatusBars` means "the bar is light, write on it in dark", hence
            // the negation of the theme's `dark` flag
            val themeCourant = ThemeVelvetAndroid.courant(this@VelvetActivity)
            androidx.compose.runtime.LaunchedEffect(themeCourant.dark) {
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                    .apply {
                        isAppearanceLightStatusBars = !themeCourant.dark
                        isAppearanceLightNavigationBars = !themeCourant.dark
                    }
            }

            // read **inside** the composition, and not before: since a screen lets one be
            // applied, the theme changes precisely while you are looking. `ThemeVelvetAndroid`
            // keeps an observable version and caches the result, so the file is reread on a
            // change and not on every frame
            VelvetTheme(
                theme = themeCourant,
                famille = li.velvetchat.app.ui.policeVelvet(),
                policeEmoji = li.velvetchat.app.ui.POLICE_TWEMOJI,
            ) {
                var erreur by remember { mutableStateOf<String?>(null) }
                var acces by remember { mutableStateOf<AccessState>(AccessState.SignedOut) }
                var connexion by remember { mutableStateOf(ConnectionState.STOPPED) }
                var serveurs by remember { mutableStateOf<List<Server>>(emptyList()) }

                // this flag is not cosmetic: `serversFlow()` requires a started sync and throws
                // otherwise. the three collections launched alongside `startSync()` therefore
                // started at the same time as it, and the servers' one failed before it had
                // finished, silently, leaving an empty rail with nothing to explain it
                var pret by remember { mutableStateOf(false) }

                // three states and not two: "we do not know yet" is not "logged out". confusing
                // them would flash the login screen at startup, for as long as reading the session
                // off the disk takes
                var session by remember { mutableStateOf<Boolean?>(null) }

                // seen with our own eyes: the verification completed on this screen
                var verifieIci by remember { mutableStateOf(false) }

                /**
                 * "later", the degraded mode
                 *
                 * what it fixes: the verification screen blocked outright. and it often needs
                 * coordinating between two people, in real time, so a child installing the app on a
                 * wednesday afternoon while their parents are at work had no way of moving on.
                 * locking somebody out of their messenger because they cannot attest a device right
                 * now is disproportionate: what they risk is an unreadable history, not a leak
                 *
                 * deliberately not persisted. deferring counts for this session, and at the next
                 * start the question comes back, because it has not gone away. a deferral written
                 * to disk would become a definitive refusal taken without saying so
                 */
                var reporte by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    runCatching { Engine.restaurer(this@VelvetActivity, engine) }
                        .onSuccess { session = it }
                        .onFailure { erreur = it.message ?: it::class.simpleName }
                }

                LaunchedEffect(session) {
                    if (session != true) return@LaunchedEffect
                    runCatching { engine.startSync() }
                        .onSuccess { pret = true }
                        .onFailure { erreur = it.message ?: it::class.simpleName }
                    // the update channel, on every launch and not only at sign up. the accounts
                    // created before this hook up existed would never have entered it, and they are
                    // precisely the ones that never received an update. entering twice costs
                    // nothing
                    Engine.rejoindreLeCanalDeMiseAJour(engine)

                    // the watch service, which holds the connection and posts the notifications
                    // when the app is no longer in the foreground. started only once the session is
                    // found: with no account it would have nothing to listen to and would show a
                    // permanent row for nothing
                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                        demanderNotifications.launch(
                            android.Manifest.permission.POST_NOTIFICATIONS
                        )
                    }

                    // the pusher before the service, and the order matters: it is the registration
                    // that decides whether the watch still has a reason to exist. done on every
                    // start because a firebase token is not definitive, it changes on reinstall, on
                    // restore, when play services is purged. no effect and no error when firebase
                    // is not configured
                    Push.enregistrer(this@VelvetActivity, engine)
                    ServiceVeille.demarrer(this@VelvetActivity)
                }

                // autofill follows the screen, and that is a promise kept
                //
                // android offered "autofill" permanently above the message field: the password
                // manager takes a `BasicTextField` for a form, for want of knowing what it expects.
                // the exclusion had then been set on the whole window, with the note that it would
                // have to be lifted the day a login screen arrived. that day came: it now only
                // holds once logged in, where there is indeed nothing left to fill
                LaunchedEffect(session) {
                    window.decorView.importantForAutofill = if (session == true) {
                        android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                    } else {
                        android.view.View.IMPORTANT_FOR_AUTOFILL_AUTO
                    }
                }
                LaunchedEffect(Unit) { engine.accessFlow().collect { acces = it } }
                LaunchedEffect(Unit) { engine.connectionFlow().collect { connexion = it } }
                LaunchedEffect(pret) {
                    if (!pret) return@LaunchedEffect
                    essayer { engine.serversFlow().collect { serveurs = it } }
                        .onFailure { erreur = horsUi.serveursIndisponibles(it.message.orEmpty()) }
                }

                // the banner takes its place instead of laying itself over
                //
                // it was a `fillMaxSize` layer aligned to the bottom: it covered the lower half of
                // the composer, seen on screen on an unverified device. its own comment promised
                // not to bleed over the composer, and it did
                //
                // a column settles it: the content takes what is left, and the banner takes its
                // height. `consumeWindowInsets` is the piece that would otherwise be missing,
                // without it the `Shell`'s composer would also add the gesture bar's inset, when
                // the banner now carries it, and you would gain twenty four points of emptiness
                // between the two
                val bandeau = reporte && acces is AccessState.VerificationRequired
                Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier.weight(1f).then(
                        if (bandeau) {
                            Modifier.consumeWindowInsets(WindowInsets.navigationBars)
                        } else Modifier
                    )
                ) {
                when {
                    erreur != null -> Message(t.connexionImpossible, erreur!!, erreur = true)

                    session == null -> Message(t.demarrage, t.lectureDeLaSession)

                    session == false -> EcranConnexion(
                        serveurParDefaut = Engine.serveurParDefaut(),
                        onConnexion = { serveur, identifiant, motDePasse ->
                            Engine.connecter(
                                this@VelvetActivity, engine, serveur, identifiant, motDePasse,
                            )
                            session = true
                        },
                        onInscription = { serveur, identifiant, motDePasse, code ->
                            Engine.inscrire(
                                this@VelvetActivity, engine,
                                serveur, identifiant, motDePasse, code,
                            )
                            session = true
                        },
                        parNavigateur = { engine.connexionParNavigateur(it) },
                        onConnexionNavigateur = { serveur ->
                            Engine.connecterNavigateur(this@VelvetActivity, engine, serveur)
                            session = true
                        },
                        onInscriptionNavigateur = { serveur, code ->
                            Engine.inscrireNavigateur(this@VelvetActivity, engine, serveur, code)
                            session = true
                        },
                    )

                    // the access policy is decided by the engine, not here: the ui only renders it.
                    // that is what stops an improvised blocking rule, screen by screen, ending up
                    // divergent
                    //
                    // the local latch beats the flow, and that is an observation rather than a
                    // liberty: when the verification screen reports having completed, the
                    // comparison was approved on both sides and the device is signed. `accessFlow`
                    // does not re emit in that case, and without this latch a freshly verified
                    // device stayed stuck on its success screen
                    acces is AccessState.VerificationRequired && !verifieIci && !reporte ->
                        VerificationScreen(
                            engine = engine,
                            canUseRecoveryKey =
                                (acces as AccessState.VerificationRequired).canUseRecoveryKey,
                            aUnAppareilTemoin =
                                (acces as AccessState.VerificationRequired).aUnAppareilTemoin,
                            onVerifie = { verifieIci = true },
                            onPlusTard = { reporte = true },
                        )

                    serveurs.isEmpty() && connexion != ConnectionState.CONNECTED ->
                        Message(t.connexionEnCours, t.synchronisationEnCours)

                    else -> Shell(engine, serveurs, connexion)
                }

                // the degraded mode banner
                //
                // it does not fade and has no cross, deliberately: what it announces is not a
                // notice but a state, the earlier messages staying unreadable until the device is
                // attested. a banner you can dismiss would let you forget a degradation that does
                // not forget itself
                //
                // it is the recovery path: it is the way back into the verification at any moment,
                // placed where the gap shows rather than in a settings screen you would have to
                // think of opening
                }
                if (bandeau) BandeauNonVerifie(onReprendre = { reporte = false })
                }

                // listening for verification requests is set up here, above everything else, and
                // only once access has been obtained
                //
                // above: a request arrives while you are reading a conversation, not when you went
                // looking for it, and a screen you would have to open yourself would miss exactly
                // what it exists for
                //
                // after access: as long as this device is not verified, it has nothing to attest
                // for another. listening earlier would have it offer to validate a trust it does
                // not have
                //
                // the flag is a latch: once access is obtained, the listening stays in place even
                // if `acces` transiently passes through another state, which is precisely what
                // happens during a verification, since it is the verification that makes that state
                // change. without the latch the collector restarted at the worst moment and lost
                // the step under way
                var ecouteActive by remember { mutableStateOf(false) }
                if (acces is AccessState.Ready) ecouteActive = true
                if (ecouteActive) EcouteVerification(engine)
            }
        }
    }
}

/**
 * the degraded mode banner, placed at the bottom and not at the top
 *
 * at the top it would take the room header's place, which is to say where you look to know where
 * you are. at the bottom, above the composer, it stays visible without disputing the place of what
 * you are reading
 */
@Composable
private fun BandeauNonVerifie(onReprendre: () -> Unit) {
    val t = textes()
    val tokens = LocalTokens.current
    // a thin strip, opaque and on one line: it says the state without eating the screen or bleeding
    // over the composer. the old version (two lines, a translucent tile) sprawled
    //
    // no longer a `fillMaxSize` layer: it is now the last child of a column, so it takes its own
    // height instead of laying itself over what is at the bottom. see the caller, that is where the
    // difference is made and where it is explained
    Box {
        Row(
            Modifier.fillMaxWidth()
                .background(tokens.surfaceRaised)
                .clickable(onClick = onReprendre)
                .navigationBarsPadding()
                .padding(horizontal = tokens.espL, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                t.appareilNonVerifie,
                color = tokens.textMuted,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                t.verifier,
                color = tokens.warning,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
        }
    }
}

/** a full screen for the states where there is nothing to show, and why */
@Composable
private fun Message(titre: String, detail: String, erreur: Boolean = false) {
    val tokens = LocalTokens.current
    Box(
        Modifier.fillMaxSize().background(tokens.bg).padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                titre,
                color = if (erreur) tokens.danger else tokens.textPrimary,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                detail,
                color = if (erreur) tokens.textSecondary else tokens.textMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = tokens.espM),
            )
        }
    }
    }
}

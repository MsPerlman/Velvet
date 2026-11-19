package li.velvetchat.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import li.velvetchat.app.ui.Conversation
import li.velvetchat.app.ui.LocalEngine
import li.velvetchat.app.ui.LocalMonIdentifiant
import li.velvetchat.app.ui.LocalTokens
import li.velvetchat.app.ui.VelvetTheme
import li.velvetchat.app.ui.bg
import li.velvetchat.app.ui.textMuted
import li.velvetchat.core.model.Channel
import li.velvetchat.core.model.ChannelKind

/**
 * the conversation bubble: one single discussion, floating over the other apps
 *
 * ## what it is exactly, and what it is not
 *
 * it is indeed the messenger style chat head, with one detail that changes everything: since
 * android 11, messenger no longer has its own bubble, it uses the system's. so ours is the same
 * feature and not an imitation, no `SYSTEM_ALERT_WINDOW`, no hand drawn overlay, no frightening
 * permission to ask for. the os is what draws the dot, moves it, stacks it with other apps' and
 * decides its fate when memory runs short
 *
 * a direct consequence: it is not us who decide that a conversation bubbles. we propose it (see
 * `ServiceVeille`, which attaches a `BubbleMetadata` to every conversation notification) and the
 * person accepts, by notification or in the system settings. an imposed bubble would be refused by
 * the system, and just as well
 *
 * ## why a separate activity
 *
 * a bubble is a window a few centimetres across. `VelvetActivity` would fit the server rail, the
 * room drawer and the conversation into it, three surfaces designed for a whole phone screen. this
 * activity shows only the conversation, which is exactly what you come for when you tap a bubble
 *
 * it reuses the shell's `Conversation` composable rather than a reduced version: the same timeline,
 * the same composer, the same gestures. writing a second one would be the two paths problem, two
 * routes to the same output, only one of which gets the fixes
 *
 * what it does not have, for want of a server context: the role colours, the custom emojis and the
 * room autocomplete. the matching `CompositionLocal`s have empty defaults, so everything degrades
 * cleanly, a nickname keeping its neutral colour instead of disappearing
 *
 * ## the manifest is not optional
 *
 * `documentLaunchMode="always"`, `allowEmbedded` and `resizeableActivity` are required by the
 * system: without them the `BubbleMetadata` is silently rejected and the notification stays an
 * ordinary one. it is the kind of omission that produces no error
 */
class ActiviteBulle : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val engine = Engine.get(this)
        val salonId = intent?.getStringExtra(ServiceVeille.EXTRA_SALON)

        // the bubble carries the same theme as the app: it is the same person looking at it, and a
        // bubble in another theme's colours would read as another app
        val themeVelvet = themeDeLUtilisateur(this)

        setContent {
            // the bubble is a composition root in itself: the chosen language has to be provided in
            // it too, otherwise it stays in french whatever you set elsewhere
            @Suppress("UNUSED_EXPRESSION") li.velvetchat.app.ui.PrefsLangue.revision
            val langue = li.velvetchat.app.ui.PrefsLangue.choisie(this@ActiviteBulle)
            CompositionLocalProvider(li.velvetchat.app.ui.LocalLangue provides langue) {
            val t = li.velvetchat.app.ui.textes()
            VelvetTheme(
                theme = themeVelvet,
                famille = li.velvetchat.app.ui.policeVelvet(),
                policeEmoji = li.velvetchat.app.ui.POLICE_TWEMOJI,
            ) {
                var salon by remember { mutableStateOf<Channel?>(null) }
                var moi by remember { mutableStateOf<String?>(null) }
                var erreur by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(salonId) {
                    if (salonId == null) {
                        erreur = t.aucuneConversationDesignee
                        return@LaunchedEffect
                    }
                    // the bubble can open when the app has never been launched: so the session has
                    // to be restored and the sync started here, as the watch service does.
                    // idempotent on both sides
                    runCatching {
                        Engine.restaurer(this@ActiviteBulle, engine)
                        engine.startSync()
                    }.onFailure { erreur = it.message ?: t.sessionIndisponible }

                    moi = runCatching { engine.currentSession().userId }.getOrNull()
                    salon = runCatching { retrouver(engine, salonId) }.getOrNull()
                    if (salon == null && erreur == null) {
                        erreur = t.conversationPlusAccessible
                    }
                }

                // the bubble counts as "room open". without that, reading in the bubble would go on
                // producing notifications for what you are reading, the fault
                // `Veille.auPremierPlan` fixes for the main screen, coming back through another
                // door
                androidx.compose.runtime.DisposableEffect(salonId) {
                    Veille.salonOuvert = salonId
                    Veille.auPremierPlan = true
                    onDispose {
                        Veille.salonOuvert = null
                        Veille.auPremierPlan = false
                    }
                }

                val tokens = LocalTokens.current
                Box(Modifier.fillMaxSize().background(tokens.bg)) {
                    val courant = salon
                    when {
                        courant != null -> CompositionLocalProvider(
                            LocalEngine provides engine,
                            LocalMonIdentifiant provides moi,
                        ) {
                            Conversation(
                                engine = engine,
                                channel = courant,
                                // the arrow was inert, and it was a wiring fault rather than a
                                // drawing one: `onOuvrirTiroir` is `{}` by default, and the bubble
                                // provided nothing. so the bar showed a control that did nothing at
                                // all, reported from outside
                                //
                                // a bubble has no room drawer to open: going back, here, means
                                // folding it. the system then brings it back to its dot, and the
                                // conversation stays one tap away
                                onOuvrirTiroir = { finish() },
                            )
                        }

                        erreur != null -> Text(
                            erreur!!,
                            color = tokens.textMuted,
                            fontSize = 14.sp,
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        )

                        else -> Text(
                            t.ouverture,
                            color = tokens.textMuted,
                            fontSize = 14.sp,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }
            }
        }
    }
}

/**
 * finds the named room, wherever it is
 *
 * a room id does not say whether it belongs to a server or is a private conversation, and the
 * notification does not know either. so we look at both, in this order: the server hierarchy
 * carries a room's name and its type, and a private conversation carries the person's name
 */
private suspend fun retrouver(
    engine: li.velvetchat.core.MatrixEngine,
    salonId: String,
): Channel? {
    runCatching { engine.servers() }.getOrNull()
        ?.flatMap { it.channels }
        ?.firstOrNull { it.id == salonId }
        ?.let { return it }

    return runCatching { engine.directs() }.getOrNull()
        ?.firstOrNull { it.roomId == salonId }
        ?.let {
            Channel(
                id = it.roomId,
                name = it.name,
                topic = null,
                kind = ChannelKind.TEXT,
                unread = it.unread,
            )
        }
}

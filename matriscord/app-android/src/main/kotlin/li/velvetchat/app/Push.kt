package li.velvetchat.app

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import li.velvetchat.core.MatrixEngine

/**
 * registering this device with the server, so it sends the pushes there
 *
 * ## why firebase, when the plan ruled it out
 *
 * we refused firebase for a reason that still holds: not handing the content of the conversations
 * to a third party. that is not what happens here. the payload is `EVENT_ID_ONLY`, two ids, room
 * and event. google learns that a wake up is due for this device for this room, and it learns
 * neither who is writing nor what is written. the phone is what fetches the event and decrypts it
 * with its own keys
 *
 * what changed our mind is a usage fault, reported from the device: the permanent "Velvet is
 * receiving your messages in the background" notification. android imposes it on any foreground
 * service and does not allow hiding it, `IMPORTANCE_MIN` channel included, which was already done.
 * the only way to be rid of it is to have no service at all, so to be woken from outside
 *
 * ## three pieces, and one missing is enough
 *
 * 1. a firebase project, so `google-services.json`. without it the plugin is not applied and
 * [BuildConfig.FIREBASE] is `false` 2. a sygnal gateway reachable by the homeserver,
 * [li.velvetchat.core.Installation.ou({ this.push }, BuildConfig.PUSH_GATEWAY)], empty until it is deployed 3. an open session, since the pusher is
 * registered on the account
 *
 * [estConfigure] only answers for the first two. failing that, the app keeps [ServiceVeille] and
 * its permanent notification: that is the intended degradation, and it is silent by construction,
 * nothing breaks, we simply fall back on the old path
 */
object Push {

    /**
     * the pusher's `app_id`, on the matrix side
     *
     * it is the key sygnal uses to pick its configuration, not a decorative id: its `apps:` table
     * must contain exactly this string, otherwise it refuses the notification with "no app
     * configured". the `.android` suffix is sygnal's convention, it also serving ios, and the
     * application id already carries `.debug` for the debug variant, so the two builds have
     * distinct pushers and can coexist on the same account
     */
    val appId: String get() = BuildConfig.APPLICATION_ID + ".android"

    /** are both halves of the configuration there? see the class documentation */
    val estConfigure: Boolean
        get() = BuildConfig.FIREBASE && li.velvetchat.core.Installation.ou({ this.push }, BuildConfig.PUSH_GATEWAY).isNotBlank()

    /**
     * declares this device. no effect, and no error, when the configuration is missing
     *
     * called on every start and not once: a firebase token is not definitive. it changes on
     * reinstall, on restoring a backup, when play services data is cleared, and sometimes on its
     * own. reregistering costs one request and avoids the one fault you would never see,
     * notifications stopping, one day, without a word
     *
     * `append = false` on the engine's side replaces the pusher with the same key, so reregistering
     * does not create a duplicate
     *
     * @return the registered token, or `null` when nothing was done
     */
    suspend fun enregistrer(contexte: Context, engine: MatrixEngine): String? {
        if (!estConfigure) return null
        val jeton = jeton() ?: return null
        return runCatching {
            engine.notifications.registerPushFcm(
                jeton = jeton,
                gatewayUrl = li.velvetchat.core.Installation.ou({ this.push }, BuildConfig.PUSH_GATEWAY),
                appId = appId,
                // what the person will see in their device list, on the server
                deviceName = android.os.Build.MODEL ?: "Android",
            )
            memoriser(contexte, jeton)
            jeton
        }.onFailure {
            android.util.Log.w(JOURNAL, "Pusher non enregistré", it)
        }.getOrNull()
    }

    /**
     * removes the pusher. to be called on logout, and it matters: an orphan pusher goes on pushing
     * forever to a device that no longer reads, without ever saying so
     *
     * the token is read back from local memory rather than asked of firebase again: at logout we
     * want to remove the one we registered, not the one firebase would return today, the two can
     * differ, and it is precisely the old one that is lying around
     */
    suspend fun retirer(contexte: Context, engine: MatrixEngine) {
        val jeton = memorise(contexte) ?: return
        runCatching { engine.notifications.unregisterPush(jeton, appId) }
            .onFailure { android.util.Log.w(JOURNAL, "Pusher non retiré", it) }
        memoriser(contexte, null)
    }

    /**
     * this installation's token
     *
     * `suspendCancellableCoroutine` rather than the `kotlinx-coroutines-play-services` dependency:
     * it would bring only an `await()` on `Task`, and that is six lines
     *
     * `null` on failure, without throwing: play services can be absent (a degoogled device), out of
     * date, or simply offline at startup. none of those cases must stop the app working, they only
     * keep it on [ServiceVeille]
     */
    private suspend fun jeton(): String? = runCatching {
        suspendCancellableCoroutine { suite ->
            FirebaseMessaging.getInstance().token
                .addOnCompleteListener { tache ->
                    suite.resume(
                        if (tache.isSuccessful) tache.result?.takeIf { it.isNotBlank() } else null
                    )
                }
        }
    }.onFailure { android.util.Log.w(JOURNAL, "Jeton Firebase indisponible", it) }.getOrNull()

    // the registered token, kept so it can be removed later. a preference and not a field: the
    // logout can happen in a process that never registered
    private fun prefs(contexte: Context) =
        contexte.applicationContext.getSharedPreferences("push", Context.MODE_PRIVATE)

    private fun memoriser(contexte: Context, jeton: String?) =
        prefs(contexte).edit().apply {
            if (jeton == null) remove(CLE_JETON) else putString(CLE_JETON, jeton)
        }.apply()

    private fun memorise(contexte: Context): String? =
        prefs(contexte).getString(CLE_JETON, null)

    /**
     * has push actually taken over from [ServiceVeille]?
     *
     * a memorised token and not `estConfigure` alone: the configuration says we can register, and
     * the memorised token says we did. confusing the two would cut the watch on a device that never
     * managed to register, offline at the first start, play services missing, which is to say a
     * device that would no longer be told anything, with nothing saying so
     */
    fun aPrisLeRelais(contexte: Context): Boolean =
        estConfigure && memorise(contexte) != null

    private const val CLE_JETON = "jeton"
    private const val JOURNAL = "Velvet/Push"
}

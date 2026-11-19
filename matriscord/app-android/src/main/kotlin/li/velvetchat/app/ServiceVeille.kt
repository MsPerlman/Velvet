package li.velvetchat.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import li.velvetchat.core.essayer
import li.velvetchat.core.model.AlerteSalon
import li.velvetchat.app.ui.horsUi

/**
 * the notifications, and why they do not go through firebase
 *
 * there were none at all: no channel, no service, not a single call to `NotificationManager`. the
 * engine did carry a complete `NotificationEngine`, designed for unifiedpush, decided precisely to
 * avoid firebase, but nothing on the app's side called it. "the notifications do not work"
 * describes a missing feature, not a broken one
 *
 * ## the choice: this service rather than fcm
 *
 * fcm would do the job, and it was set aside for three reasons, two of them practical:
 *
 * 1. it requires a firebase project, a `google-services.json` per variant, and a push gateway on
 * the server side (sygnal) to install and maintain on the nas. three pieces of infrastructure for
 * ten phones 2. it routes the existence of every message through google, which we explicitly
 * refuse, and which the ntfy/unifiedpush path exists to avoid 3. the sync is already running. what
 * was missing was not a transport channel, it was somebody to look at what it brings back and put
 * it in the status bar
 *
 * so this service is the version that works today, with nothing to install anywhere
 *
 * what it costs, honestly: a held connection, so battery, and a permanent notification android
 * imposes and that cannot be hidden. the day it weighs, `NotificationEngine.registerPush` is
 * already written and waiting for an ntfy server, which is the way out, and it will not ask for
 * this file to be rewritten
 *
 * ## what it does not notify
 *
 *     - your own messages, sent from another device ([AlerteSalon.aMoi])
 *     - the room open on screen ([Veille.salonOuvert]), being told about what you are reading being a
 *       messaging client's most irritating fault
 *     - the same message twice: [dejaVues] holds the last event announced per room
 */
class ServiceVeille : Service() {

    private var boucle: Job? = null
    private val portee = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** `salonId -> last announced event`. see [AlerteSalon.dernierEvenement] */
    private val dejaVues = mutableMapOf<String, String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        creerCanaux(this)
        // `startForeground` must arrive within seconds of the start, otherwise android kills the
        // service with a `ForegroundServiceDidNotStartInTimeException`. so it is here, before any
        // work, and not after the first sync
        demarrerEnPremierPlan()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (boucle == null) boucle = portee.launch { veiller() }
        // `START_STICKY`: if the system kills us under memory pressure, it restarts us. without it
        // the notifications would stop one day with nobody knowing when
        return START_STICKY
    }

    override fun onDestroy() {
        portee.cancel()
        super.onDestroy()
    }

    /**
     * the loop: on every change of the room list, reread what is unread
     *
     * `serversFlow` as a signal and not as data. it already emits on every message received, which
     * is what updates the badges, so there is no poll to write and no interval to pick
     *
     * `collect` and not `collectLatest`, and that is a fix, not a preference. `collectLatest`
     * cancels the current block as soon as a value arrives, and two messages sent back to back
     * produce several. the cancellation landed in the middle of `annoncer`, after the dedup key had
     * been set, so the notification was lost, and never retried since the message counted as
     * already announced. measured on the device: two messages sent, `notification_count` at 2 on
     * the server, zero notifications shown
     *
     * the flow is already debounced upstream (`SERVERS_DEBOUNCE_MS`), so nothing piles up
     */
    private suspend fun veiller() {
        val engine = Engine.get(this)
        // a session is needed, without it the flow throws and the service is no use
        if (!runCatching { Engine.restaurer(this, engine) }.getOrDefault(false)) {
            stopSelf()
            return
        }
        runCatching { engine.startSync() }

        val muets = runCatching { engine.salonsMuets() }.getOrDefault(emptySet())

        // an exception in the `collect` body ends the flow. it goes up to the collector,
        // `runCatching` swallows it, and the watch stops for good, without a word, leaving the
        // service running as if nothing had happened. seen in use: the first notification got
        // through, the following ones never
        //
        // so each room is handled under its own `runCatching`, and the failure is logged rather
        // than swallowed. a room that has a problem no longer silences all the others
        essayer {
            engine.serversFlow().collect {
                val alertes = runCatching { engine.alertes() }
                    .onFailure { android.util.Log.w(JOURNAL, "Lecture des alertes", it) }
                    .getOrDefault(emptyList())
                alertes.forEach { alerte ->
                    runCatching { peutEtreAnnoncer(alerte, alerte.salonId in muets) }
                        .onFailure {
                            android.util.Log.w(JOURNAL, "Annonce de ${alerte.salonId}", it)
                        }
                }
                // what is no longer unread no longer has a notification. `alertes()` only returns
                // the rooms that are waiting, and those leaving it have been read, here, on the pc,
                // or from the panel. without this pass the notification stayed displayed
                // indefinitely
                val enAttente = alertes.map { it.salonId }.toSet()
                (dejaVues.keys - enAttente).forEach { salonId ->
                    Annonceur.effacer(this, salonId)
                    dejaVues.remove(salonId)
                }
            }
        }.onFailure { android.util.Log.w(JOURNAL, "La veille s'est arrêtée", it) }
    }

    private suspend fun peutEtreAnnoncer(alerte: AlerteSalon, muet: Boolean) {
        val cle = alerte.dernierEvenement ?: return
        if (muet || alerte.aMoi) {
            // memoised anyway: bringing a room out of silence must not dump everything it missed at
            // once
            dejaVues[alerte.salonId] = cle
            return
        }
        // and only if the app is in front. the first version only looked at `salonOuvert`, and
        // leaving by the home button does not destroy the composition, so the value stayed set and
        // the last room visited never notified again. measured: message sent, service active, no
        // notification
        if (Veille.auPremierPlan && alerte.salonId == Veille.salonOuvert) {
            dejaVues[alerte.salonId] = cle
            return
        }
        if (dejaVues[alerte.salonId] == cle) return
        // marked afterwards, and never before. the other way round, setting the key then
        // announcing, loses the message as soon as the announcement fails or is cancelled: it
        // counts as already seen and will not come back. a duplicate beats a silence
        Annonceur.annoncer(this, alerte)
        dejaVues[alerte.salonId] = cle
    }

    private fun demarrerEnPremierPlan() {
        val notification = Notification.Builder(this, CANAL_VEILLE)
            .setSmallIcon(R.drawable.ic_notif_velvet)
            .setContentTitle(horsUi.connexionTitre)
            // say what it does, and not "service running": this line is permanently visible, so it
            // may as well explain why it is there
            .setContentText(horsUi.recoitEnArrierePlan)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, VelvetActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                ID_VEILLE,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING,
            )
        } else {
            startForeground(ID_VEILLE, notification)
        }
    }

    private fun notifieur() =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {

        private const val ID_VEILLE = 1

        private const val JOURNAL = "Velvet/Veille"

        /** the room to open, carried by a conversation shortcut's intent */
        const val EXTRA_SALON = "salon"

        /** the channel for ordinary messages: discreet, no sound */
        const val CANAL_MESSAGES = "messages"

        /** the mentions one: it is the only one allowed to ring */
        const val CANAL_MENTIONS = "mentions"

        /** the permanent notification's channel, at the lowest possible importance */
        const val CANAL_VEILLE = "veille"

        /**
         * the service only starts when push has not taken over
         *
         * this is the full switch: when firebase is configured and a pusher is registered, the
         * server is what wakes the device ([ServicePush]) and this service has no reason to exist,
         * so no permanent notification either, which was the whole problem
         *
         * both conditions and not only the first: a configured firebase project whose pusher
         * registration failed (offline at startup, play services missing, a server refusing) would
         * leave the device with no way of being told at all. falling back on the watch is then
         * exactly right, and it corrects itself at the next successful start
         */
        fun demarrer(contexte: Context) {
            if (Push.aPrisLeRelais(contexte)) {
                // it may have been running before push was configured
                arreter(contexte)
                return
            }
            val intent = Intent(contexte, ServiceVeille::class.java)
            if (Build.VERSION.SDK_INT >= 26) contexte.startForegroundService(intent)
            else contexte.startService(intent)
        }

        fun arreter(contexte: Context) {
            contexte.stopService(Intent(contexte, ServiceVeille::class.java))
        }

        /**
         * the three channels
         *
         * three and not one: android lets people set sound and vibration channel by channel, and
         * that is exactly the distinction a messaging client has to offer, muting ordinary messages
         * without muting mentions. a single channel would make that setting impossible, and it
         * cannot be changed after creation
         */
        fun creerCanaux(contexte: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val manager =
                contexte.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    CANAL_VEILLE,
                    horsUi.connexionArrierePlan,
                    NotificationManager.IMPORTANCE_MIN,
                ).apply { description = horsUi.canalVeilleDescription }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    CANAL_MESSAGES,
                    horsUi.mpTitre,
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    CANAL_MENTIONS,
                    horsUi.mentions,
                    NotificationManager.IMPORTANCE_HIGH,
                )
            )
        }
    }
}

/**
 * the little state the service and the screen share
 *
 * an object and not a bound service: the only thing the service needs to know about the screen is
 * which room is open, so as not to notify what you are reading. a service binding for one string
 * would be disproportionate
 */
object Veille {

    /** the room shown right now. only counts together with [auPremierPlan] */
    @Volatile
    var salonOuvert: String? = null

    /**
     * is the app visible?
     *
     * set by the activity's `onStart`/`onStop`, and not deduced from [salonOuvert]: going to the
     * background does not destroy the composition, so the room stays "open" from compose's point of
     * view while nobody is looking at it any more
     */
    @Volatile
    var auPremierPlan: Boolean = false
}

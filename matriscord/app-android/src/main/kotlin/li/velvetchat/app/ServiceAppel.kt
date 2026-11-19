package li.velvetchat.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import li.velvetchat.app.ui.horsUi

/**
 * the foreground service that keeps a call alive
 *
 * without it the call does not survive a return to the home screen: android cuts the mic capture of
 * an app gone to the background. a foreground service of type microphone is the only way of keeping
 * the mic open with the screen off, and android requires it, with a permanent notification telling
 * the person their mic is active
 *
 * it does not do the media (that is [VocalManager]): it only keeps the process in the foreground
 * and carries the "call in progress" notification and its hang up button. started when the call
 * connects, stopped when it ends
 */
class ServiceAppel : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // the notification's hang up button comes back through here: we leave and we stop
        if (intent?.action == ACTION_RACCROCHER) {
            VocalManager.quitter()
            stopSelf()
            return START_NOT_STICKY
        }

        creerCanal(this)

        val raccrocher = PendingIntent.getService(
            this, 1,
            Intent(this, ServiceAppel::class.java).setAction(ACTION_RACCROCHER),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val ouvrir = PendingIntent.getActivity(
            this, 0,
            Intent(this, VelvetActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = Notification.Builder(this, CANAL_APPEL)
            .setSmallIcon(android.R.drawable.stat_sys_speakerphone)
            .setContentTitle(horsUi.appelVocal)
            .setContentText(horsUi.appelEnCours)
            .setOngoing(true)
            .setUsesChronometer(true)
            .setContentIntent(ouvrir)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                    horsUi.raccrocher,
                    raccrocher,
                ).build()
            )
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID_APPEL, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(ID_APPEL, notification)
        }
        return START_STICKY
    }

    companion object {
        private const val ID_APPEL = 2

        /**
         * the id of the screen sharing row
         *
         * a second notification, and there is no choice. screen sharing runs in the livekit sdk's
         * service (`ScreenCaptureService`), which is the one carrying the `mediaProjection` type
         * android 14 requires, so two foreground services, so two rows. the reference only shows
         * one, and we pay here for wanting a screen share that does not mean rewriting the capture
         * ourselves
         */
        const val ID_PARTAGE = 3
        const val CANAL_APPEL = "appel"
        const val ACTION_RACCROCHER = "li.velvetchat.RACCROCHER"

        fun demarrer(contexte: Context) {
            val intent = Intent(contexte, ServiceAppel::class.java)
            if (Build.VERSION.SDK_INT >= 26) contexte.startForegroundService(intent)
            else contexte.startService(intent)
        }

        fun arreter(contexte: Context) {
            contexte.stopService(Intent(contexte, ServiceAppel::class.java))
        }

        /**
         * the row shown during a screen share
         *
         * built here and passed to the sdk, which starts its own service with it: without it, it
         * would put up a generic one carrying its own name, in the middle of ours
         *
         * it says "your screen is visible" rather than "sharing active". the nuance is not a matter
         * of style: this row is the only thing left on screen when you leave the app to show
         * something, and that is precisely the moment to remember that the others see everything
         */
        fun notificationPartage(contexte: Context): Notification {
            creerCanal(contexte)
            val ouvrir = PendingIntent.getActivity(
                contexte, 0,
                Intent(contexte, VelvetActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return Notification.Builder(contexte, CANAL_APPEL)
                // our icon, as everywhere else in the status bar
                .setSmallIcon(R.drawable.ic_notif_velvet)
                .setContentTitle(horsUi.votreEcranEstVisible)
                .setContentText(horsUi.partageAvecLesParticipants)
                .setOngoing(true)
                .setContentIntent(ouvrir)
                .build()
        }

        fun creerCanal(contexte: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val manager =
                contexte.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    CANAL_APPEL,
                    horsUi.appelEnCours,
                    // low: a call's permanent row must neither ring nor vibrate, the incoming
                    // call's ringtone doing that, on its own channel
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = horsUi.canalAppelDescription }
            )
        }
    }
}

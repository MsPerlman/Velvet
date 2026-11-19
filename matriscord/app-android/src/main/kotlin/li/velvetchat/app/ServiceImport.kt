package li.velvetchat.app

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import li.velvetchat.core.ClientImport
import li.velvetchat.app.ui.horsUi

/**
 * the discord import, followed from the notification bar
 *
 * the import runs on the nas and takes hours: staying on the screen to watch it makes no sense, and
 * closing the app must not lose track of where it is. this service polls the progress every five
 * seconds and puts it in a notification, how many messages, at what rate, and how long is left,
 * which is the only thing you really want to know
 *
 * it does nothing else: the import is not here, it is on the nas. closing the notification
 * therefore stops nothing, and that is intended, we follow, we do not run
 */
class ServiceImport : Service() {

    private val portee = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val tache = intent?.getStringExtra(EXTRA_TACHE) ?: return START_NOT_STICKY.also { stopSelf() }
        val jeton = intent.getStringExtra(EXTRA_JETON).orEmpty()
        demarrerEnPremierPlan(texte = horsUi.importLecture, faits = 0, total = 0)
        portee.launch { suivre(tache, jeton) }
        return START_STICKY
    }

    private suspend fun suivre(tache: String, jeton: String) {
        val client = ClientImport()
        var echecs = 0
        while (true) {
            val etat = runCatching { client.etat(jeton, tache) }.getOrNull()
            if (etat == null) {
                // the nas can disappear for a moment (wifi, sleep): we do not conclude that an
                // import which is in fact still going over there has failed
                if (++echecs > 60) return finir(horsUi.avancementIntrouvable)
                delay(5_000)
                continue
            }
            echecs = 0
            when {
                etat.phase == "fini" -> return finir(
                    horsUi.importTermineEvenements(etat.evenements) +
                        if (etat.mediasRates > 0) horsUi.importMediasRates(etat.mediasRates) else "",
                )
                etat.phase == "échec" -> return finir(horsUi.importInterrompuQuoi(etat.erreur ?: horsUi.importRaisonInconnue))
                etat.phase == "arrêté" -> return finir(horsUi.importArrete)
                else -> {
                    val reste = restant(etat.faits, etat.total, etat.vitesse)
                    afficher(
                        titre = horsUi.importDiscordTitre,
                        texte = horsUi.importAvancement(etat.faits, etat.total) +
                            (if (reste != null) " · $reste" else "") +
                            (if (etat.vitesse > 0) horsUi.importVitesse(etat.vitesse.toString()) else ""),
                        faits = etat.faits,
                        total = etat.total,
                    )
                }
            }
            delay(5_000)
        }
    }

    private fun finir(texte: String) {
        notifieur().notify(
            ID + 1,
            batir(titre = horsUi.importDiscordTitre, texte = texte, faits = 0, total = 0, permanente = false),
        )
        stopSelf()
    }

    private fun afficher(titre: String, texte: String, faits: Int, total: Int) {
        notifieur().notify(ID, batir(titre, texte, faits, total, permanente = true))
    }

    private fun demarrerEnPremierPlan(texte: String, faits: Int, total: Int) {
        val notification = batir(horsUi.importDiscordTitre, texte, faits, total, permanente = true)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(ID, notification)
        }
    }

    private fun batir(titre: String, texte: String, faits: Int, total: Int, permanente: Boolean): Notification =
        Notification.Builder(this, ServiceVeille.CANAL_VEILLE)
            .setSmallIcon(R.drawable.ic_notif_velvet)
            .setContentTitle(titre)
            .setContentText(texte)
            .setStyle(Notification.BigTextStyle().bigText(texte))
            .setOngoing(permanente)
            .setOnlyAlertOnce(true)
            .apply { if (total > 0) setProgress(total, faits, false) }
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, VelvetActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()

    private fun notifieur() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override fun onDestroy() {
        portee.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ID = 7
        private const val EXTRA_TACHE = "tache"
        private const val EXTRA_JETON = "jeton"

        /**
         * the time left, in plain words. `null` while nothing can be said about it, announcing "a
         * minute" on three measured messages being worse than saying nothing
         */
        fun restant(faits: Int, total: Int, vitesse: Double): String? {
            if (total <= 0 || faits <= 0 || vitesse <= 0.0) return null
            val secondes = ((total - faits) / vitesse).toLong()
            if (secondes <= 0) return null
            return when {
                secondes < 90 -> horsUi.etaSecondes(secondes)
                secondes < 5_400 -> horsUi.etaMinutes(secondes / 60)
                else -> horsUi.etaHeures(secondes / 3_600, (secondes % 3_600) / 60)
            }
        }

        fun suivre(contexte: Context, tache: String, jeton: String) {
            contexte.startService(
                Intent(contexte, ServiceImport::class.java)
                    .putExtra(EXTRA_TACHE, tache)
                    .putExtra(EXTRA_JETON, jeton),
            )
        }
    }
}

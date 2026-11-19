package li.velvetchat.app

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.content.Intent
import android.os.Build
import li.velvetchat.core.model.AlerteSalon
import li.velvetchat.app.ui.horsUi

/**
 * who puts a notification in the status bar, and nothing else. pulled out of [ServiceVeille] when
 * firebase became the main transport, against the two paths problem: there are now two ways of
 * learning a message arrived, the foreground service and the fcm wake up ([ServicePush]), and there
 * has to stay one way of displaying it
 *
 * what would have diverged, and not theoretically: the `MessagingStyle`, the conversation shortcut
 * without which android writes "Velvet" instead of the first name, the bubble, the `content://`
 * icon without which the dot shows the app logo, the two action buttons. five details, an evening
 * each to find
 *
 * an object and not a class for its three caches — the thread ([historique]), the decoded faces,
 * our own `Person`. an fcm wake up dies right after, so they will not always survive, but two
 * messages back to back save two downloads, and a stacked thread only means anything shared
 */
object Annonceur {

    /** the last messages announced per room, for `MessagingStyle`'s stacked rendering */
    private val historique =
        mutableMapOf<String, ArrayDeque<Notification.MessagingStyle.Message>>()

    /** the already decoded avatars. a face does not change between two messages */
    private val iconesEnCache = mutableMapOf<String, android.graphics.drawable.Icon>()

    private var moiEnCache: Person? = null

    /**
     * the last alert announced per room
     *
     * it serves [ajouterMaReponse], which has to rebuild the style of an already posted
     * notification: the conversation title and the "group" flag do not belong to the message
     * thread, and nothing lets them be read back from a displayed notification
     */
    private val derniereAlerte = mutableMapOf<String, AlerteSalon>()

    /** 128 px: the size the system asks for a person icon */
    private const val TAILLE_AVATAR_PX = 128

    /** the height of an unfolded bubble. a short conversation, not a second screen */
    private const val HAUTEUR_BULLE_DP = 620

    private fun notifieur(contexte: Context) =
        contexte.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * the group key. one for the whole app: what we group are the conversations with each other,
     * not the servers with each other, which is what the reference does, and it is also the only
     * way android knows how to stack `MessagingStyle`s
     */
    private const val GROUPE = "li.velvetchat.conversations"

    /** the id reserved for the summary. fixed, since there is only one */
    private const val ID_SYNTHESE = 2

    /**
     * the app's blue violet, hard coded and in `0xAARRGGBB`
     *
     * written here rather than read from the tokens: this code runs outside any compose
     * composition, a firebase wake up having neither theme nor `LocalTokens`. the value has to
     * follow `Colors.accent` if it changes, and this is the only place it is copied
     */
    private const val ACCENT = 0xFF5865F2.toInt()

    /**
     * the summary notification, the one carrying the group
     *
     * android requires it: without a notification carrying `setGroupSummary(true)` and the same
     * group key, the children stay displayed flat and the grouping does not show. it is practically
     * never shown as such, the system displaying the children and only falling back on it when
     * folded
     *
     * `GROUP_ALERT_CHILDREN`: the child is what rings, not the summary. without it every message
     * would vibrate twice, once for the conversation and once for the summary
     */
    private fun synthese(contexte: Context) {
        val notification = Notification.Builder(contexte, ServiceVeille.CANAL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notif_velvet)
            .setColor(ACCENT)
            .setGroup(GROUPE)
            .setGroupSummary(true)
            .setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    contexte,
                    0,
                    Intent(contexte, VelvetActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .build()
        notifieur(contexte).notify(ID_SYNTHESE, notification)
    }

    /**
     * the room is read: its notification goes, and its thread is forgotten
     *
     * two faults in one, reported: the notification stayed displayed after the messages had been
     * read, nothing clearing it, `setAutoCancel` only counting for a tap, and the next message
     * reopened a panel filled with the five previous messages, already read, because [historique]
     * kept them
     *
     * this counts for a read here or elsewhere: the watcher calls this for the rooms that left the
     * alerts, so reading on the pc also clears the phone's notification
     */
    fun effacer(contexte: Context, salonId: String) {
        historique.remove(salonId)
        derniereAlerte.remove(salonId)
        notifieur(contexte).cancel(salonId.hashCode())
        // the summary carries the group: with no child, it would be left alone in the panel
        if (historique.isEmpty()) notifieur(contexte).cancel(ID_SYNTHESE)
    }

    /**
     * what you have just sent from the panel appears in the panel
     *
     * without it, replying from the notification simply made it disappear: no trace of what you had
     * just written, and nothing to tell "sent" from "the button did nothing". the reference adds
     * the line to the thread and leaves the conversation open, so you can send two messages in a
     * row without opening the app
     *
     * returns `false` when there is no thread in memory for that room: the process may have been
     * recreated between the display and the reply. the caller then clears the notification, which
     * was the old behaviour and stays correct
     */
    fun ajouterMaReponse(contexte: Context, salonId: String, texte: String): Boolean {
        val fil = historique[salonId] ?: return false
        val moi = moiEnCache ?: return false
        fil.addLast(
            Notification.MessagingStyle.Message(texte, System.currentTimeMillis(), moi)
        )
        while (fil.size > 5) fil.removeFirst()

        val existante = notifieur(contexte).activeNotifications
            .firstOrNull { it.id == salonId.hashCode() }?.notification ?: return false

        // the style is rebuilt, not extracted. `extractMessagingStyleFromNotification` only exists
        // in the `androidx` version, and on the framework side you do not get a posted style back.
        // this is not a makeshift: `historique` already carries the whole thread, it is the source,
        // and reading it back from the notification would be reading back our own writing. the
        // rest, shortcut, actions, bubble, colour, is recovered as is by `recoverBuilder`, which is
        // the part we especially do not want to redo
        val alerte = derniereAlerte[salonId]
        val style = Notification.MessagingStyle(moi)
            .apply { if (alerte?.estDirect == false) conversationTitle = alerte.nom }
            .setGroupConversation(alerte?.estDirect == false)
        fil.forEach { style.addMessage(it) }

        val rebatie = Notification.Builder.recoverBuilder(contexte, existante)
            .setStyle(style)
            // silent: we have just written, we know what is happening. making the phone ring again
            // for your own reply is the kind of detail that gets an app's notifications turned off
            .setOnlyAlertOnce(true)
            .build()
        notifieur(contexte).notify(salonId.hashCode(), rebatie)
        return true
    }

    /**
     * a conversation notification, in the sense android gives the word. the first version set a
     * title and a line of text, which works and lands in the common heap of "other" notifications
     * rather than the conversations section that grants priorities, per person silence and bubbles
     *
     * what makes one a conversation is neither firebase nor the transport, but three local things:
     * a long lived shortcut (`setLongLived`) carrying a `Person` and `SHORTCUT_CATEGORY_CONVERSATION`,
     * a `setShortcutId` linking the notification to it, and the `MessagingStyle` that carries
     * messages as messages instead of a line of text
     *
     * worth stating because the question asked was whether firebase was needed for android's
     * conversation features. it is not: fcm decides how a message arrives, this decides how it is
     * shown, and we have one without the other
     *
     * the per room history ([historique]) is what feeds the stacked rendering, which a notification
     * carrying only the last message cannot do
     */
    suspend fun annoncer(contexte: Context, alerte: AlerteSalon) {
        val ouvrir = PendingIntent.getActivity(
            contexte,
            alerte.salonId.hashCode(),
            Intent(contexte, VelvetActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val texte = alerte.apercu ?: horsUi.nouveauMessage
        val visage = icone(contexte, alerte.avatarAuteur)
        val expediteur = Person.Builder()
            // the key is the matrix id, never the display name: android uses it to recognise the
            // same person from one notification to the next, and a name that changes would show two
            // correspondents where there is only one
            .setKey(alerte.auteurId ?: alerte.salonId)
            .setName(alerte.auteur ?: horsUi.quelquunMaj)
            .apply { visage?.let { setIcon(it) } }
            .build()

        derniereAlerte[alerte.salonId] = alerte
        val messages = historique.getOrPut(alerte.salonId) { ArrayDeque() }
        messages.addLast(Notification.MessagingStyle.Message(texte, alerte.horodatage, expediteur))
        // five: enough to see a conversation picking up, without turning the panel into a second
        // timeline. android only shows the last few anyway
        while (messages.size > 5) messages.removeFirst()

        val style = Notification.MessagingStyle(moi(contexte))
            // the room's name only when it is one. in a two person conversation the title would be
            // the person's name, which is already the sender's, and android then shows it twice
            .apply { if (!alerte.estDirect) conversationTitle = alerte.nom }
            .setGroupConversation(!alerte.estDirect)
        messages.forEach { style.addMessage(it) }

        // the id is computed once: the actions have to be able to remove this notification, and
        // were doing it with a hash recomputed elsewhere
        val idNotif = alerte.salonId.hashCode()
        val avatarSalon = icone(contexte, alerte.avatarSalon)
        val raccourci = raccourciDe(contexte, alerte, expediteur, avatarSalon)

        val notification = Notification.Builder(
            contexte,
            if (alerte.mentions > 0) ServiceVeille.CANAL_MENTIONS else ServiceVeille.CANAL_MESSAGES,
        )
            // our icon, not android's. `stat_notify_chat` is the system's generic bubble: in the
            // status bar, velvet was indistinguishable from any other messenger. that is the first
            // place an app is recognised, and often the only one people look at
            .setSmallIcon(R.drawable.ic_notif_velvet)
            // the app's blue violet. android puts it on the icon, on the name, and on the
            // conversation thread in the panel: it is what makes a notification look like it comes
            // from somewhere rather than from the system
            .setColor(ACCENT)
            .setStyle(style)
            .setWhen(alerte.horodatage)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(ouvrir)
            // the group and its summary. without it, five active rooms make five independent lines
            // that push everything else out of the panel. with it, android files them under a
            // single expandable entry, which is the reference's behaviour, and every modern
            // messenger's
            .setGroup(GROUPE)
            // the conversation thread, linking the notification, the shortcut and the bubble.
            // without it android treats the three as three unrelated objects: the bubble does not
            // close when you read, and the shortcut is not promoted
            .apply {
                if (Build.VERSION.SDK_INT >= 29) {
                    setLocusId(android.content.LocusId("salon:" + alerte.salonId))
                }
                // the short replies the system offers, computed on the device (no text leaves).
                // that is what puts "ok" and "thanks!" under the message, like the reference
                setAllowSystemGeneratedContextualActions(true)
            }
            // the reference's two actions. see `ActionsNotification`: a personal messenger mostly
            // receives messages you answer in three words, or acknowledge without answering. making
            // people open the app to write "ok" is what stops them answering
            .addAction(ActionsNotification.actionRepondre(contexte, alerte.salonId, idNotif))
            .addAction(ActionsNotification.actionLu(contexte, alerte.salonId, idNotif))
            .apply {
                if (alerte.nonLus > 1) setNumber(alerte.nonLus)
                // without the shortcut, everything above stays an ordinary notification: it is that
                // link, and only that link, that moves it up into "conversations"
                if (raccourci != null) {
                    setShortcutId(raccourci)
                    bulleDe(contexte, alerte, avatarSalon ?: visage)?.let { setBubbleMetadata(it) }
                }
            }
            .build()

        // one notification per room, replaced each time: ten messages in #general make one line
        // that updates, not ten lines to swipe away one by one
        notifieur(contexte).notify(idNotif, notification)
        synthese(contexte)
    }

    /**
     * the bubble, the system chat head messenger has used since android 11 instead of its own
     * overlay. we only propose: a `BubbleMetadata` makes the conversation eligible and the person
     * accepts, from the notification or the settings, conversation by conversation. a bubble that
     * imposed itself would be refused by the system, and deservedly — a window over every app is
     * not taken without consent
     *
     * `setAutoExpandBubble` and `setSuppressNotification` are deliberately unused: they only work
     * with the app already in the foreground, the one case where a bubble is no use, so setting
     * them would fake a setting that never acts
     *
     * three conditions hold silently, none of them erroring when absent — the activity declared
     * `documentLaunchMode="always"`, `allowEmbedded` and `resizeableActivity`, the notification
     * already a conversation, and the icon present. miss one and it stays ordinary without a word
     *
     * the height is a short conversation's: the last exchanges and the composer, not a screen
     *
     * and the icon must be a file. the system does not prefer a `content://`, it ignores anything
     * else: measured on a device, a bitmap icon let the app logo take the face's place on the dot.
     * hence the `FileProvider`, see `icone`
     */
    private fun bulleDe(
        contexte: Context,
        alerte: AlerteSalon,
        icone: android.graphics.drawable.Icon?,
    ): Notification.BubbleMetadata? {
        if (Build.VERSION.SDK_INT < 30) return null
        val visage = icone ?: android.graphics.drawable.Icon.createWithResource(
            contexte,
            android.R.drawable.stat_notify_chat,
        )
        val vers = PendingIntent.getActivity(
            contexte,
            // one request per room: two bubbles have to target two conversations, and a shared code
            // would make them overlap
            alerte.salonId.hashCode(),
            Intent(contexte, ActiviteBulle::class.java)
                .setAction(Intent.ACTION_VIEW)
                .putExtra(ServiceVeille.EXTRA_SALON, alerte.salonId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        return runCatching {
            Notification.BubbleMetadata.Builder(vers, visage)
                .setDesiredHeight(HAUTEUR_BULLE_DP)
                .build()
        }.getOrNull()
    }

    /** us, as android shows us in the thread. read once, the profile does not move */
    private suspend fun moi(contexte: Context): Person {
        moiEnCache?.let { return it }
        val profil = runCatching { Engine.get(contexte).profile() }.getOrNull()
        val visage = icone(contexte, profil?.avatarUrl)
        return Person.Builder()
            .setKey(profil?.userId ?: "moi")
            .setName(profil?.displayName ?: horsUi.moi)
            .apply { visage?.let { setIcon(it) } }
            .build()
            .also { moiEnCache = it }
    }

    /**
     * publishes, or refreshes, a room's conversation shortcut, and returns its id. `null` before
     * android 11, where `setLongLived` does not exist and neither does the conversations section:
     * the notification then stays ordinary, which is the correct degradation
     *
     * `pushDynamicShortcut` and not `addDynamicShortcuts`: the first makes room on its own when the
     * quota is reached, by evicting the least used shortcut. the second fails silently past the
     * quota, and the quota, for ten people plus the rooms, does fill up
     */
    private fun raccourciDe(
        contexte: Context,
        alerte: AlerteSalon,
        expediteur: Person,
        avatarSalon: android.graphics.drawable.Icon?,
    ): String? {
        if (Build.VERSION.SDK_INT < 30) return null
        val id = "salon:" + alerte.salonId
        val personne = if (alerte.estDirect) expediteur else {
            Person.Builder()
                .setKey(alerte.salonId)
                .setName(alerte.nom)
                // the face of whoever is writing, failing a room avatar
                //
                // it is the shortcut's icon, and not the `BubbleMetadata`'s, that the system shows
                // on the floating dot, found by looking at the bubble: it carried velvet's logo.
                // the cause was not the bubble but the shortcut, left with no icon because a group
                // room most often has no avatar of its own
                //
                // a bubble with no face says nothing about who is waiting, which is to say
                // everything it exists to say. failing better, the last person who wrote is worth
                // infinitely more than the app's logo
                .apply { (avatarSalon ?: expediteur.icon)?.let { setIcon(it) } }
                .build()
        }
        // an empty label gets the shortcut rejected, and so loses the "conversations" section
        // without anything saying so. a room with no name exists
        val etiquette = alerte.nom.takeIf { it.isNotBlank() }
            ?: personne.name?.toString()?.takeIf { it.isNotBlank() }
            ?: horsUi.conversation

        val raccourci = androidx.core.content.pm.ShortcutInfoCompat.Builder(contexte, id)
            .setLongLived(true)
            .setShortLabel(etiquette)
            .setCategories(setOf("android.shortcut.conversation"))
            .setPerson(
                androidx.core.app.Person.Builder()
                    .setKey(if (alerte.estDirect) alerte.auteurId ?: alerte.salonId else alerte.salonId)
                    .setName(personne.name)
                    .apply {
                        personne.icon?.let {
                            setIcon(androidx.core.graphics.drawable.IconCompat.createFromIcon(contexte, it))
                        }
                    }
                    .build()
            )
            .apply {
                personne.icon?.let {
                    androidx.core.graphics.drawable.IconCompat.createFromIcon(contexte, it)
                        ?.let { compat -> setIcon(compat) }
                }
            }
            .setIntent(
                Intent(contexte, VelvetActivity::class.java)
                    .setAction(Intent.ACTION_VIEW)
                    .putExtra(ServiceVeille.EXTRA_SALON, alerte.salonId)
            )
            .build()

        // the failure is no longer swallowed. `pushDynamicShortcut` is rate limited when the app is
        // in the background, which is to say exactly when a notification arrives, and the original
        // `runCatching` returned `null` silently. with no shortcut id the notification does not
        // enter the "conversations" section: android then shows the app's name as the title instead
        // of the person's. that is what we were seeing, "Velvet" where the reference shows the
        // first name. a fault whose cause was two layers below the symptom, and invisible by
        // construction
        return try {
            androidx.core.content.pm.ShortcutManagerCompat.pushDynamicShortcut(contexte, raccourci)
            id
        } catch (t: Throwable) {
            android.util.Log.w("Velvet", "Raccourci de conversation refusé pour ${alerte.salonId}", t)
            null
        }
    }

    /**
     * the avatar of an `mxc://`, as an adaptive icon
     *
     * `null` on failure, and quietly: a notification without a photo is still a readable
     * notification, while waiting for the network to show it would delay it. the cache avoids
     * downloading the same face again on every message
     */
    private suspend fun icone(contexte: Context, mxc: String?): android.graphics.drawable.Icon? {
        if (mxc == null) return null
        iconesEnCache[mxc]?.let { return it }
        return runCatching {
            // `suspend` and not `runBlocking`: blocking a `Dispatchers.Default` thread from a
            // coroutine already running on it is the best way to freeze the watch loop on a
            // saturated pool
            val octets = Engine.get(contexte).avatarBytes(mxc, TAILLE_AVATAR_PX)
                ?: return null

            // a file, not a bitmap, and this is not a preference
            //
            // the system logs on every bubble that bubbles work best with icons of TYPE_URI or
            // TYPE_URI_ADAPTIVE_BITMAP, and it does not merely warn: measured on a device, a bitmap
            // icon is ignored for the bubble's dot, and the app's icon takes its place. so we saw
            // velvet's logo floating on the screen instead of the face of the person writing, which
            // empties the bubble of its point, its point being precisely to say who is waiting
            //
            // the file lives in the cache, under a name derived from the `mxc` address: two
            // notifications from the same person therefore reuse the same one, and the system
            // purges the lot when room runs short
            val dossier = java.io.File(contexte.cacheDir, "avatars").apply { mkdirs() }
            val fichier = java.io.File(dossier, mxc.hashCode().toUInt().toString(16) + ".png")
            if (!fichier.exists()) {
                val bitmap = android.graphics.BitmapFactory
                    .decodeByteArray(octets, 0, octets.size) ?: return null
                java.io.FileOutputStream(fichier).use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            val uri = androidx.core.content.FileProvider.getUriForFile(
                contexte,
                "${contexte.packageName}.fichiers",
                fichier,
            )
            // `AdaptiveBitmapContentUri` since android 11: that is the one the bubble wants, and it
            // is also the version that lets the system apply its own round mask
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.drawable.Icon.createWithAdaptiveBitmapContentUri(uri)
            } else {
                android.graphics.drawable.Icon.createWithContentUri(uri)
            }.also { iconesEnCache[mxc] = it }
        }.getOrNull()
    }
}

package li.velvetchat.app

import li.velvetchat.core.tr

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CompletableDeferred

/**
 * the way back from the browser after a login on MAS
 *
 * MAS sends the browser to `li.velvet-chat:/callback?code=…`, android hands that url to this
 * activity, which passes it to the waiting login and brings velvet back to the front. a separate
 * activity rather than a filter on [VelvetActivity], whose launch mode the notifications rely on
 */
class RetourOauth : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.toString()?.let { attente?.complete(it) }
        startActivity(
            Intent(this, VelvetActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                ),
        )
        finish()
    }

    companion object {
        /** the scheme is the client_uri's host reversed, a rule of MAS, see `CLIENT_URI` */
        const val ADRESSE = "li.velvet-chat:/callback"

        @Volatile
        private var attente: CompletableDeferred<String>? = null

        /** opens [url] in the browser and suspends until the callback comes back */
        suspend fun ouvrir(context: Context, url: String): String {
            val d = CompletableDeferred<String>()
            attente?.cancel()
            attente = d
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return d.await()
        }

        /**
         * velvet came back to the front. if no callback arrived with it, the person closed the
         * browser: the login gives up instead of waiting forever behind a greyed button. the
         * delay lets this activity, which brings velvet back, deliver first
         */
        fun auRetour() {
            val d = attente ?: return
            Handler(Looper.getMainLooper()).postDelayed({
                if (d.isActive) d.completeExceptionally(IllegalStateException(tr("connexion abandonnée dans le navigateur", "sign-in abandoned in the browser")))
            }, 1_500)
        }
    }
}

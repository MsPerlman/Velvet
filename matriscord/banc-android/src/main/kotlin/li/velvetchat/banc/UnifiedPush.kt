package li.velvetchat.banc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

object UnifiedPush {

    const val ACTION_REGISTER = "org.unifiedpush.android.distributor.REGISTER"
    const val ACTION_UNREGISTER = "org.unifiedpush.android.distributor.UNREGISTER"
    const val ACTION_NEW_ENDPOINT = "org.unifiedpush.android.connector.NEW_ENDPOINT"
    const val ACTION_MESSAGE = "org.unifiedpush.android.connector.MESSAGE"
    const val ACTION_REGISTRATION_FAILED = "org.unifiedpush.android.connector.REGISTRATION_FAILED"
    const val ACTION_UNREGISTERED = "org.unifiedpush.android.connector.UNREGISTERED"

    const val TOKEN = "matriscord-s05"

    fun distributors(context: Context): List<String> =
        context.packageManager
            .queryBroadcastReceivers(Intent(ACTION_REGISTER), 0)
            .mapNotNull { it.activityInfo?.packageName }
            .distinct()

    fun register(context: Context, distributor: String) {
        val intent = Intent(ACTION_REGISTER).apply {
            `package` = distributor
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            putExtra("token", TOKEN)
            putExtra("application", context.packageName)
        }
        context.sendBroadcast(intent)
        Log.i(TAG, "Demande d'enregistrement envoyée à $distributor")
    }

    fun unregister(context: Context, distributor: String) {
        val intent = Intent(ACTION_UNREGISTER).apply {
            `package` = distributor
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            putExtra("token", TOKEN)
        }
        context.sendBroadcast(intent)
        Log.i(TAG, "Désinscription envoyée à $distributor")
    }

    const val TAG = "MatriscordS05"
}

class UnifiedPushReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            UnifiedPush.ACTION_NEW_ENDPOINT -> {
                val endpoint = intent.getStringExtra("endpoint").orEmpty()
                Log.i(UnifiedPush.TAG, "① Endpoint UnifiedPush reçu : $endpoint")
                S05State.saveEndpoint(context, endpoint)
            }

            UnifiedPush.ACTION_MESSAGE -> {
                val body = intent.getByteArrayExtra("bytesMessage")?.decodeToString()
                    ?: intent.getStringExtra("message").orEmpty()
                Log.i(UnifiedPush.TAG, "② PUSH REÇU (application fermée si Doze) : $body")
                NotificationDecryptWorker.enqueue(context, body, goAsync())
            }

            UnifiedPush.ACTION_REGISTRATION_FAILED ->
                Log.e(UnifiedPush.TAG, "✗ Enregistrement UnifiedPush refusé par le distributeur")

            UnifiedPush.ACTION_UNREGISTERED ->
                Log.w(UnifiedPush.TAG, "Désenregistré du distributeur")
        }
    }
}

object S05State {
    private const val PREFS = "s05_spike"
    private const val KEY_ENDPOINT = "endpoint"

    fun saveEndpoint(context: Context, endpoint: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ENDPOINT, endpoint).apply()
    }

    fun endpoint(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ENDPOINT, null)

    fun clearEndpoint(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_ENDPOINT).apply()
    }
}

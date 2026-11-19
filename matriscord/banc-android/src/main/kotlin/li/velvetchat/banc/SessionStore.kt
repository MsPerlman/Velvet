package li.velvetchat.banc

import android.content.Context
import org.json.JSONObject
import org.matrix.rustcomponents.sdk.Session
import org.matrix.rustcomponents.sdk.SlidingSyncVersion

class SessionStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(session: Session) {
        val json = JSONObject().apply {
            put("accessToken", session.accessToken)
            put("refreshToken", session.refreshToken)
            put("userId", session.userId)
            put("deviceId", session.deviceId)
            put("homeserverUrl", session.homeserverUrl)
            put("oauthData", session.oauthData)
            put("slidingSyncVersion", session.slidingSyncVersion.name)
        }
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    fun load(): Session? {
        val raw = prefs.getString(KEY, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            Session(
                accessToken = json.getString("accessToken"),
                refreshToken = json.optNullableString("refreshToken"),
                userId = json.getString("userId"),
                deviceId = json.getString("deviceId"),
                homeserverUrl = json.getString("homeserverUrl"),
                oauthData = json.optNullableString("oauthData"),
                slidingSyncVersion = SlidingSyncVersion.valueOf(
                    json.getString("slidingSyncVersion")
                ),
            )
        }.getOrNull()
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private fun JSONObject.optNullableString(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }

    private companion object {
        const val PREFS = "s01_spike_session"
        const val KEY = "session"
    }
}

package li.velvetchat.core.model

/**
 * what you need to join a call's media server
 *
 * obtained through [li.velvetchat.core.MatrixEngine.jetonVocal]: the engine trades an openid
 * token from the account for a livekit token from `lk-jwt-service`, which checks on the way
 * through that the person is in the room. without that token you join no media room, and that is
 * the real access barrier to voice
 *
 * deliberately minimal. the media connection itself, mic and speaker and ice, belongs to the
 * livekit sdk on the app side and not to the engine. the engine only hands out the pass
 *
 * @param url the sfu's websocket address (`wss://…`), as the token service returns it
 * @param jwt the short lived livekit token, tied to this room and this account
 */
data class InfoVocal(
    val url: String,
    val jwt: String,
)

/**
 * a media key somebody in the call sent, matrixrtc's way (`io.element.call.encryption_keys`)
 *
 * the call's sound and video are encrypted end to end, each sender with their own key, which
 * travels as an encrypted room event. element call does the same, so the two hear each other
 *
 * @param identite the livekit identity it belongs to, `@person:server:DEVICE`
 * @param index the key's slot: a sender moving to a new key bumps it
 * @param cle the raw key, 16 bytes, from which livekit derives the frame key (hkdf)
 */
class CleAppel(val identite: String, val index: Int, val cle: ByteArray)

/**
 * a call happening in a room, as its organiser announces it
 *
 * the ringing, in data form. somebody starting a call writes a state marker
 * (`li.velvetchat.call`) in the room, and everyone else reads it and offers to join. the
 * matrix sdk can read matrixrtc membership but not write it, so we write our own marker, which
 * is enough at this scale entirely on velvet
 *
 * @param appelant who started it, so we do not offer you the chance to join your own call
 * @param horodatage when the marker was written, in ms. too old and it gets ignored, since an
 *   app killed outright can leave a call "in progress" that is not
 */
data class AppelEnCours(
    val salonId: String,
    val appelant: String,
    val horodatage: Long,
)

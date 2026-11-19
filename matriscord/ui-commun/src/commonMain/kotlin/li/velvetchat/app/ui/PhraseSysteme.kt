package li.velvetchat.app.ui

import li.velvetchat.core.model.MessageBody
import li.velvetchat.core.model.SystemKind

// moved out of `Shell.kt`: the desktop was showing "System" for want of being able to read it
/**
 * how a system event gets worded
 *
 * an enum name (`ROOM_NAME_CHANGED`) is not a sentence. the engine types the event, and it is up to
 * the interface to say it in words, and this is the right level, because the wording depends on the
 * language and the product's tone, not on the protocol
 */
fun MessageBody.System.phrase(): String {
    val qui = actorDisplayName ?: actor.substringAfter('@').substringBefore(':')
    val cible = targetDisplayName ?: target?.substringAfter('@')?.substringBefore(':')
    val t = horsUi
    val vise = cible ?: t.sysQuelquun
    return when (kind) {
        SystemKind.JOINED -> t.sysRejoint(qui)
        SystemKind.LEFT -> t.sysParti(qui)
        SystemKind.INVITED -> t.sysInvite(qui, vise)
        SystemKind.INVITE_ACCEPTED -> t.sysInvitationAcceptee(qui)
        SystemKind.INVITE_REJECTED -> t.sysInvitationRefusee(qui)
        SystemKind.INVITE_REVOKED -> t.sysInvitationRetiree(qui, vise)
        SystemKind.KICKED -> t.sysExclu(qui, vise)
        SystemKind.BANNED -> t.sysBanni(qui, vise)
        SystemKind.UNBANNED -> t.sysDebanni(qui, vise)
        SystemKind.KNOCKED -> t.sysDemandeEntrer(qui)
        SystemKind.ROOM_NAME_CHANGED -> t.sysSalonRenomme(qui, detail)
        SystemKind.ROOM_TOPIC_CHANGED -> t.sysSujetChange(qui)
        SystemKind.ENCRYPTION_ENABLED -> t.sysChiffrementActif
        SystemKind.ROOM_UPGRADED -> t.sysSalonRemplace
        SystemKind.CALL -> t.sysAppelLance(qui)
        // the remaining cases are classed as noise and filtered before they get here
        else -> "$qui · ${kind.name.lowercase()}"
    }
}

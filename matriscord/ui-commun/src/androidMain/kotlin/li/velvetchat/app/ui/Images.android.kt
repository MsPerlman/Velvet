package li.velvetchat.app.ui

/** the platform's lock. see the common declaration for why these three lines exist */
internal actual class Verrou {
    actual fun <R> tenir(bloc: () -> R): R = synchronized(this) { bloc() }
}

internal actual val filAttente: kotlinx.coroutines.CoroutineDispatcher =
    kotlinx.coroutines.Dispatchers.IO

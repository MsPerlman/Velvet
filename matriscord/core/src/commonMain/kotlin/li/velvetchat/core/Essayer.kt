package li.velvetchat.core

import kotlinx.coroutines.CancellationException

/**
 * `runCatching`, minus the one mistake it makes inside a coroutine
 *
 * ## what is wrong with `runCatching`
 *
 * it catches [Throwable], and `CancellationException` is a `Throwable`. so a `runCatching` around
 * anything suspending also swallows the signal that says "stop, your caller is gone". the block
 * then hands back a `Result.failure` that looks like an ordinary error, the coroutine carries on
 * to whatever follows, and the cancellation it was told about is lost
 *
 * that is not a style preference. structured concurrency is built on that exception travelling
 * back out: a parent cancels a child by throwing it, and a child that eats it stops being
 * cancellable. the damage is usually invisible, which is why it survives review
 *
 * ## what this does instead
 *
 * the same thing for every real failure, and it lets cancellation through untouched. so
 *
 *     essayer { flux.collect { ... } }.onFailure { journaliser(it) }
 *
 * reports a server error and stays silent when the screen simply closed, which is the difference
 * a swallowed `CancellationException` erases
 *
 * ## why `suspend` and not `inline`
 *
 * inline was the first shape and it did not build. `app-android` compiles to jvm target 17 while
 * `core` compiles to 21, so kotlin refuses to inline this module's bytecode into the app's. that
 * is a standing property of the build, not a detail of this file: no inline function declared
 * here can be called from the android app until the two targets agree
 *
 * suspend rather than a plain lambda because the point is wrapping suspending work, and a
 * non-inline `() -> T` cannot contain a suspension point. every caller is already in a coroutine,
 * which is where the bug being fixed lives anyway. around code that does not suspend,
 * `runCatching` remains correct and is what to use
 */
suspend fun <T> essayer(bloc: suspend () -> T): Result<T> =
    try {
        Result.success(bloc())
    } catch (annulation: CancellationException) {
        // always back out. the caller is being cancelled and has to learn it
        throw annulation
    } catch (t: Throwable) {
        Result.failure(t)
    }

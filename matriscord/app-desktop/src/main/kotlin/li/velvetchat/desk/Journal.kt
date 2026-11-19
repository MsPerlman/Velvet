package li.velvetchat.desk

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * says a background flow died, once, in one place
 *
 * the desktop has no error banner of its own, and the alternative was inlining the same
 * `System.err.println` at every collector, which is how a message ends up worded eight
 * different ways. a line on stderr is not a user facing report, but it turns an invisible
 * failure into a findable one, which is the whole distance being covered here
 */
internal fun grogner(quoi: String, t: Throwable) {
    noter("velvet: " + quoi + " - " + (t.message ?: t.toString()))
}

/**
 * says something happened, without anything having gone wrong
 *
 * added the evening the rich presence relay misbehaved, after an hour spent guessing at a
 * program whose only output was buffered inside gradle until it exited. a running app that
 * cannot tell you what it is doing is a program you debug by changing it and restarting it,
 * which is exactly as slow as it sounds
 *
 * deliberately not a logging framework. one line, one file, no levels, no appenders, no xml
 */
internal fun murmurer(quoi: String) {
    noter("velvet: $quoi")
}

/** where the lines go, besides stderr */
internal val fichierJournal: File by lazy {
    val base = System.getenv("LOCALAPPDATA")?.let { File(it) } ?: File(System.getProperty("java.io.tmpdir"))
    File(base, "Velvet").apply { runCatching { mkdirs() } }.let { File(it, "velvet.log") }
}

private val horloge = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

private fun noter(ligne: String) {
    val horodatee = LocalDateTime.now().format(horloge) + " " + ligne
    System.err.println(horodatee)
    // best effort and synchronised: several transports write from their own threads, and a log
    // with half a line of one message inside another is worse than no log
    synchronized(horloge) {
        runCatching {
            // a log that grows forever is a log nobody opens. a megabyte is a lot of lines
            if (fichierJournal.length() > 1_000_000) fichierJournal.writeText("")
            fichierJournal.appendText(horodatee + System.lineSeparator())
        }
    }
}

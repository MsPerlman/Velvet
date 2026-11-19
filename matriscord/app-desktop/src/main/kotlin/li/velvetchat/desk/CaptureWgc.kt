package li.velvetchat.desk

import java.io.File

/**
 * capturing a window the way obs does it, through windows graphics capture
 *
 * sharing a window used to mean cutting the piece of desktop it sits on, so whatever passed in
 * front went out on the call: a notification, a browser you alt tabbed to, the person you are
 * talking to. and a maximised window reaches eight points past each screen edge, its invisible
 * resize borders, which `gdigrab` refuses outright — "capture area extends outside window area" —
 * so the picture was black and the share never started, on the most common way there is to share
 *
 * wgc asks the compositor for the window's own surface. it is what obs uses, and it works on a
 * covered window, a minimised one, and one drawn by the gpu, which is nearly all of them. ffmpeg
 * does not expose it, being a winrt api the jvm cannot call without native code of our own
 *
 * gstreamer does, in `d3d11screencapturesrc capture-api=wgc`, reached the way we already reach
 * ffmpeg: a process writing raw `BGRA` on its standard output, exactly what the sending path
 * wants. no binding, no jni, no native library — one more executable to find on disk
 *
 * absent, we degrade instead of breaking, falling back on the desktop region, occluders included.
 * [disponible] is what the picker reads to say which of the two it will use
 *
 * one thing to know: wgc delivers a frame when the window redraws, not on a clock, so a still
 * window gives one frame and then nothing whatever framerate you ask — measured both ways. that is
 * right for a share, a still screen should cost no bandwidth, and the repeat a late joiner needs
 * happens where it blocks nobody, in `PartageEcranBureau`
 *
 * `videorate` was here for exactly that and had to come out: it holds a buffer back until the next
 * one says how long the first lasted, so on a still window it swallowed the only frame there was
 */
internal object CaptureWgc {

    /**
     * `gst-launch-1.0.exe`, if it is there
     *
     * the per user directory comes first and it is not an afterthought: gstreamer's own installer
     * lands in `%LOCALAPPDATA%\Programs` when it cannot elevate, which is the case on a machine
     * whose owner is not an administrator, so on most of the machines this app is for
     */
    /**
     * the copy that travels with the application
     *
     * 28 files, 14 megabytes, the subset the four pipelines below actually load. it comes first
     * on purpose: whoever installs velvet gets window sharing without installing anything else,
     * and we know exactly which version they are running rather than whatever happened to be on
     * the machine
     */
    private val embarque: File? by lazy {
        System.getProperty("compose.application.resources.dir")
            ?.let { File(it, "gstreamer") }
            ?.takeIf { File(it, "bin/gst-launch-1.0.exe").isFile }
    }

    val executable: File? by lazy {
        val candidats = buildList {
            embarque?.let { add(File(it, "bin/gst-launch-1.0.exe")) }
            System.getenv("GSTREAMER_1_0_ROOT_MSVC_X86_64")?.let {
                add(File(it, "bin/gst-launch-1.0.exe"))
            }
            System.getenv("GSTREAMER_1_0_ROOT_MINGW_X86_64")?.let {
                add(File(it, "bin/gst-launch-1.0.exe"))
            }
            System.getenv("LOCALAPPDATA")?.let {
                add(File(it, "Programs/gstreamer/1.0/msvc_x86_64/bin/gst-launch-1.0.exe"))
                add(File(it, "Programs/gstreamer/1.0/mingw_x86_64/bin/gst-launch-1.0.exe"))
            }
            add(File("C:/gstreamer/1.0/msvc_x86_64/bin/gst-launch-1.0.exe"))
            add(File("C:/gstreamer/1.0/mingw_x86_64/bin/gst-launch-1.0.exe"))
            System.getenv("PATH").orEmpty().split(';')
                .filter { it.isNotBlank() }
                .forEach { add(File(it, "gst-launch-1.0.exe")) }
        }
        candidats.firstOrNull { it.isFile }
    }

    val disponible: Boolean get() = executable != null

    /**
     * a process builder that will find the plugins we shipped, and only those
     *
     * without this, the embedded `gst-launch` looks for its plugins where the machine's own
     * gstreamer would be, and shares the registry cache with it. on a machine that has both, the
     * cache decides which wins, and it decides differently depending on what ran last. so the
     * three variables are not belt and braces, they are what makes the shipped copy the one that
     * runs
     *
     * an empty system path is deliberate: a plugin we did not ship is a plugin we did not test
     *
     * outside the embedded copy we change nothing, because an installation found on the machine
     * knows its own layout better than we do
     */
    fun processus(commande: List<String>): ProcessBuilder {
        val b = ProcessBuilder(commande)
        val racine = embarque ?: return b
        if (commande.firstOrNull() != File(racine, "bin/gst-launch-1.0.exe").absolutePath) return b
        b.environment().apply {
            put("GST_PLUGIN_PATH", File(racine, "lib/gstreamer-1.0").absolutePath)
            put("GST_PLUGIN_SYSTEM_PATH", "")
            // our own registry, beside the app's data, so a rebuilt cache never fights with the
            // one a system installation keeps
            put("GST_REGISTRY", File(Donnees.cache, "gstreamer-registre.bin").absolutePath)
        }
        return b
    }

    /**
     * the source element, as one argument per token
     *
     * `window-capture-mode=client` gives the window's inside, without its title bar or its
     * shadow. that is what you mean when you say "share this window", and it also avoids the
     * transparent margin a desktop crop used to include
     */
    private fun element(source: SourceVideo): List<String>? = when (source) {
        is SourceVideo.Fenetre -> listOf(
            "d3d11screencapturesrc",
            "capture-api=wgc",
            "window-handle=${source.poignee}",
            "window-capture-mode=client",
            "show-cursor=true",
            "show-border=false",
            "do-timestamp=true",
        )
        is SourceVideo.Ecran -> listOf(
            "d3d11screencapturesrc",
            "capture-api=wgc",
            "monitor-index=${source.index}",
            "show-cursor=true",
            "show-border=false",
            "do-timestamp=true",
        )
        // a camera or a film is ffmpeg's business, it always was
        else -> null
    }

    /**
     * the whole command: a window or a screen, out as raw `BGRA` on standard output
     *
     * `videorate` before the fixed rate, `add-borders` on the scaler so a window whose shape is
     * not the track's shape arrives whole rather than stretched, and `sync=false` on the sink
     * because a live capture is paced by what it captures, not by a clock we would then wait on
     */
    fun commande(source: SourceVideo, largeur: Int, hauteur: Int, images: Int): List<String>? {
        val exe = executable ?: return null
        val src = element(source) ?: return null
        return buildList {
            add(exe.absolutePath)
            add("-q")
            addAll(src)
            addAll(listOf("!", "d3d11convert", "!", "d3d11download"))
            addAll(listOf("!", "videoconvert", "!", "videoscale", "add-borders=true"))
            // a ceiling, never a filler. `drop-only` is the whole point: plain `videorate` holds
            // a buffer back until the next one dates it, which on a still window means holding
            // the only frame there is. this way a 144 hz window comes down to the preset's rate
            // and a window that never moves still gets its first frame out
            addAll(listOf("!", "videorate", "max-rate=$images", "drop-only=true"))
            add("!")
            add("video/x-raw,format=BGRA,width=$largeur,height=$hauteur")
            addAll(listOf("!", "fdsink", "fd=1", "sync=false"))
        }
    }

    /**
     * the machine's sound, or one application's, as raw `S16LE` on standard output
     *
     * windows can record per process since 10 20H1, by process tree, and gstreamer exposes it as
     * `wasapi2src loopback-mode`. it is the same api obs uses for its application audio source
     *
     * [SonDuPartage.FENETRE] records the shared window's process and nothing else, so the call
     * does not pick up your music. [SonDuPartage.SYSTEME] records everything *except* velvet,
     * and that exclusion is not an optimisation: recording the machine's output while velvet is
     * playing the call into it sends the call back into the call
     *
     * a loopback keeps emitting silence when nothing plays, unlike the video side, so the track
     * has a steady clock with no help from us
     */
    fun commandeSon(mode: SonDuPartage, pidCible: Long, taux: Int, canaux: Int): List<String>? {
        val exe = executable ?: return null
        val loopback = when (mode) {
            SonDuPartage.FENETRE -> "include-process-tree"
            SonDuPartage.SYSTEME -> "exclude-process-tree"
            SonDuPartage.AUCUN -> return null
        }
        return buildList {
            add(exe.absolutePath)
            add("-q")
            addAll(
                listOf(
                    "wasapi2src",
                    "loopback=true",
                    "loopback-mode=$loopback",
                    "loopback-target-pid=$pidCible",
                    "low-latency=true",
                    "do-timestamp=true",
                ),
            )
            addAll(listOf("!", "audioconvert", "!", "audioresample"))
            add("!")
            add("audio/x-raw,format=S16LE,rate=$taux,channels=$canaux,layout=interleaved")
            addAll(listOf("!", "fdsink", "fd=1", "sync=false"))
        }
    }

    /**
     * one frame as png, for a thumbnail
     *
     * short lived on purpose: wgc hands over the current content at once, even for a window that
     * has not redrawn in an hour, so the process starts, writes its image and exits. that is what
     * makes it usable for a grid of previews, where the old ffmpeg route waited six seconds per
     * window and gave up
     *
     * null on anything unexpected, and the caller then falls back on the desktop crop. a preview
     * is worth having wrong rather than not at all
     */
    fun uneImage(source: SourceVideo, largeur: Int): ByteArray? {
        val exe = executable ?: return null
        val src = element(source) ?: return null
        val commande = buildList {
            add(exe.absolutePath)
            add("-q")
            addAll(src)
            add("num-buffers=1")
            addAll(listOf("!", "d3d11convert", "!", "d3d11download"))
            // scale first, then convert, and `RGBA` and not `BGRA`: the png encoder refuses
            // `BGRA` outright, "pngenc can't handle caps", and the pipeline then failed to link
            // at all. it cost a grid of desktop crops that looked almost right, which is the
            // worst kind of wrong
            addAll(listOf("!", "videoscale", "add-borders=true", "!", "videoconvert"))
            add("!")
            add("video/x-raw,format=RGBA,width=$largeur,height=${largeur * 9 / 16}")
            addAll(listOf("!", "pngenc", "!", "fdsink", "fd=1", "sync=false"))
        }
        return runCatching {
            val p = ProcessBuilder(commande)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            val octets = p.inputStream.readBytes()
            // a window that closed while we asked would leave the pipeline waiting for a frame
            // that will never come. three seconds is far more than the few hundred milliseconds
            // this normally takes
            if (!p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) p.destroyForcibly()
            octets.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }
}

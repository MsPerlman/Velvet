package li.velvetchat.desk

import java.io.File

/**
 * what you can send into a call: a screen, a video input, or a file
 *
 * the jdk has no camera api, and livekit's native core opens none either, it only takes frames you
 * push. "what does obs build on" has a precise answer on windows — directshow, a com api — and
 * ffmpeg is a complete client of it (`-f dshow`), already installed on this machine
 *
 * going through it settles all three requests at once: any directshow webcam, obs' virtual camera,
 * which is a directshow input like any other, so everything obs can compose arrives here with no
 * extra line, and a film, ffmpeg reading a file as happily as a device
 *
 * with no java dependency: we launch `ffmpeg.exe` and ask for `rawvideo` in `bgra` on standard
 * output, exactly what the sending path wants. a jvm webcam library would do less, with one more
 * native driver to maintain
 *
 * missing, we degrade without breaking — screens go through `java.awt.Robot` and need nothing, only
 * the other two families drop out of the list
 */
internal sealed interface SourceVideo {
    val libelle: String

    /** a screen, captured by the jdk. [index] is `GraphicsEnvironment`'s */
    data class Ecran(val index: Int, override val libelle: String) : SourceVideo

    /** a directshow input: webcam, capture card, obs virtual camera */
    data class Entree(val nom: String) : SourceVideo {
        override val libelle: String get() = nom
    }

    /**
     * a window, taken by its handle
     *
     * the handle and not the title. a title is not an identity: a browser renames its window on
     * every tab change, so a share started on one page looked for a window by that name a second
     * later, found none, and stopped
     */
    data class Fenetre(
        val poignee: Long,
        /** the process behind it, which is what its sound comes out of */
        val processus: Long,
        override val libelle: String,
    ) : SourceVideo

    /** a video file, played at its real speed */
    data class Fichier(val chemin: String) : SourceVideo {
        override val libelle: String get() = File(chemin).name
    }
}

/**
 * the quality of what you send
 *
 * presets rather than two sliders: nobody picks "1152 points wide", they pick readable or smooth.
 * the values are the reference's, minus 4k, which only serves if everybody has fibre, and the house
 * link does not
 */
internal enum class QualitePartage(
    val largeur: Int,
    val images: Int,
    /**
     * what the encoder is allowed to spend, in bits per second
     *
     * this is the field that was missing, and its absence is why the four buttons did nothing
     * visible. nothing set `videoEncoding` on the published track, so webrtc fell back on its own
     * default for every one of them: changing the preset changed the size we captured, and the
     * encoder then squeezed all four down to the same rate. you moved a slider that was not
     * connected
     */
    val debit: Long,
    /**
     * what gives way when the link cannot keep up
     *
     * and it is exactly what the preset names promise. the readable presets keep the resolution
     * and let the frame rate drop, because a sharp screenshot of text beats a smooth blur. the
     * smooth ones do the opposite. leaving it unset gave `MAINTAIN_RESOLUTION` to everybody,
     * which is how "smooth" came out stuttering
     */
    val degradation: livekit.proto.Room.DegradationPreference,
) {
    BASSE(1280, 15, 1_200_000, livekit.proto.Room.DegradationPreference.DEGRADATION_PREFERENCE_MAINTAIN_RESOLUTION),
    NORMALE(1600, 15, 2_500_000, livekit.proto.Room.DegradationPreference.DEGRADATION_PREFERENCE_MAINTAIN_RESOLUTION),
    FLUIDE(1600, 30, 4_000_000, livekit.proto.Room.DegradationPreference.DEGRADATION_PREFERENCE_MAINTAIN_FRAMERATE),
    NETTE(1920, 30, 6_000_000, livekit.proto.Room.DegradationPreference.DEGRADATION_PREFERENCE_MAINTAIN_FRAMERATE),
    ;

    val libelle: String get() = "${largeur * 9 / 16}p · ${images}fps"
}

/**
 * whose sound goes out with the picture
 *
 * what was there before offered the directshow inputs, which is to say the microphones. sharing
 * your microphone as "the sound of the share" makes no sense, it already goes out on the voice
 * track, and picking it sent your voice twice. the two answers anybody actually wants are the
 * sound of the thing being shown, and the sound of the machine
 *
 * [SYSTEME] is everything except velvet, and the exception is the whole point: taking the machine's
 * output while velvet is playing the call into it would send the call back into the call
 */
internal enum class SonDuPartage {
    AUCUN,
    FENETRE,
    SYSTEME,
}

internal object SourcesVideo {

    /**
     * `ffmpeg.exe`, if it is there
     *
     * the `PATH` first, then the two places it lands on windows. null when it is nowhere, and the
     * list then comes down to the screens, which is still usable
     */
    val ffmpeg: File? by lazy {
        val candidats = buildList {
            System.getenv("PATH").orEmpty().split(';')
                .filter { it.isNotBlank() }
                .forEach { add(File(it, "ffmpeg.exe")) }
            add(File("C:/Program Files/ffmpeg/bin/ffmpeg.exe"))
            add(File("C:/ffmpeg/bin/ffmpeg.exe"))
            System.getenv("LOCALAPPDATA")?.let {
                add(File(it, "Microsoft/WinGet/Links/ffmpeg.exe"))
            }
        }
        candidats.firstOrNull { it.isFile }
    }

    /** the screens, always available, since they do not go through ffmpeg */
    fun ecrans(): List<SourceVideo.Ecran> = runCatching {
        java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
            .mapIndexed { i, appareil ->
                val b = appareil.defaultConfiguration.bounds
                SourceVideo.Ecran(i, "${li.velvetchat.app.ui.horsUi.ecran} ${i + 1} — ${b.width}×${b.height}")
            }
    }.getOrDefault(emptyList())

    /**
     * the video inputs directshow declares
     *
     * ffmpeg writes them on standard error and exits with a failure, which is how `-list_devices`
     * works rather than a fault, hence the ignored exit code
     */
    fun entrees(): List<SourceVideo.Entree> {
        val exe = ffmpeg ?: return emptyList()
        return runCatching {
            val p = ProcessBuilder(
                exe.absolutePath, "-hide_banner", "-list_devices", "true",
                "-f", "dshow", "-i", "dummy",
            ).redirectErrorStream(true).start()
            val sortie = p.inputStream.bufferedReader().readText()
            p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
            MOTIF_ENTREE.findAll(sortie)
                .map { SourceVideo.Entree(it.groupValues[1]) }
                .distinct()
                .toList()
        }.getOrDefault(emptyList())
    }

    /** `[dshow @ …] "Device name" (video)`, and only the video inputs interest us */
    private val MOTIF_ENTREE = Regex("\"([^\"]+)\"\\s+\\(video\\)")

    /** the shareable windows. empty outside windows, or when `user32` does not answer */
    fun fenetres(): List<SourceVideo.Fenetre> =
        FenetresWindows.ouvertes().map {
            SourceVideo.Fenetre(it.poignee, it.processus, it.titre)
        }

    /**
     * where a source sits on the desktop, or null when it is not a desktop region
     *
     * one place for it, because three callers need the same answer: the thumbnail, the capture
     * arguments, and the size of the published track
     */
    fun cadre(source: SourceVideo): java.awt.Rectangle? = when (source) {
        is SourceVideo.Ecran -> runCatching {
            val b = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                .screenDevices[source.index].defaultConfiguration.bounds
            FenetresWindows.dansLeBureau(b)
        }.getOrNull()
        is SourceVideo.Fenetre -> FenetresWindows.cadreDe(source.poignee)
        else -> null
    }

    /**
     * a region of the screen, with the cursor
     *
     * `gdigrab -i title=` returned a black image: gdi cannot read a window rendered by the gpu,
     * which is nearly all of them today, measured here on an open window. obs does not use it
     * either, it goes through windows graphics capture, a winrt api ffmpeg does not expose and the
     * jvm cannot call without native code
     *
     * what does work, and what this project can do: take the region of the desktop where the window
     * sits. the trade is clear and worth saying, a covered window will show whatever passes in
     * front. a correct picture 95% of the time beats a black rectangle 100% of the time
     *
     * `-draw_mouse 1`: the cursor is part of what you show. without it you share a screen where
     * nobody sees what you are pointing at
     */
    private fun region(cadre: java.awt.Rectangle, images: Int) = listOf(
        "-f", "gdigrab",
        "-framerate", images.toString(),
        "-draw_mouse", "1",
        // nothing to buffer on a live capture: ffmpeg's job here is to hand over the frame it
        // just took, not to smooth a stream out
        "-fflags", "nobuffer",
        "-flags", "low_delay",
        "-offset_x", cadre.x.toString(),
        "-offset_y", cadre.y.toString(),
        "-video_size", "${cadre.width}x${cadre.height}",
        "-i", "desktop",
    )

    /**
     * the whole command that will write raw `BGRA` frames on its standard output
     *
     * one seam, two backends. a window or a screen goes through windows graphics capture when
     * gstreamer is installed, which gets the window's own surface rather than the piece of
     * desktop it sits on, so an occluder no longer ends up on the call and a maximised window no
     * longer fails outright. without gstreamer we fall back on the desktop crop, which is what
     * the app always did
     *
     * a camera or a film never had that problem and stays with ffmpeg
     */
    fun commandeCapture(
        source: SourceVideo,
        largeur: Int,
        hauteur: Int,
        imagesParSeconde: Int,
    ): List<String>? {
        CaptureWgc.commande(source, largeur, hauteur, imagesParSeconde)?.let { return it }
        val exe = ffmpeg ?: return null
        val entree = arguments(source, imagesParSeconde) ?: return null
        return buildList {
            add(exe.absolutePath)
            addAll(listOf("-hide_banner", "-loglevel", "error"))
            addAll(entree)
            // scaled and centred on black: a source whose shape is not the track's has to keep
            // its proportions rather than be stretched
            addAll(
                listOf(
                    "-vf",
                    "scale=$largeur:$hauteur:force_original_aspect_ratio=decrease," +
                        "pad=$largeur:$hauteur:(ow-iw)/2:(oh-ih)/2",
                ),
            )
            // passthrough: without it ffmpeg conforms the stream to a constant rate, duplicating
            // a frame when the screen did not move and holding one back when it moved twice. on a
            // live capture that is a queue, which is to say delay, for pictures nobody needed
            addAll(listOf("-fps_mode", "passthrough"))
            addAll(listOf("-f", "rawvideo", "-pix_fmt", "bgra", "-"))
        }
    }

    /** the capture arguments for a source that goes through ffmpeg, or null */
    fun arguments(source: SourceVideo, imagesParSeconde: Int): List<String>? = when (source) {
        // the frame is resolved now and not when the picker opened: a window moved in between
        // would have been captured where it used to be
        is SourceVideo.Ecran, is SourceVideo.Fenetre ->
            cadre(source)?.let { region(it, imagesParSeconde) }
        is SourceVideo.Entree -> listOf(
            "-f", "dshow",
            "-rtbufsize", "64M",
            "-i", "video=${source.nom}",
        )
        is SourceVideo.Fichier -> listOf(
            // at the film's real speed. without `-re`, ffmpeg decodes as fast as it can and an hour
            // of film would go out in seconds
            "-re",
            "-i", source.chemin,
            "-r", imagesParSeconde.toString(),
        )
    }
}

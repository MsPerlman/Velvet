package li.velvetchat.core

import kotlin.test.Test
import kotlin.test.assertEquals

class InstallationTest {

    @Test
    fun litLeBlocVelvetEtLeFocusLivekit() {
        val c = Installation.analyser(
            """
            {"m.homeserver":{"base_url":"https://example.org/"},
             "org.matrix.msc4143.rtc_foci":[{"type":"autre"},{"type":"livekit","livekit_service_url":"https://lk.example.org/"}],
             "li.velvetchat":{"dictee":"wss://d.example.org/dictee","import":"http://10.0.0.2:8181/",
               "mises_a_jour":"#maj:example.org","gifs":" cle ","inconnu":1}}
            """,
        )
        assertEquals("https://lk.example.org/sfu/get", c.vocal)
        assertEquals("wss://d.example.org/dictee", c.dictee)
        assertEquals("http://10.0.0.2:8181", c.import)
        assertEquals("#maj:example.org", c.misesAJour)
        assertEquals("cle", c.gifs)
        assertEquals("", c.catalogue)
    }

    @Test
    fun leSalonCompleteSansEffacer() {
        val dossier = kotlin.io.path.createTempDirectory().toString()
        Installation.oublier()
        Installation.completer(dossier, """{"gifs":"secret","dictee":"wss://prive/dictee"}""")
        val c = Installation.config.value
        assertEquals("secret", c.gifs)
        assertEquals("wss://prive/dictee", c.dictee)
        assertEquals("secret", Installation.ou({ this.gifs }, "build"))
        assertEquals("build", Installation.ou({ this.catalogue }, "build"))
        Installation.oublier()
    }

    @Test
    fun unServeurQuiNeDitRienNeDonneRien() {
        assertEquals(ConfigServeur(), Installation.analyser("""{"m.homeserver":{"base_url":"https://x/"}}"""))
    }
}

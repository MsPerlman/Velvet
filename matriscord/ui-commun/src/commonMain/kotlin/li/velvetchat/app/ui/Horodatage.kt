package li.velvetchat.app.ui

/**
 * timestamping a message, in one place
 *
 * this logic used to live inside `calculerLignes`, as local functions. it was correct there and
 * unreachable: the first screen that wanted to date a message elsewhere, the pin list, had no
 * choice but to rewrite it
 *
 * that is exactly the two paths to one output problem, where only one of them gets the fixes. two
 * divergent date formats in the same app do not get noticed either, they get read, and people
 * wonder why it is written differently
 *
 * why a class and not a function
 *
 * `calculerLignes` carries a detailed comment on this: the `SimpleDateFormat`s and the current day
 * are hoisted out of the loop, because allocating them per message cost real money while scrolling,
 * `Calendar` being the heaviest date object on the platform
 *
 * extracting a free function that built its formatters on every call would have undone that
 * optimisation while hiding it. so the state is carried by an instance built once: in a `remember`
 * on the screen side, once per computation on the timeline side
 *
 * what the instance freezes. [aujourdhui] is read at construction, so an instance kept for several
 * days would say "today" about yesterday. that is harmless here, the timeline builds one per
 * recomputation and a details screen does not survive midnight, but it is worth knowing before
 * keeping one in a singleton
 */
/**
 * public and not internal since the move into `ui-commun`: internal in kotlin covers the module and
 * not the package. so what crosses the boundary has to be visible, and what has no business
 * crossing stays private
 */
/**
 * the chosen language's locale, for everything that formats a date or a number
 *
 * the uk and not the us: our english speaker is english, and a day first date speaks to them where
 * a month first one would not
 */
fun localeCourante(): java.util.Locale = when (langueCourante) {
    Langue.FRANCAIS -> java.util.Locale.FRENCH
    Langue.ANGLAIS -> java.util.Locale.UK
}

class Horodateur {

    private val zone = java.util.TimeZone.getDefault()

    /**
     * the formats follow the chosen language, not the phone's
     *
     * a `SimpleDateFormat` freezes its locale at construction, and a timestamper can outlive a
     * language change, leaving a french date in the middle of an english screen. so we remember
     * which language built the formats and rebuild them when it changes: three objects, once, and
     * not per message displayed
     */
    private var langueDesFormats = langueCourante
    private var heure = format("HH:mm")
    private var dateComplete = format("dd/MM/yyyy HH:mm")
    private var dateLongue = format("d MMMM yyyy")

    private fun format(motif: String) =
        java.text.SimpleDateFormat(motif, localeCourante())

    private fun aJour() {
        if (langueDesFormats == langueCourante) return
        langueDesFormats = langueCourante
        heure = format("HH:mm")
        dateComplete = format("dd/MM/yyyy HH:mm")
        dateLongue = format("d MMMM yyyy")
    }

    /**
     * the local day number, without going through `Calendar`
     *
     * a timezone offset and a division are enough, and the offset is read per timestamp, without
     * which the result would be wrong on either side of a daylight saving change
     */
    fun jourLocal(ts: Long): Long = (ts + zone.getOffset(ts)) / 86_400_000L

    private val aujourdhui = jourLocal(System.currentTimeMillis())

    /**
     * a message's time, in the reference's shape
     *
     * three cases, and it is not fussiness: a full date on a message from ten minutes ago drowns
     * the useful part, the time, in eight digits everybody already knows. conversely, a bare time
     * on a message from last year makes it look like it just arrived. the day separator is not
     * enough, it is at the top of the screen and often out of view when you are reading the middle
     * of a long day
     */
    fun horodater(ts: Long): String {
        aJour()
        return when (jourLocal(ts)) {
            aujourdhui -> horsUi.aujourdhuiA(heure.format(java.util.Date(ts)))
            aujourdhui - 1 -> horsUi.hierA(heure.format(java.util.Date(ts)))
            else -> dateComplete.format(java.util.Date(ts))
        }
    }

    /**
     * the time alone, which is what the desktop reference puts next to the name
     *
     * the date never appears there: the day separator carries it, once for the whole day. repeating
     * it on every message is what velvetdesk used to do
     */
    fun heureSeule(ts: Long): String {
        aJour()
        return heure.format(java.util.Date(ts))
    }

    /**
     * the full date, with no "today" or "yesterday"
     *
     * for the lists that take a message out of its conversation, the pins first of all. there,
     * "today at 20:26" is a bad answer to the question asked: you are looking at a list of excerpts
     * from any time, and what you want to know is when, precisely. it is also what the reference
     * shows in its pin panel
     */
    fun dateEtHeure(ts: Long): String {
        aJour()
        return dateComplete.format(java.util.Date(ts))
    }


    /**
     * a day separator's label: "today", "yesterday", or a full date
     *
     * the separator used to reuse `horodater()` cut before the "at", which worked for today and
     * yesterday and left the time inside the date on any other day
     */
    fun jour(ts: Long): String {
        aJour()
        return when (jourLocal(ts)) {
            aujourdhui -> horsUi.aujourdhui
            aujourdhui - 1 -> horsUi.hier
            else -> dateLongue.format(java.util.Date(ts))
        }
    }
}

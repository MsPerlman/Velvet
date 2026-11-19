package li.velvetchat.design

/**
 * the themes shipped with the app, named once for both platforms
 *
 * they live as files in `design/themes/`, which is the single source the module's readme
 * documents. neither platform can list that folder at runtime — a jar cannot enumerate its own
 * classpath, and an apk's assets are listable but the desktop's are not — so the names are
 * written out here rather than discovered
 *
 * written **once**, though. the desktop used to keep its own copy of this list, so adding a
 * theme meant remembering a file nobody would think to open, and forgetting it meant the theme
 * shipped and never appeared. `tools/verifier-themes.py` now fails when the folder and this list
 * disagree, in either direction
 */
object ThemesLivres {

    /** file names, in the order the picker should offer them */
    val NOMS: List<String> = listOf(
        "velvet-curfew.json",
        "arctic.json",
        "blossom.json",
        "soft-canvas.json",
        "accent-violet.json",
    )

    /**
     * the template, kept out of [NOMS] on purpose
     *
     * it is a complete theme and it would load, but offering it as a choice would mean offering
     * "the default theme" twice under two names. it is there to be copied, not picked
     */
    const val MODELE = "modele.json"
}

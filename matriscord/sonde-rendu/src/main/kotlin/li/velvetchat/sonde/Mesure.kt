package li.velvetchat.sonde

data class Passe(
    val nom: String,
    val charge: String,
    val durees: DoubleArray,
) {
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
}

data class Lecture(
    val periodeMs: Double,
    val medianeMs: Double,
    val p95Ms: Double,
    val p99Ms: Double,
    val pireMs: Double,
    val perdues: Int,
    val total: Int,
) {
    val partPerdues: Double get() = perdues * 100.0 / total

    val hz: Int get() = Math.round(1000.0 / periodeMs).toInt()
}

private const val FACTEUR_PERDUE = 1.5

fun lire(durees: DoubleArray): Lecture {
    val triees = durees.sorted()
    fun centile(p: Double) = triees[(triees.size * p).toInt().coerceAtMost(triees.size - 1)]
    val mediane = centile(0.50)
    return Lecture(
        periodeMs = mediane,
        medianeMs = mediane,
        p95Ms = centile(0.95),
        p99Ms = centile(0.99),
        pireMs = triees.last(),
        perdues = durees.count { it > mediane * FACTEUR_PERDUE },
        total = durees.size,
    )
}

fun verdict(reelle: Lecture, adverse: Lecture): String = when {
    reelle.partPerdues > 2.0 ->
        "INSUFFISANT — la charge realiste perd deja des trames. Le repli du cahier (UI " +
            "desktop separee) redevient une question."

    adverse.partPerdues > 10.0 ->
        "FLUIDE, SANS MARGE — la charge realiste passe, la charge adverse s'ecroule. " +
            "Compose Multiplatform convient, mais le plafond d'animations simultanees " +
            "(deja pose cote mobile) devra l'etre cote desktop des le lot 1."

    adverse.partPerdues > 2.0 ->
        "FLUIDE — la charge realiste passe sans perte, l'adverse accroche un peu. C'est " +
            "exactement ce que le plafond d'animations existe pour couvrir."

    else ->
        "FLUIDE, AVEC MARGE — ni la charge realiste ni la charge adverse ne perdent de " +
            "trame. Compose Multiplatform tient, la decision n°1 du plan est confirmee."
}

fun redigerRapport(passes: List<Pair<Passe, Lecture>>): String = buildString {
    appendLine("Sonde de rendu - lot 0 du PLAN-DESKTOP")
    appendLine("=".repeat(64))
    val (_, premiere) = passes.first()
    appendLine("ecran    : ${premiere.hz} Hz (periode observee %.2f ms)".format(premiere.periodeMs))
    appendLine("machine  : ${System.getProperty("os.name")} / ${Runtime.getRuntime().availableProcessors()} coeurs")
    appendLine("java     : ${System.getProperty("java.version")}")
    appendLine("=".repeat(64))
    passes.forEach { (passe, l) ->
        appendLine()
        appendLine("${passe.nom}")
        appendLine("  charge  : ${passe.charge}")
        appendLine("  mediane : %6.2f ms      p95 : %6.2f ms".format(l.medianeMs, l.p95Ms))
        appendLine("  p99     : %6.2f ms     pire : %6.2f ms".format(l.p99Ms, l.pireMs))
        appendLine(
            "  trames perdues (> %.2f ms) : %d / %d  (%.1f %%)".format(
                l.periodeMs * FACTEUR_PERDUE, l.perdues, l.total, l.partPerdues,
            )
        )
    }
    appendLine()
    appendLine("=".repeat(64))
    appendLine(verdict(passes[0].second, passes[1].second))
}

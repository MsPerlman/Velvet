package li.velvetchat.verif

import kotlin.system.exitProcess

internal class Checks {
    private val lines = mutableListOf<Triple<String, String, String>>()

    fun pass(name: String, detail: String) = add("✓", name, detail)
    fun fail(name: String, detail: String) = add("✗", name, detail)
    fun inconclusive(name: String, detail: String) = add("~", name, detail)
    fun note(name: String, detail: String) = add("i", name, detail)

    private fun add(mark: String, name: String, detail: String) {
        lines += Triple(mark, name, detail)
        println("$mark $name — $detail")
    }

    fun report() {
        val failed = lines.count { it.first == "✗" }
        val unclear = lines.count { it.first == "~" }
        println("=".repeat(72))
        when {
            failed > 0 -> {
                println("  ÉCHEC — $failed vérification(s) non satisfaite(s)")
                lines.filter { it.first == "✗" }.forEach { println("    • ${it.second} : ${it.third}") }
                println("=".repeat(72))
                exitProcess(1)
            }
            unclear > 0 -> println("  VÉRIFIÉ, sauf $unclear point non concluant (voir ~)")
            else -> println("  VÉRIFIÉ — chaque effet a été observé, pas supposé")
        }
        println("=".repeat(72))
    }
}

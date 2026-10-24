package app.work.server

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class SourceRulesTest {
    @Test
    fun mainSourcesKeepTheHardRules() {
        val files = kotlinFiles(File("src/main/kotlin")) + kotlinFiles(File("../shared/src/commonMain/kotlin"))
        val violations = files.flatMap { file ->
            val text = file.readText()
            val hits = mutableListOf<String>()
            if ("println(" in text) hits += "println"
            if ("System.out" in text) hits += "System.out"
            if (Regex("""(?i)select\s""").containsMatchIn(text)) hits += "SQL"
            if (file.path.contains("/agent/") || file.path.endsWith("Routes.kt")) {
                if (".contains(" in text) hits += "contains"
                if ("Regex(" in text) hits += "Regex"
            }
            hits.map { "${file.path}: $it" }
        }
        assertEquals(emptyList(), violations)
    }

    private fun kotlinFiles(root: File): List<File> =
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
}

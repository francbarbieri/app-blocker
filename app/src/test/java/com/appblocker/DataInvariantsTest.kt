package com.appblocker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Mechanical checks for data-layer invariants — they exist because earlier
 * drift (the missing schemas/1.json) left a migration unverifiable.
 */
class DataInvariantsTest {

    private val schemasDir = locateModuleFile("schemas/com.appblocker.data.local.AppDatabase")

    @Test
    fun `current database version has an exported schema JSON`() {
        val version = readDatabaseVersion()
        val schemaFile = schemasDir.resolve("$version.json")
        assertTrue(
            "Schema for current @Database(version = $version) is missing at ${schemaFile.canonicalPath}. " +
                "exportSchema=true and ksp room.schemaLocation must be configured so every version " +
                "ships with a JSON the migration test helper can validate against.",
            schemaFile.exists()
        )
    }

    @Test
    fun `production does not call fallbackToDestructiveMigration`() {
        val mainSrc = locateModuleFile("src/main/java")
        val offenders = mutableListOf<String>()
        mainSrc.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                file.readLines().forEachIndexed { idx, line ->
                    if (line.contains("fallbackToDestructiveMigration") && !line.trimStart().startsWith("//")) {
                        offenders += "${file.relativeTo(mainSrc)}:${idx + 1}"
                    }
                }
            }
        assertFalse(
            "fallbackToDestructiveMigration silently drops user data. Write a Migration instead. Offenders:\n${offenders.joinToString("\n")}",
            offenders.isNotEmpty()
        )
    }

    private fun readDatabaseVersion(): Int {
        val appDatabase = locateModuleFile("src/main/java/com/appblocker/data/local/AppDatabase.kt")
        val text = appDatabase.readText()
        val match = Regex("""version\s*=\s*(\d+)""").find(text)
            ?: error("Could not parse @Database(version = N) from ${appDatabase.canonicalPath}")
        return match.groupValues[1].toInt()
    }

    private fun locateModuleFile(relative: String): File {
        val candidates = listOf(File(relative), File("app/$relative"))
        return candidates.firstOrNull { it.exists() }
            ?: error("Could not locate $relative from cwd=${File(".").canonicalPath}")
    }
}

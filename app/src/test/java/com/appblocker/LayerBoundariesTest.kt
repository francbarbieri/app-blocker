package com.appblocker

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Mechanical layer-boundary checks. These rules used to drift (e.g. ViewModels
 * importing [com.appblocker.data] directly) — the build now fails when they do.
 */
class LayerBoundariesTest {

    @Test
    fun `domain layer has no Android, Compose, or Room dependencies`() {
        val offenders = scan(
            root = locateMainJava().resolve("com/appblocker/domain"),
            forbiddenPrefixes = listOf(
                "import android.",
                "import androidx.",
                "import kotlinx.coroutines.android",
                "import androidx.compose",
                "import androidx.room",
            )
        )
        assertTrue(
            "Domain layer must be free of Android / Compose / Room imports. Offenders:\n${offenders.joinToString("\n")}",
            offenders.isEmpty()
        )
    }

    @Test
    fun `presentation layer does not import data layer impls`() {
        val offenders = scan(
            root = locateMainJava().resolve("com/appblocker/presentation"),
            forbiddenPrefixes = listOf("import com.appblocker.data."),
        )
        assertTrue(
            "Presentation layer must depend on domain abstractions, not data impls. Wire repository implementations through AppContainer. Offenders:\n${offenders.joinToString("\n")}",
            offenders.isEmpty()
        )
    }

    @Test
    fun `service layer does not import data layer impls`() {
        val offenders = scan(
            root = locateMainJava().resolve("com/appblocker/service"),
            forbiddenPrefixes = listOf("import com.appblocker.data."),
        )
        assertTrue(
            "Service layer must consume use cases from AppContainer, not construct repositories directly. Offenders:\n${offenders.joinToString("\n")}",
            offenders.isEmpty()
        )
    }

    private fun scan(root: File, forbiddenPrefixes: List<String>): List<String> {
        if (!root.exists()) error("Expected source root not found: ${root.canonicalPath}")
        val offenders = mutableListOf<String>()
        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                file.readLines().forEachIndexed { idx, line ->
                    val trimmed = line.trimStart()
                    forbiddenPrefixes.firstOrNull { trimmed.startsWith(it) }?.let { prefix ->
                        offenders += "${file.relativeTo(root)}:${idx + 1} -> $prefix"
                    }
                }
            }
        return offenders
    }

    private fun locateMainJava(): File {
        val candidates = listOf(File("src/main/java"), File("app/src/main/java"))
        return candidates.firstOrNull { it.exists() }
            ?: error("Could not locate src/main/java from cwd=${File(".").canonicalPath}")
    }
}

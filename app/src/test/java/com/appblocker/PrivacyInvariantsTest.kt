package com.appblocker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PrivacyInvariantsTest {

    @Test
    fun `manifest disables auto backup`() {
        val manifest = locateModuleFile("src/main/AndroidManifest.xml").readText()
        assertTrue(
            "AndroidManifest.xml must declare android:allowBackup=\"false\"",
            manifest.contains("android:allowBackup=\"false\"")
        )
        assertFalse(
            "AndroidManifest.xml must not declare android:allowBackup=\"true\"",
            manifest.contains("android:allowBackup=\"true\"")
        )
    }

    @Test
    fun `manifest references data extraction rules`() {
        val manifest = locateModuleFile("src/main/AndroidManifest.xml").readText()
        assertTrue(
            "AndroidManifest.xml must reference @xml/data_extraction_rules to block API 31+ cloud backup and device transfer",
            manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\"")
        )
        assertTrue(
            "res/xml/data_extraction_rules.xml must exist",
            locateModuleFile("src/main/res/xml/data_extraction_rules.xml").exists()
        )
    }

    @Test
    fun `manifest declares no QUERY_ALL_PACKAGES permission`() {
        val manifest = locateModuleFile("src/main/AndroidManifest.xml").readText()
        assertFalse(
            "QUERY_ALL_PACKAGES is unused and removed for Play policy compliance; use <queries> instead",
            manifest.contains("QUERY_ALL_PACKAGES")
        )
    }

    @Test
    fun `production sources contain no network code`() {
        val mainSrc = locateModuleFile("src/main/java")
        val forbidden = listOf(
            "okhttp3",
            "retrofit2",
            "java.net.URL",
            "java.net.HttpURLConnection",
            "android.webkit.WebView",
            "io.ktor.client",
            "com.android.volley"
        )
        val offenders = mutableListOf<String>()
        mainSrc.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    val trimmed = line.trimStart()
                    if (trimmed.startsWith("//")) return@forEachIndexed
                    forbidden.firstOrNull { trimmed.contains(it) }?.let { token ->
                        offenders += "${file.relativeTo(mainSrc)}:${index + 1} contains $token"
                    }
                }
            }
        assertTrue(
            "Privacy promise requires zero network code under app/src/main/. Offenders:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    private fun locateModuleFile(relative: String): File {
        val candidates = listOf(File(relative), File("app/$relative"))
        return candidates.firstOrNull { it.exists() }
            ?: error("Could not locate $relative from cwd=${File(".").canonicalPath}")
    }
}

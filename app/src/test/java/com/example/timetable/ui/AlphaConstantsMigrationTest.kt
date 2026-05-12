package com.example.timetable.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class AlphaConstantsMigrationTest {

    @Test
    fun mainSourceDoesNotUseLegacyAlphaAliases() {
        val legacyAliases = listOf(
            "overlayPrimaryHigh",
            "overlayPrimaryMedium",
            "overlaySecondary",
            "overlayHint",
            "overlayDecorative",
            "overlayDisabled",
            "overlayFaint",
            "overlayVeryFaint",
            "overlayMediumHigher",
            "overlayMediumHigh",
            "overlayBackgroundOverlay",
            "overlaySelected",
            "overlayHover",
            "overlayActive",
            "overlayActiveLight",
            "overlayLightest",
        )
        val sourceRoot = sourceDirectory("src/main/java/com/example/timetable/ui")
        val sourceText = sourceRoot
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString(separator = "\n") { it.readText() }

        legacyAliases.forEach { alias ->
            assertFalse("$alias should be migrated", sourceText.contains(alias))
        }
    }

    private fun sourceDirectory(path: String): File {
        val moduleRelative = File(path)
        if (moduleRelative.exists()) return moduleRelative
        return File("app", path)
    }
}

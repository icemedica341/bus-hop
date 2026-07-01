package com.bushop

import org.junit.Assert
import org.junit.Test
import java.io.File

/**
 * Architecture constraint tests.
 *
 * Reads source files as text and verifies layer separation rules
 * using simple string matching on import lines.
 *
 * Rule violations fail the test with a descriptive message.
 */
class ArchitectureTest {
    private val projectRoot: File by lazy {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        val candidates = listOf(cwd, cwd.parentFile).filterNotNull()
        val root = candidates.firstOrNull { File(it, "app/build.gradle.kts").exists() }
        requireNotNull(root) {
            "Could not find project root (need app/build.gradle.kts). Tried: " +
                candidates.map { it.absolutePath }
        }
    }

    // ── Domain module must not import android / androidx ──

    @Test
    fun `domain module does not import android or androidx`() {
        val violations = mutableListOf<String>()

        val domainDirs =
            listOf(
                "model",
                "api",
                "repository",
                "usecase",
            )

        val domainRoot = File(projectRoot, "domain/src/main/kotlin/com/bushop/domain")

        for (subDir in domainDirs) {
            File(domainRoot, subDir)
                .walkTopDown()
                .filter { it.extension == "kt" }
                .forEach { file ->
                    val lines = file.readLines()
                    for (line in lines) {
                        val trimmed = line.trim()
                        if (trimmed.startsWith("import ")) {
                            if (trimmed.startsWith("import android.") ||
                                trimmed.startsWith("import androidx.")
                            ) {
                                violations.add("${file.name}: $trimmed")
                            }
                        }
                    }
                }
        }

        Assert.assertTrue(
            buildString {
                appendLine("domain/ module must not import android.* or androidx.*:")
                violations.forEach { appendLine("  $it") }
            },
            violations.isEmpty(),
        )
    }

    // ── Release build must have minification enabled ──

    @Test
    fun `release build has isMinifyEnabled = true`() {
        val buildFile = File(projectRoot, "app/build.gradle.kts")
        val content = buildFile.readText()

        val releaseBlock =
            Regex(
                """release\s*\{(.*?)\}""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE),
            ).find(content)

        Assert.assertNotNull("Could not find 'release' block in app/build.gradle.kts", releaseBlock)
        Assert.assertTrue(
            "release block must contain 'isMinifyEnabled = true', found:\n${releaseBlock!!.value}",
            releaseBlock.value.contains("isMinifyEnabled = true"),
        )
    }

    // ── No hardcoded dependency strings ──

    @Test
    fun `no hardcoded dependency strings in build-gradle-kts`() {
        val buildFile = File(projectRoot, "app/build.gradle.kts")
        val content = buildFile.readLines()

        val hardcodedPattern =
            Regex(
                """implementation\(["'](androidx|com\.squareup|org\.jetbrains|junit)""",
                RegexOption.IGNORE_CASE,
            )

        val violations =
            content.mapIndexedNotNull { idx, line ->
                if (hardcodedPattern.containsMatchIn(line.trim())) {
                    "line ${idx + 1}: ${line.trim()}"
                } else {
                    null
                }
            }

        Assert.assertTrue(
            buildString {
                appendLine("Found hardcoded dependency strings — use libs.* instead:")
                violations.forEach { appendLine("  $it") }
            },
            violations.isEmpty(),
        )
    }

    // ── app module must not import data module directly ──

    @Test
    fun `app module does not import bushop data classes`() {
        val violations = mutableListOf<String>()

        // Known DI imports that are acceptable (composition root in MainActivity.kt and Factory):
        // RetrofitBusArrivalDataSource, BusStopStorage, BusRepositoryImpl — wired in Activity
        // UpdateCheckerImpl — concrete implementation injected at creation site
        // BusStopIndex — concrete implementation needed for randomEntry() not in repository
        val allowedPrefixes =
            listOf(
                "import com.bushop.data.api.RetrofitBusArrivalDataSource",
                "import com.bushop.data.api.UpdateCheckerImpl",
                "import com.bushop.data.local.BusStopIndex",
                "import com.bushop.data.local.BusStopStorage",
                "import com.bushop.data.repository.BusRepositoryImpl",
            )

        val appRoot = File(projectRoot, "app/src/main/kotlin")

        appRoot
            .walkTopDown()
            .filter { it.extension == "kt" }
            .forEach { file ->
                val lines = file.readLines()
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("import com.bushop.data.") &&
                        allowedPrefixes.none { trimmed == it }
                    ) {
                        violations.add("${file.name}: $trimmed")
                    }
                }
            }

        Assert.assertTrue(
            buildString {
                appendLine("app/ module must not import com.bushop.data.* — use domain-layer abstractions:")
                violations.forEach { appendLine("  $it") }
            },
            violations.isEmpty(),
        )
    }

    // ── ProGuard keep rules must reference existing first-party packages ──

    @Test
    fun `proguard keep rules match existing packages`() {
        val proguardFile = File(projectRoot, "app/proguard-rules.pro")
        Assert.assertTrue("proguard-rules.pro not found", proguardFile.exists())
        val lines = proguardFile.readLines()
        val violations = mutableListOf<String>()
        val keepPattern = Regex("""^-keep\s+(class\s+[\w.]+)""")
        val keepAllowObfuscationPattern = Regex("""^-keep,allowobfuscation\s+(class\s+[\w.]+)""")
        for (line in lines) {
            val trimmed = line.trim()
            for (pattern in listOf(keepPattern, keepAllowObfuscationPattern)) {
                val match = pattern.find(trimmed)
                if (match != null) {
                    val classSpec = match.groupValues[1]
                    val rawClass =
                        classSpec
                            .removePrefix("class ")
                            .removeSuffix(".**")
                            .removeSuffix(".*")
                    // Only validate packages in our source tree — library/third-party packages
                    // (androidx, com.google, retrofit2, okhttp3, kotlin, kotlinx) won't be found.
                    if (!rawClass.startsWith("com.bushop")) continue
                    // Strip inner class ($Factory, $Companion, etc.) — they live in the outer class file
                    val outerClass = rawClass.substringBefore('$')
                    val packagePart = outerClass.replace('.', '/')
                    val domainDir = File(projectRoot, "domain/src/main/kotlin/$packagePart")
                    val dataDir = File(projectRoot, "data/src/main/kotlin/$packagePart")
                    val appDir = File(projectRoot, "app/src/main/kotlin/$packagePart")
                    val domainFile = File(projectRoot, "domain/src/main/kotlin/$packagePart.kt")
                    val dataFile = File(projectRoot, "data/src/main/kotlin/$packagePart.kt")
                    val appFile = File(projectRoot, "app/src/main/kotlin/$packagePart.kt")
                    if (!domainDir.exists() &&
                        !dataDir.exists() &&
                        !appDir.exists() &&
                        !domainFile.exists() &&
                        !dataFile.exists() &&
                        !appFile.exists()
                    ) {
                        violations.add("'$trimmed' — '$outerClass' not found in any module")
                    }
                }
            }
        }
        Assert.assertTrue(
            buildString {
                appendLine("ProGuard -keep rules reference non-existent packages:")
                violations.forEach { appendLine("  $it") }
            },
            violations.isEmpty(),
        )
    }

    // ── Domain module must not depend on Android or AndroidX ──

    @Test
    fun `domain module has no Android dependencies`() {
        val buildFile = File(projectRoot, "domain/build.gradle.kts")
        Assert.assertTrue("domain/build.gradle.kts not found", buildFile.exists())
        val lines = buildFile.readLines()
        val violations = mutableListOf<String>()
        val androidPatterns =
            listOf("androidx.", "android.", "com.android.", "com.google.android.")
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("implementation") ||
                trimmed.startsWith("api") ||
                trimmed.startsWith("compileOnly") ||
                trimmed.startsWith("runtimeOnly")
            ) {
                for (pattern in androidPatterns) {
                    if (trimmed.contains(pattern)) {
                        violations.add(trimmed)
                        break
                    }
                }
            }
        }
        Assert.assertTrue(
            buildString {
                appendLine("domain/ module must not depend on Android/AndroidX libraries:")
                violations.forEach { appendLine("  $it") }
            },
            violations.isEmpty(),
        )
    }

    // ── Module dependency direction ──

    @Test
    fun `module dependency direction is correct`() {
        val violations = mutableListOf<String>()

        // domain must not depend on data or app
        val domainBuild = File(projectRoot, "domain/build.gradle.kts").readLines()
        for (line in domainBuild) {
            val trimmed = line.trim()
            if (trimmed.startsWith("implementation") || trimmed.startsWith("api")) {
                if (trimmed.contains("project(\":data") || trimmed.contains("project(\":app")) {
                    violations.add("domain/build.gradle.kts: $trimmed")
                }
            }
        }

        // data must depend on domain but not on app
        val dataBuild = File(projectRoot, "data/build.gradle.kts").readLines()
        var hasDomainDep = false
        for (line in dataBuild) {
            val trimmed = line.trim()
            if (trimmed.startsWith("implementation") || trimmed.startsWith("api")) {
                if (trimmed.contains("project(\":domain")) hasDomainDep = true
                if (trimmed.contains("project(\":app")) {
                    violations.add("data/build.gradle.kts: $trimmed")
                }
            }
        }
        if (!hasDomainDep) {
            violations.add("data/build.gradle.kts: missing dependency on :domain")
        }

        Assert.assertTrue(
            buildString {
                appendLine("Module dependency direction violations:")
                violations.forEach { appendLine("  $it") }
            },
            violations.isEmpty(),
        )
    }

    // ── Version catalog has no unused entries ──
    // Scopes regex to the correct TOML section ([versions], [libraries], [plugins]).

    @Test
    fun `all version catalog entries are referenced`() {
        val catalogFile = File(projectRoot, "gradle/libs.versions.toml")
        Assert.assertTrue("libs.versions.toml not found", catalogFile.exists())
        val lines = catalogFile.readLines()

        // Track which TOML section we are in
        var section = ""
        val versionKeys = mutableListOf<String>()
        val libraryKeys = mutableListOf<String>()
        val pluginKeys = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed == "[versions]" -> {
                    section = "versions"
                }

                trimmed == "[libraries]" -> {
                    section = "libraries"
                }

                trimmed == "[plugins]" -> {
                    section = "plugins"
                }

                trimmed.startsWith("[") -> {
                    section = "other"
                }

                trimmed.isEmpty() || trimmed.startsWith("#") -> {
                    continue
                }

                section == "versions" -> {
                    val eqIdx = trimmed.indexOf('=')
                    if (eqIdx > 0) versionKeys.add(trimmed.substring(0, eqIdx).trim())
                }

                section == "libraries" -> {
                    val eqIdx = trimmed.indexOf("= {")
                    if (eqIdx > 0) libraryKeys.add(trimmed.substring(0, eqIdx).trim())
                }

                section == "plugins" -> {
                    val eqIdx = trimmed.indexOf("= {")
                    if (eqIdx > 0) pluginKeys.add(trimmed.substring(0, eqIdx).trim())
                }
            }
        }

        // Search all build.gradle.kts files for references
        val allBuildFiles =
            listOf(
                File(projectRoot, "build.gradle.kts"),
                File(projectRoot, "app/build.gradle.kts"),
                File(projectRoot, "data/build.gradle.kts"),
                File(projectRoot, "domain/build.gradle.kts"),
            )
        val allBuildText = allBuildFiles.filter { it.exists() }.joinToString("\n") { it.readText() }

        val violations = mutableListOf<String>()

        // Check each library key has a "libs.<key>" reference in build files
        // (hyphens in catalog keys map to dots in Kotlin DSL accessors)
        for (key in libraryKeys) {
            val dslKey = key.replace("-", ".")
            val refPattern = "libs.$dslKey"
            if (!allBuildText.contains(refPattern)) {
                violations.add("'$key' defined in libs.versions.toml but never referenced (looked for libs.$dslKey)")
            }
        }

        // Check each plugin key has a "libs.plugins.<key>" reference in build files
        for (key in pluginKeys) {
            val dslKey = key.replace("-", ".")
            val refPattern = "libs.plugins.$dslKey"
            if (!allBuildText.contains(refPattern)) {
                violations.add("'$key' defined in [plugins] but never referenced (looked for $refPattern)")
            }
        }

        // Check each version key that's not referenced by any library entry
        val referencedVersions =
            Regex("""version\.ref\s*=\s*"([^"]+)""")
                .findAll(catalogFile.readText())
                .map { it.groupValues[1] }
                .toSet()

        for (key in versionKeys) {
            if (key !in referencedVersions) {
                violations.add("version '$key' defined but not referenced by any library entry")
            }
        }

        Assert.assertTrue(
            buildString {
                appendLine("Version catalog issues:")
                violations.forEach { appendLine("  $it") }
            },
            violations.isEmpty(),
        )
    }
}

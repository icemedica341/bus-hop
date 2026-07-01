plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless)
}

// ── Dependency locking for reproducible builds ──
dependencyLocking {
    lockAllConfigurations()
}

// ── Badge update task ──
// Refreshes static SVGs in docs/badges/ from shields.io.
// Usage: ./gradlew updateBadges -PtestCount=NNN   (specify test count)
//        ./gradlew updateBadges                     (use existing count)
//        ./gradlew test updateBadges -PautoDetect    (extract from test results)
val badgeTestCount =
    providers
        .gradleProperty("testCount")
        .orElse(
            providers
                .gradleProperty("autoDetect")
                .map {
                    val results =
                        fileTree(".") {
                            include("**/test-results/**/*.xml")
                            exclude(".gradle/")
                        }.files
                    if (results.isEmpty()) throw GradleException("No test results. Run ./gradlew test first, or pass -PtestCount=N")
                    results.sumOf { f -> Regex("""tests="(\d+)""").findAll(f.readText()).sumOf { it.groupValues[1].toInt() } }.toString()
                }.orElse(
                    providers.provider {
                        val svg = file("docs/badges/tests.svg")
                        if (svg.exists()) Regex(""": (\d+)""").find(svg.readText())?.groupValues?.get(1) ?: "161" else "161"
                    },
                ),
        )

tasks.register("updateBadges") {
    notCompatibleWithConfigurationCache("fetches external SVGs from shields.io")
    description = "Refresh static badge SVGs from shields.io into docs/badges/"
    group = "Development"

    val badges =
        listOf(
            "Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white" to "kotlin",
            "Compose-BOM%202026.05-4285F4?logo=jetpackcompose&logoColor=white" to "compose",
            "minSdk-26-34A853" to "minsdk",
            "targetSdk-37-34A853" to "targetsdk",
            "license-MIT-yellow" to "license",
            "GPT_5.4-high-10a37f" to "gpt5.4",
            "DeepSeek_V4_Flash-max-4F46E5" to "deepseek",
        )

    doLast {
        val t = badgeTestCount.get()
        file("docs/badges").mkdirs()
        val allBadges = badges + ("tests-$t-34A853" to "tests")
        var failed = 0

        allBadges.forEach { (slug, name) ->
            val url = "https://img.shields.io/badge/$slug"
            val out = file("docs/badges/$name.svg")
            logger.lifecycle("Fetching $url")
            try {
                out.writeBytes(java.net.URL(url).readBytes())
                logger.lifecycle("  -> $out (${out.length()} bytes)")
            } catch (e: Exception) {
                logger.warn("  ❌ $name failed: ${e.message}")
                failed++
            }
        }

        if (failed == 0) {
            logger.lifecycle("\n✅ All ${allBadges.size} badges updated")
        } else {
            throw GradleException("$failed/${allBadges.size} badge(s) failed")
        }
    }
}

// ── Spotless: format root Gradle scripts ──
spotless {

    kotlinGradle {
        ktlint("1.5.0")
        target("*.gradle.kts")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

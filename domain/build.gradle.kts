plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
}

spotless {
    kotlin {
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(
            mapOf("ktlint_standard_filename" to "disabled")
        )
        target("src/**/*.kt")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
detekt {
    config.setFrom(rootProject.file("detekt.yml"))
    buildUponDefaultConfig = true
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}

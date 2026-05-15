plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(libs.gson)
    implementation(libs.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}

// Pure Kotlin: the BotHub API client, request signing, pairing links, Guard
// codes and the update check. No Android classes, so it is tested on the
// JVM (also end-to-end against a BotHub in Docker, see README).
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(libs.okhttp)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    // End-to-end tests run only with BOTHUB_E2E_URL (and the login of a test user).
    environment("BOTHUB_E2E_URL", System.getenv("BOTHUB_E2E_URL") ?: "")
    testLogging { events("passed", "skipped", "failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}

// Root build file. Plugins are declared here with `apply false` and applied in
// the module build files, so versions live in one place (gradle/libs.versions.toml).
plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.sonarqube)
}

// One SonarQube project for the whole repo; the `link` module contributes its
// coverage report. Server URL and keys come from CI variables/secrets.
sonar {
    properties {
        property("sonar.projectKey", System.getenv("SONAR_PROJECT_KEY") ?: "FrontFin_android-sdk")
        property("sonar.organization", System.getenv("SONAR_ORGANIZATION") ?: "")
        property("sonar.host.url", System.getenv("SONAR_HOST_URL") ?: "")
    }
}

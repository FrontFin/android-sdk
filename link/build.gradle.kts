import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    jacoco
}

android {
    namespace = "com.meshpay.link"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        // The version sent to Link (meshSdkVersion / X-SDK-Version) is generated
        // from the version catalog, never hand-maintained (§2.12).
        buildConfigField("String", "SDK_VERSION", "\"${libs.versions.meshpay.link.get()}\"")
        consumerProguardFiles("consumer-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        targetSdk = 35
        unitTests {
            // Robolectric needs android resources and the real org.json on the
            // unit-test classpath for the URL builder, policy and parser tests.
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

kotlin {
    jvmToolchain(17)
    // Public API is the contract; a change to it must be deliberate.
    explicitApi()
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.core)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}

// --- Coverage (§2.9): lines/functions/statements >= 90 %, branches >= 80 % ---
// Measured over the SDK's own code only; generated and framework-glue classes are
// excluded so the ratchet reflects logic the tests actually own.
val coverageExclusions = listOf(
    "**/BuildConfig.*",
    "**/R.class",
    "**/R$*.class",
    // WebView/Activity glue is exercised by the instrumented tests (androidTest),
    // not the JVM unit tests this report measures, so it is excluded from the
    // unit-coverage floor. The pure logic (URL builder, navigation policy, bridge
    // parser, origin helpers) carries the ratchet.
    "**/internal/LinkActivity*.*",
    "**/internal/ExternalLauncher*.*",
)

jacoco {
    toolVersion = "0.8.12"
}

tasks.withType<Test>().configureEach {
    configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

val jacocoTestReport = tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    val kotlinClasses = fileTree("${layout.buildDirectory.get()}/tmp/kotlin-classes/debug") {
        exclude(coverageExclusions)
    }
    classDirectories.setFrom(kotlinClasses)
    sourceDirectories.setFrom(files("src/main/java"))
    executionData.setFrom(
        fileTree(layout.buildDirectory.get()) {
            include("**/testDebugUnitTest.exec", "**/jacoco/testDebugUnitTest.exec")
        },
    )
}

tasks.register<JacocoCoverageVerification>("jacocoCoverageVerification") {
    dependsOn(jacocoTestReport)
    val kotlinClasses = fileTree("${layout.buildDirectory.get()}/tmp/kotlin-classes/debug") {
        exclude(coverageExclusions)
    }
    classDirectories.setFrom(kotlinClasses)
    sourceDirectories.setFrom(files("src/main/java"))
    executionData.setFrom(
        fileTree(layout.buildDirectory.get()) {
            include("**/testDebugUnitTest.exec", "**/jacoco/testDebugUnitTest.exec")
        },
    )
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }
        }
        rule {
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

mavenPublishing {
    // Group/artifact also come from gradle.properties (GROUP / POM_ARTIFACT_ID);
    // this sets the version from the single source of truth and is explicit.
    coordinates(
        groupId = "com.meshpay",
        artifactId = "link",
        version = libs.versions.meshpay.link.get(),
    )
    // The Central Portal host and in-memory signing are configured from
    // gradle.properties (SONATYPE_HOST=CENTRAL_PORTAL, RELEASE_SIGNING_ENABLED=true),
    // which creates the `publishAndReleaseToMavenCentral` task. Calling
    // publishToMavenCentral() here as well would set the already-finalized host a
    // second time and fail configuration.
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
}

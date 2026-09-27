import org.gradle.api.artifacts.dsl.LockMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover)
}

val localProperties =
    Properties().apply {
        rootProject
            .file("local.properties")
            .takeIf { it.isFile }
            ?.inputStream()
            ?.use { load(it) }
    }
val githubClientId =
    providers
        .gradleProperty("mobimon.githubClientId")
        .orElse(providers.environmentVariable("MOBIMON_GITHUB_CLIENT_ID"))
        .getOrElse(localProperties.getProperty("mobimon.githubClientId", ""))
require(githubClientId.matches(Regex("[A-Za-z0-9_]*"))) { "Invalid GitHub OAuth client ID" }

android {
    namespace = "com.monsters.mobimon"
    compileSdk = 34
    useLibrary("android.car")

    defaultConfig {
        applicationId = "com.monsters.mobimon"
        minSdk = 34
        targetSdk = 34
        buildConfigField("String", "GITHUB_CLIENT_ID", "\"$githubClientId\"")
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "com.monsters.mobimon.testing.MobiMonTestRunner"
    }

    buildTypes {
        debug { applicationIdSuffix = ".demo" }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Robolectric decodes character PNGs at full size (the Mobi atlas alone is ~150MB); 512MB default OOMs.
        unitTests.all {
            it.maxHeapSize = "2g"
            it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
        }
    }

    sourceSets {
        getByName("testDebug").java.srcDir("src/journeyTest/java")
        getByName("androidTest").java.srcDir("src/journeyTest/java")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        htmlReport = true
        xmlReport = true
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ktlint {
    version.set(libs.versions.ktlint.engine)
}

// Lock application and test dependencies, including all Android build variants.
// Keep Gradle plugin/compiler tool configurations outside the application lock.
configurations.configureEach {
    if (name.endsWith("CompileClasspath") || name.endsWith("RuntimeClasspath")) {
        resolutionStrategy.activateDependencyLocking()
    }
}

dependencyLocking {
    lockMode.set(LockMode.STRICT)
}

dependencies {
    implementation(project(":core:core-navigation"))
    implementation(project(":core:core-presentation"))
    kover(project(":core:core-navigation"))
    kover(project(":core:core-presentation"))
    implementation(project(":core:core-domain"))
    implementation(project(":core:core-auth"))
    kover(project(":core:core-auth"))
    implementation(project(":core:core-database"))
    implementation(project(":core:core-vss"))
    implementation(project(":core:core-ui"))
    implementation(project(":feature:feature-pet"))
    implementation(project(":feature:feature-customization"))
    implementation(project(":feature:feature-quest"))
    implementation(project(":feature:feature-vehicle-info"))
    implementation(project(":feature:feature-auth"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.robolectric)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    // Match AGP's tested-app runtime graph when generating strict test dependency locks.
    androidTestRuntimeOnly(libs.kotlin.stdlib.common)
    kover(project(":core:core-domain"))
    kover(project(":core:core-database"))
    kover(project(":core:core-vss"))
    kover(project(":core:core-ui"))
    kover(project(":feature:feature-pet"))
    kover(project(":feature:feature-customization"))
    kover(project(":feature:feature-quest"))
    kover(project(":feature:feature-vehicle-info"))
    kover(project(":feature:feature-auth"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

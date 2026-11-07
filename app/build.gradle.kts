import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    // Screens rendered on the JVM (Robolectric) for tests and for the README screenshots.
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "gr.wsol.gamenight"
    compileSdk = 37

    defaultConfig {
        applicationId = "gr.wsol.gamenight"
        minSdk = 29
        targetSdk = 36
        versionCode = 12
        versionName = "1.2.1"
        buildConfigField("boolean", "UPDATE_CHECK", (!project.hasProperty("noUpdateCheck")).toString())
    }

    // The release key comes from keystore.properties (git-ignored, written by CI from the repository's
    // secrets); without it, release builds are signed with the debug key (test builds only).
    // In CI the passwords come straight from the environment (no escaping by Properties, so a "\\" in a
    // password survives), with a pasted trailing newline removed.
    val keys = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { f -> Properties().apply { f.inputStream().use { load(it) } } }
    fun secret(env: String, prop: String): String? = (System.getenv(env)?.takeIf { it.isNotEmpty() } ?: keys?.getProperty(prop))?.trimEnd('\r', '\n')
    signingConfigs {
        if (keys != null) create("release") {
            storeFile = rootProject.file(keys.getProperty("storeFile"))
            storePassword = secret("RELEASE_STORE_PASSWORD", "storePassword")
            keyAlias = secret("RELEASE_KEY_ALIAS", "keyAlias")
            keyPassword = secret("RELEASE_KEY_PASSWORD", "keyPassword")
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "PACK_BRANCH", "\"cinematic\"")
        }
        release {
            buildConfigField("String", "PACK_BRANCH", "\"main\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
            it.maxHeapSize = "3g"
            listOf("core.search", "core.only").forEach { k -> System.getProperty(k)?.let { v -> it.systemProperty(k, v) } }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
}

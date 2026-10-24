import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Release tags look like v1.2.3; everything else is a dev build.
val appVersion: String = System.getenv("TABGUARD_VERSION")?.removePrefix("v")?.takeIf { it.isNotBlank() } ?: "0.0.0-dev"
val appVersionCode: Int = Regex("""^(\d+)\.(\d+)\.(\d+)""").find(appVersion)
    ?.destructured?.let { (a, b, c) -> a.toInt() * 10_000 + b.toInt() * 100 + c.toInt() } ?: 1

android {
    namespace = "app.tabguard"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.tabguard"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersion
    }

    // One key for every build so each APK installs over the previous one. CI decodes it from the
    // ANDROID_KEYSTORE_BASE64 secret; without it (forks, local) the default debug key is used.
    val keystore = System.getenv("TABGUARD_KEYSTORE")?.let(::file)?.takeIf { it.exists() }
        ?: file("debug.keystore").takeIf { it.exists() }
    signingConfigs {
        if (keystore != null) {
            create("tabguard") {
                storeFile = keystore
                storePassword = System.getenv("TABGUARD_KEYSTORE_PASSWORD") ?: "android"
                keyAlias = System.getenv("TABGUARD_KEY_ALIAS") ?: "androiddebugkey"
                keyPassword = System.getenv("TABGUARD_KEY_PASSWORD") ?: "android"
            }
        }
    }

    buildTypes {
        val signing = signingConfigs.findByName("tabguard") ?: signingConfigs.getByName("debug")
        getByName("debug") { signingConfig = signing }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signing
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}

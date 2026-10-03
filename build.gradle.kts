// Plugins go on the root buildscript classpath so that AGP and KGP share one classloader,
// and AGP is only requested when the Android SDK (and therefore :app) is present.
buildscript {
    val hasAndroidSdk = System.getenv("ANDROID_HOME") != null ||
        System.getenv("ANDROID_SDK_ROOT") != null ||
        file("local.properties").let { it.exists() && it.readText().contains("sdk.dir") }
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
        if (hasAndroidSdk) {
            classpath(libs.android.gradle.plugin)
            classpath(libs.compose.compiler.gradle.plugin)
            classpath(libs.ksp.gradle.plugin)
        }
    }
}

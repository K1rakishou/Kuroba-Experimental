plugins {
    alias(libs.plugins.kotlin.compose).apply(false)
    alias(libs.plugins.detekt).apply(false)
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://plugins.gradle.org/m2/") }
    }
}
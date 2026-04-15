package com.github.k1rakishou.buildsrc

import com.android.build.gradle.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

class KurobaLibraryPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.pluginManager.apply("com.android.library")
    target.pluginManager.apply("kotlin-android")
    target.pluginManager.apply("com.google.devtools.ksp")
    target.pluginManager.apply("org.jetbrains.kotlin.plugin.parcelize")

    val libs = target.extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
    val isComposeModule = target.properties["compose"] == "true"

    if (isComposeModule) {
      target.pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
    }

    target.extensions.configure<LibraryExtension> {
      compileSdk = libs.findVersion("compileSdk").get().requiredVersion.toInt()

      defaultConfig {
        minSdk = libs.findVersion("minSdk").get().requiredVersion.toInt()
      }

      buildTypes {
        release { isMinifyEnabled = false }
        debug { isMinifyEnabled = false }
      }

      compileOptions {
        val jvmTarget = JavaVersion.toVersion(libs.findVersion("jvmTarget").get().requiredVersion)
        sourceCompatibility = jvmTarget
        targetCompatibility = jvmTarget
      }

      if (isComposeModule) {
        buildFeatures {
          compose = true
        }
      }
    }

    target.extensions.configure<KotlinAndroidProjectExtension> {
      compilerOptions {
        jvmTarget.set(JvmTarget.Companion.fromTarget(libs.findVersion("jvmTarget").get().requiredVersion))

        freeCompilerArgs.addAll(FreeCompilerArgs.args)
        if (isComposeModule) {
          freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
        }
      }
    }
  }
}
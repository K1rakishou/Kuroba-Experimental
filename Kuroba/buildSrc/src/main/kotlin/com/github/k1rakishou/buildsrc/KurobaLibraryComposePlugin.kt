package com.github.k1rakishou.buildsrc

import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

class KurobaLibraryComposePlugin : Plugin<Project> {
  override fun apply(target: Project) {
    // Apply base plugin first
    target.pluginManager.apply(KurobaLibraryPlugin::class)
    // Then Compose compiler plugin
    target.pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

    target.extensions.configure<LibraryExtension> {
      buildFeatures {
        compose = true
      }
    }
  }
}
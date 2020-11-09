package com.github.k1rakishou.core_themes

import android.app.Application
import com.github.k1rakishou.core_themes.di.DaggerThemesComponent
import com.github.k1rakishou.core_themes.di.ThemesComponent
import com.github.k1rakishou.fsaf.FileManager
import kotlinx.coroutines.CoroutineScope

object ThemesModuleInjector {
  lateinit var themesComponent: ThemesComponent

  @JvmStatic
  fun build(
    application: Application,
    scope: CoroutineScope,
    fileManager: FileManager
  ): ThemesComponent {
    val dependencies = ThemesComponent.Dependencies(
      application,
      scope,
      fileManager
    )

    val mainComponent = DaggerThemesComponent.builder()
      .dependencies(dependencies)
      .build()

    themesComponent = mainComponent
    return themesComponent
  }

}
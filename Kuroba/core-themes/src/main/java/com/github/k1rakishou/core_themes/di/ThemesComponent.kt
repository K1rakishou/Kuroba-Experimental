package com.github.k1rakishou.core_themes.di

import android.app.Application
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.fsaf.FileManager
import dagger.BindsInstance
import dagger.Component
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Singleton
@Component(modules = [ThemesModule::class])
interface ThemesComponent {
  fun getThemeEngine(): ThemeEngine

  @Component.Builder
  interface Builder {
    @BindsInstance
    fun dependencies(deps: Dependencies): Builder
    fun build(): ThemesComponent
  }

  class Dependencies(
    val application: Application,
    val appScope: CoroutineScope,
    val fileManager: FileManager
  )

}
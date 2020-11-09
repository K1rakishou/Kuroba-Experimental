package com.github.k1rakishou.core_themes.di

import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.core_themes.ThemeParser
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class ThemesModule {

  @Provides
  @Singleton
  fun provideThemeEngine(
    dependencies: ThemesComponent.Dependencies,
    themeParser: ThemeParser
  ): ThemeEngine {
    return ThemeEngine(dependencies.appScope, themeParser)
  }

  @Singleton
  @Provides
  fun provideThemeParser(
    dependencies: ThemesComponent.Dependencies
  ): ThemeParser {
    return ThemeParser(
      dependencies.application,
      dependencies.fileManager
    )
  }

}
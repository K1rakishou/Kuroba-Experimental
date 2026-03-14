package com.github.k1rakishou.v2.di

import com.github.k1rakishou.v2.database.KurobaSettingsDatabase
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class KurobaSettingsModule {
  @Singleton
  @Provides
  fun provideDatabase(
    dependencies: KurobaSettingsComponent.Dependencies
  ): KurobaSettingsDatabase {
    return KurobaSettingsDatabase.buildDatabase(dependencies.application)
  }
}
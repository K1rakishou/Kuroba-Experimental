package com.github.k1rakishou.v2.di

import android.app.Application
import com.github.k1rakishou.v2.database.KurobaSettingsDatabase
import dagger.BindsInstance
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(
  modules = [
    KurobaSettingsModule::class
  ]
)
interface KurobaSettingsComponent {
  fun getKurobaSettingsDatabase(): KurobaSettingsDatabase

  @Component.Builder
  interface Builder {
    @BindsInstance
    fun dependencies(deps: Dependencies): Builder
    fun build(): KurobaSettingsComponent
  }

  class Dependencies(
    val application: Application
  )
}
package com.github.k1rakishou.v2.di

import android.app.Application

object KurobaSettingsComponentInjector {
  lateinit var kurobaSettingsComponent: KurobaSettingsComponent

  fun build(application: Application): KurobaSettingsComponent {
    val dependencies = KurobaSettingsComponent.Dependencies(
      application = application
    )

    val kurobaSettingsComponent = DaggerKurobaSettingsComponent.builder()
      .dependencies(dependencies)
      .build()

    this.kurobaSettingsComponent = kurobaSettingsComponent
    return kurobaSettingsComponent
  }
}
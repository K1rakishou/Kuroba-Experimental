package com.github.k1rakishou.model

import android.app.Application
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.model.di.DaggerModelComponent
import com.github.k1rakishou.model.di.ModelComponent
import com.github.k1rakishou.model.di.NetworkModule
import kotlinx.coroutines.CoroutineScope
import okhttp3.Dns

object ModelModuleInjector {
  lateinit var modelComponent: ModelComponent

  @JvmStatic
  fun build(
    application: Application,
    scope: CoroutineScope,
    dns: Dns,
    protocols: NetworkModule.OkHttpProtocolList,
    verboseLogs: Boolean,
    isDevFlavor: Boolean,
    appConstants: AppConstants
  ): ModelComponent {
    val dependencies = ModelComponent.Dependencies(
      application,
      scope,
      verboseLogs,
      isDevFlavor,
      dns,
      protocols,
      appConstants
    )

    val mainComponent = DaggerModelComponent.builder()
      .dependencies(dependencies)
      .build()

    modelComponent = mainComponent
    return modelComponent
  }

}
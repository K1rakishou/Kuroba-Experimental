package com.github.k1rakishou.model

import android.app.Application
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.model.di.DaggerModelMainComponent
import com.github.k1rakishou.model.di.ModelMainComponent
import com.github.k1rakishou.model.di.NetworkModule
import kotlinx.coroutines.CoroutineScope
import okhttp3.Dns
import okhttp3.Protocol

object DatabaseModuleInjector {
  lateinit var modelMainComponent: ModelMainComponent

  @JvmStatic
  fun build(
    application: Application,
    dns: Dns,
    protocols: List<Protocol>,
    loggerTagPrefix: String,
    verboseLogs: Boolean,
    isDevFlavor: Boolean,
    betaOrDevBuild: Boolean,
    appConstants: AppConstants,
    scope: CoroutineScope
  ): ModelMainComponent {
    val mainComponent = DaggerModelMainComponent.builder()
      .application(application)
      .okHttpDns(dns)
      .okHttpProtocols(NetworkModule.OkHttpProtocolList(protocols))
      .loggerTagPrefix(loggerTagPrefix)
      .verboseLogs(verboseLogs)
      .isDevFlavor(isDevFlavor)
      .betaOrDevBuild(betaOrDevBuild)
      .appConstants(appConstants)
      .appCoroutineScope(scope)
      .build()

    modelMainComponent = mainComponent
    return modelMainComponent
  }

}
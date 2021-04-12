package com.github.k1rakishou.model

import android.app.Application
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.dns.DnsOverHttpsSelectorFactory
import com.github.k1rakishou.common.dns.NormalDnsSelectorFactory
import com.github.k1rakishou.model.di.DaggerModelComponent
import com.github.k1rakishou.model.di.ModelComponent
import com.github.k1rakishou.model.di.NetworkModule
import kotlinx.coroutines.CoroutineScope

object ModelModuleInjector {
  lateinit var modelComponent: ModelComponent

  @JvmStatic
  fun build(
    application: Application,
    scope: CoroutineScope,
    normalDnsSelectorFactory: NormalDnsSelectorFactory,
    dnsOverHttpsSelectorFactory: DnsOverHttpsSelectorFactory,
    protocols: NetworkModule.OkHttpProtocolList,
    verboseLogs: Boolean,
    isDevFlavor: Boolean,
    okHttpUseDnsOverHttps: Boolean,
    appConstants: AppConstants
  ): ModelComponent {
    val dependencies = ModelComponent.Dependencies(
      application,
      scope,
      verboseLogs,
      isDevFlavor,
      okHttpUseDnsOverHttps,
      normalDnsSelectorFactory,
      dnsOverHttpsSelectorFactory,
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
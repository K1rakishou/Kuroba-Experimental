package com.github.k1rakishou.model.di

import android.app.Application
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.dns.DnsOverHttpsSelectorFactory
import com.github.k1rakishou.common.dns.NormalDnsSelectorFactory
import com.github.k1rakishou.v2.KurobaSettings
import kotlinx.coroutines.CoroutineScope

object ModelComponentInjector {
  lateinit var modelComponent: ModelComponent

  @JvmStatic
  fun build(
    application: Application,
    scope: CoroutineScope,
    normalDnsSelectorFactory: NormalDnsSelectorFactory,
    dnsOverHttpsSelectorFactory: DnsOverHttpsSelectorFactory,
    verboseLogs: Boolean,
    isDevFlavor: Boolean,
    isLowRamDevice: Boolean,
    okHttpUseDnsOverHttps: Boolean,
    appConstants: AppConstants,
    kurobaSettings: KurobaSettings
  ): ModelComponent {
    val dependencies = ModelComponent.Dependencies(
      application = application,
      coroutineScope = scope,
      verboseLogs = verboseLogs,
      isDevFlavor = isDevFlavor,
      isLowRamDevice = isLowRamDevice,
      okHttpUseDnsOverHttps = okHttpUseDnsOverHttps,
      normalDnsSelectorFactory = normalDnsSelectorFactory,
      dnsOverHttpsSelectorFactory = dnsOverHttpsSelectorFactory,
      appConstants = appConstants,
      kurobaSettings = kurobaSettings
    )

    val mainComponent = DaggerModelComponent.builder()
      .dependencies(dependencies)
      .build()

    modelComponent = mainComponent
    return modelComponent
  }

}
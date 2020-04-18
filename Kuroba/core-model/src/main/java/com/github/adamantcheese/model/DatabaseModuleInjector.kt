package com.github.adamantcheese.model

import android.app.Application
import com.github.adamantcheese.common.AppConstants
import com.github.adamantcheese.model.di.DaggerMainComponent
import com.github.adamantcheese.model.di.MainComponent
import com.github.adamantcheese.model.di.NetworkModule
import okhttp3.Dns
import okhttp3.Protocol

object DatabaseModuleInjector {

    @JvmStatic
    fun build(
            application: Application,
            dns: Dns,
            protocols: List<Protocol>,
            loggerTagPrefix: String,
            verboseLogs: Boolean,
            appConstants: AppConstants
    ): MainComponent {
        return DaggerMainComponent.builder()
                .application(application)
                .okHttpDns(dns)
                .okHttpProtocols(NetworkModule.OkHttpProtocolList(protocols))
                .loggerTagPrefix(loggerTagPrefix)
                .verboseLogs(verboseLogs)
                .appConstants(appConstants)
                .build()
    }

}
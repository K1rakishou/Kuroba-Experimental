package com.github.adamantcheese.model

import android.app.Application
import com.github.adamantcheese.common.AppConstants
import com.github.adamantcheese.model.di.DaggerModelMainComponent
import com.github.adamantcheese.model.di.ModelMainComponent
import com.github.adamantcheese.model.di.NetworkModule
import kotlinx.coroutines.CoroutineScope
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
            appConstants: AppConstants,
            scope: CoroutineScope
    ): ModelMainComponent {
        return DaggerModelMainComponent.builder()
                .application(application)
                .okHttpDns(dns)
                .okHttpProtocols(NetworkModule.OkHttpProtocolList(protocols))
                .loggerTagPrefix(loggerTagPrefix)
                .verboseLogs(verboseLogs)
                .appConstants(appConstants)
                .appCoroutineScope(scope)
                .build()
    }

}
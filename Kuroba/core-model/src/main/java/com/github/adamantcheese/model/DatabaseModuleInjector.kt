package com.github.adamantcheese.model

import android.app.Application
import com.github.adamantcheese.model.di.DaggerDatabaseComponent
import com.github.adamantcheese.model.di.DatabaseComponent
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
            verboseLogs: Boolean
    ): DatabaseComponent {
        return DaggerDatabaseComponent.builder()
                .application(application)
                .okHttpDns(dns)
                .okHttpProtocols(NetworkModule.OkHttpProtocolList(protocols))
                .loggerTagPrefix(loggerTagPrefix)
                .verboseLogs(verboseLogs)
                .build()
    }

}
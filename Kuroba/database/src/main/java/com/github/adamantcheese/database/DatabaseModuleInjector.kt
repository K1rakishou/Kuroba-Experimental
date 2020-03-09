package com.github.adamantcheese.database

import android.app.Application
import com.github.adamantcheese.database.di.DaggerDatabaseComponent
import com.github.adamantcheese.database.di.DatabaseComponent

object DatabaseModuleInjector {

    @JvmStatic
    fun build(
            application: Application,
            loggerTagPrefix: String,
            verboseLogs: Boolean
    ): DatabaseComponent {
        return DaggerDatabaseComponent.builder()
                .application(application)
                .loggerTagPrefix(loggerTagPrefix)
                .verboseLogs(verboseLogs)
                .build()
    }

}
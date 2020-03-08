package com.github.adamantcheese.database

import android.app.Application
import com.github.adamantcheese.database.di.DaggerDatabaseComponent
import com.github.adamantcheese.database.di.DatabaseComponent

object DatabaseModuleInjector {

    @JvmStatic
    fun build(application: Application): DatabaseComponent {
        return DaggerDatabaseComponent.builder()
                .application(application)
                .build()
    }

}
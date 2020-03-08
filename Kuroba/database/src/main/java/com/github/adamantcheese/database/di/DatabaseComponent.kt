package com.github.adamantcheese.database.di

import android.app.Application
import com.github.adamantcheese.database.KurobaDatabase
import dagger.BindsInstance
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(
        modules = [
            DatabaseModule::class
        ]
)
interface DatabaseComponent {
    fun inject(application: Application)

    fun getKurobaDatabase(): KurobaDatabase

    @Component.Builder
    interface Builder {
        @BindsInstance
        fun application(application: Application): Builder

        fun build(): DatabaseComponent
    }

}
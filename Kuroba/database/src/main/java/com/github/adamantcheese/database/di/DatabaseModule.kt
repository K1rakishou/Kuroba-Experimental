package com.github.adamantcheese.database.di

import android.app.Application
import com.github.adamantcheese.database.KurobaDatabase
import com.github.adamantcheese.database.source.YoutubeLinkExtraContentLocalSource
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class DatabaseModule {

    @Singleton
    @Provides
    fun provideDatabase(application: Application): KurobaDatabase {
        return KurobaDatabase.buildDatabase(application)
    }

    @Singleton
    @Provides
    fun provideYoutubeLinkExtraContentLocalSource(database: KurobaDatabase): YoutubeLinkExtraContentLocalSource {
        return YoutubeLinkExtraContentLocalSource(database)
    }

}
package com.github.adamantcheese.database.di

import android.app.Application
import com.github.adamantcheese.database.KurobaDatabase
import com.github.adamantcheese.database.common.Logger
import com.github.adamantcheese.database.di.annotation.LoggerTagPrefix
import com.github.adamantcheese.database.di.annotation.VerboseLogs
import com.github.adamantcheese.database.repository.YoutubeLinkExtraContentRepository
import com.github.adamantcheese.database.source.Loadable2LocalSource
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
    fun provideLogger(@VerboseLogs verboseLogs: Boolean): Logger {
        return Logger(verboseLogs)
    }

    /**
     * Local sources
     * */

    @Singleton
    @Provides
    fun provideLoadable2LocalSource(database: KurobaDatabase): Loadable2LocalSource {
        return Loadable2LocalSource(database)
    }

    @Singleton
    @Provides
    fun provideYoutubeLinkExtraContentLocalSource(database: KurobaDatabase): YoutubeLinkExtraContentLocalSource {
        return YoutubeLinkExtraContentLocalSource(database)
    }

    /**
     * Repositories
     * */

    @Singleton
    @Provides
    fun provideYoutubeLinkExtraContentRepository(
            logger: Logger,
            database: KurobaDatabase,
            loadable2LocalSource: Loadable2LocalSource,
            youtubeLinkExtraContentLocalSource: YoutubeLinkExtraContentLocalSource,
            @LoggerTagPrefix loggerTag: String
    ): YoutubeLinkExtraContentRepository {
        return YoutubeLinkExtraContentRepository(
                database,
                loggerTag,
                logger,
                loadable2LocalSource,
                youtubeLinkExtraContentLocalSource
        )
    }
}
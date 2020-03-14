package com.github.adamantcheese.database.di

import android.app.Application
import com.github.adamantcheese.database.KurobaDatabase
import com.github.adamantcheese.database.common.Logger
import com.github.adamantcheese.database.di.annotation.LoggerTagPrefix
import com.github.adamantcheese.database.di.annotation.VerboseLogs
import com.github.adamantcheese.database.repository.MediaServiceLinkExtraContentRepository
import com.github.adamantcheese.database.repository.SeenPostRepository
import com.github.adamantcheese.database.source.local.Loadable2LocalSource
import com.github.adamantcheese.database.source.local.MediaServiceLinkExtraContentLocalSource
import com.github.adamantcheese.database.source.local.SeenPostLocalSource
import com.github.adamantcheese.database.source.remote.MediaServiceLinkExtraContentRemoteSource
import dagger.Module
import dagger.Provides
import okhttp3.OkHttpClient
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
    fun provideMediaServiceLinkExtraContentLocalSource(database: KurobaDatabase): MediaServiceLinkExtraContentLocalSource {
        return MediaServiceLinkExtraContentLocalSource(database)
    }

    @Singleton
    @Provides
    fun provideSeenPostLocalSource(database: KurobaDatabase): SeenPostLocalSource {
        return SeenPostLocalSource(database)
    }

    /**
     * Remote sources
     * */

    @Singleton
    @Provides
    fun provideMediaServiceLinkExtraContentRemoteSource(
            logger: Logger,
            okHttpClient: OkHttpClient,
            @LoggerTagPrefix loggerTag: String
    ): MediaServiceLinkExtraContentRemoteSource {
        return MediaServiceLinkExtraContentRemoteSource(okHttpClient, loggerTag, logger)
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
            mediaServiceLinkExtraContentLocalSource: MediaServiceLinkExtraContentLocalSource,
            mediaServiceLinkExtraContentRemoteSource: MediaServiceLinkExtraContentRemoteSource,
            @LoggerTagPrefix loggerTag: String
    ): MediaServiceLinkExtraContentRepository {
        return MediaServiceLinkExtraContentRepository(
                database,
                loggerTag,
                logger,
                loadable2LocalSource,
                mediaServiceLinkExtraContentLocalSource,
                mediaServiceLinkExtraContentRemoteSource
        )
    }

    @Singleton
    @Provides
    fun provideSeenPostRepository(
            logger: Logger,
            database: KurobaDatabase,
            loadable2LocalSource: Loadable2LocalSource,
            seenPostLocalSource: SeenPostLocalSource,
            @LoggerTagPrefix loggerTag: String
    ): SeenPostRepository {
        return SeenPostRepository(
                database,
                loggerTag,
                logger,
                loadable2LocalSource,
                seenPostLocalSource
        )
    }
}
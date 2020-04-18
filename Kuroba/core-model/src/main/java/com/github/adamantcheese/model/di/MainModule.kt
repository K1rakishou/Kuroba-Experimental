package com.github.adamantcheese.model.di

import android.app.Application
import com.github.adamantcheese.common.AppConstants
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.di.annotation.LoggerTagPrefix
import com.github.adamantcheese.model.di.annotation.VerboseLogs
import com.github.adamantcheese.model.repository.*
import com.github.adamantcheese.model.source.cache.GenericCacheSource
import com.github.adamantcheese.model.source.local.ChanPostLocalSource
import com.github.adamantcheese.model.source.local.InlinedFileInfoLocalSource
import com.github.adamantcheese.model.source.local.MediaServiceLinkExtraContentLocalSource
import com.github.adamantcheese.model.source.local.SeenPostLocalSource
import com.github.adamantcheese.model.source.remote.ArchivesRemoteSource
import com.github.adamantcheese.model.source.remote.InlinedFileInfoRemoteSource
import com.github.adamantcheese.model.source.remote.MediaServiceLinkExtraContentRemoteSource
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
class MainModule {

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

    @Singleton
    @Provides
    fun provideGson(): Gson {
        return Gson().newBuilder().create()
    }

    /**
     * Local sources
     * */

    @Singleton
    @Provides
    fun provideMediaServiceLinkExtraContentLocalSource(
            database: KurobaDatabase,
            @LoggerTagPrefix loggerTag: String,
            logger: Logger
    ): MediaServiceLinkExtraContentLocalSource {
        return MediaServiceLinkExtraContentLocalSource(
                database,
                loggerTag,
                logger
        )
    }

    @Singleton
    @Provides
    fun provideSeenPostLocalSource(
            database: KurobaDatabase,
            @LoggerTagPrefix loggerTag: String,
            logger: Logger
    ): SeenPostLocalSource {
        return SeenPostLocalSource(
                database,
                loggerTag,
                logger
        )
    }

    @Singleton
    @Provides
    fun provideInlinedFileInfoLocalSource(
            database: KurobaDatabase,
            @LoggerTagPrefix loggerTag: String,
            logger: Logger
    ): InlinedFileInfoLocalSource {
        return InlinedFileInfoLocalSource(
                database,
                loggerTag,
                logger
        )
    }

    @Singleton
    @Provides
    fun provideChanPostLocalSource(
            database: KurobaDatabase,
            @LoggerTagPrefix loggerTag: String,
            logger: Logger,
            gson: Gson
    ): ChanPostLocalSource {
        return ChanPostLocalSource(
                database,
                loggerTag,
                logger,
                gson
        )
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

    @Singleton
    @Provides
    fun provideInlinedFileInfoRemoteSource(
            logger: Logger,
            okHttpClient: OkHttpClient,
            @LoggerTagPrefix loggerTag: String
    ): InlinedFileInfoRemoteSource {
        return InlinedFileInfoRemoteSource(okHttpClient, loggerTag, logger)
    }

    @Singleton
    @Provides
    fun provideArchivesRemoteSource(
            logger: Logger,
            okHttpClient: OkHttpClient,
            @LoggerTagPrefix loggerTag: String
    ): ArchivesRemoteSource {
        return ArchivesRemoteSource(okHttpClient, loggerTag, logger)
    }

    /**
     * Repositories
     * */

    @Singleton
    @Provides
    fun provideYoutubeLinkExtraContentRepository(
            logger: Logger,
            database: KurobaDatabase,
            mediaServiceLinkExtraContentLocalSource: MediaServiceLinkExtraContentLocalSource,
            mediaServiceLinkExtraContentRemoteSource: MediaServiceLinkExtraContentRemoteSource,
            @LoggerTagPrefix loggerTag: String
    ): MediaServiceLinkExtraContentRepository {
        return MediaServiceLinkExtraContentRepository(
                database,
                loggerTag,
                logger,
                GenericCacheSource(),
                mediaServiceLinkExtraContentLocalSource,
                mediaServiceLinkExtraContentRemoteSource
        )
    }

    @Singleton
    @Provides
    fun provideSeenPostRepository(
            logger: Logger,
            database: KurobaDatabase,
            seenPostLocalSource: SeenPostLocalSource,
            @LoggerTagPrefix loggerTag: String
    ): SeenPostRepository {
        return SeenPostRepository(
                database,
                loggerTag,
                logger,
                seenPostLocalSource
        )
    }

    @Singleton
    @Provides
    fun provideInlinedFileInfoRepository(
            logger: Logger,
            database: KurobaDatabase,
            inlinedFileInfoLocalSource: InlinedFileInfoLocalSource,
            inlinedFileInfoRemoteSource: InlinedFileInfoRemoteSource,
            @LoggerTagPrefix loggerTag: String
    ): InlinedFileInfoRepository {
        return InlinedFileInfoRepository(
                database,
                loggerTag,
                logger,
                GenericCacheSource(),
                inlinedFileInfoLocalSource,
                inlinedFileInfoRemoteSource
        )
    }

    @Singleton
    @Provides
    fun provideChanPostRepository(
            logger: Logger,
            database: KurobaDatabase,
            chanPostLocalSource: ChanPostLocalSource,
            @LoggerTagPrefix loggerTag: String,
            appConstants: AppConstants
    ): ChanPostRepository {
        return ChanPostRepository(
                database,
                loggerTag,
                logger,
                chanPostLocalSource,
                appConstants
        )
    }

    @Singleton
    @Provides
    fun provideArchivesRepository(
            logger: Logger,
            database: KurobaDatabase,
            archivesRemoteSource: ArchivesRemoteSource,
            @LoggerTagPrefix loggerTag: String
    ): ArchivesRepository {
        return ArchivesRepository(database, loggerTag, logger, archivesRemoteSource)
    }
}
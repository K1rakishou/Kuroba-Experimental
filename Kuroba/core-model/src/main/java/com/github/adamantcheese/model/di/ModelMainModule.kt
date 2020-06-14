package com.github.adamantcheese.model.di

import android.app.Application
import com.github.adamantcheese.common.AppConstants
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.di.annotation.AppCoroutineScope
import com.github.adamantcheese.model.di.annotation.LoggerTagPrefix
import com.github.adamantcheese.model.di.annotation.VerboseLogs
import com.github.adamantcheese.model.parser.ArchivesJsonParser
import com.github.adamantcheese.model.repository.*
import com.github.adamantcheese.model.source.cache.GenericCacheSource
import com.github.adamantcheese.model.source.local.*
import com.github.adamantcheese.model.source.remote.ArchivesRemoteSource
import com.github.adamantcheese.model.source.remote.InlinedFileInfoRemoteSource
import com.github.adamantcheese.model.source.remote.MediaServiceLinkExtraContentRemoteSource
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
class ModelMainModule {

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
   * Parsers
   * */

  @Singleton
  @Provides
  fun provideArchivesJsonParser(
    @LoggerTagPrefix loggerTag: String,
    logger: Logger
  ): ArchivesJsonParser {
    return ArchivesJsonParser(loggerTag, logger)
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

  @Singleton
  @Provides
  fun provideThirdPartyArchiveInfoLocalSource(
    database: KurobaDatabase,
    @LoggerTagPrefix loggerTag: String,
    logger: Logger
  ): ThirdPartyArchiveInfoLocalSource {
    return ThirdPartyArchiveInfoLocalSource(
      database,
      loggerTag,
      logger
    )
  }

  @Singleton
  @Provides
  fun provideNavHistoryLocalSource(
    database: KurobaDatabase,
    @LoggerTagPrefix loggerTag: String,
    logger: Logger
  ): NavHistoryLocalSource {
    return NavHistoryLocalSource(
      database,
      loggerTag,
      logger
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
    @LoggerTagPrefix loggerTag: String,
    archivesJsonParser: ArchivesJsonParser
  ): ArchivesRemoteSource {
    return ArchivesRemoteSource(okHttpClient, loggerTag, logger, archivesJsonParser)
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
    @AppCoroutineScope scope: CoroutineScope,
    @LoggerTagPrefix loggerTag: String
  ): MediaServiceLinkExtraContentRepository {
    return MediaServiceLinkExtraContentRepository(
      database,
      loggerTag,
      logger,
      scope,
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
    @LoggerTagPrefix loggerTag: String,
    @AppCoroutineScope scope: CoroutineScope
  ): SeenPostRepository {
    return SeenPostRepository(
      database,
      loggerTag,
      logger,
      scope,
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
    @AppCoroutineScope scope: CoroutineScope,
    @LoggerTagPrefix loggerTag: String
  ): InlinedFileInfoRepository {
    return InlinedFileInfoRepository(
      database,
      loggerTag,
      logger,
      scope,
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
    @AppCoroutineScope scope: CoroutineScope,
    appConstants: AppConstants
  ): ChanPostRepository {
    return ChanPostRepository(
      database,
      loggerTag,
      logger,
      scope,
      chanPostLocalSource,
      appConstants
    )
  }

  @Singleton
  @Provides
  fun provideThirdPartyArchiveInfoRepository(
    logger: Logger,
    database: KurobaDatabase,
    thirdPartyArchiveInfoLocalSource: ThirdPartyArchiveInfoLocalSource,
    archivesRemoteSource: ArchivesRemoteSource,
    appConstants: AppConstants,
    @AppCoroutineScope scope: CoroutineScope,
    @LoggerTagPrefix loggerTag: String
  ): ThirdPartyArchiveInfoRepository {
    return ThirdPartyArchiveInfoRepository(
      database,
      loggerTag,
      logger,
      appConstants,
      scope,
      thirdPartyArchiveInfoLocalSource,
      archivesRemoteSource
    )
  }

  @Singleton
  @Provides
  fun provideHistoryNavigationRepository(
    logger: Logger,
    database: KurobaDatabase,
    @AppCoroutineScope scope: CoroutineScope,
    @LoggerTagPrefix loggerTag: String,
    navHistoryLocalSource: NavHistoryLocalSource
  ): HistoryNavigationRepository {
    return HistoryNavigationRepository(
      database,
      loggerTag,
      logger,
      scope,
      navHistoryLocalSource
    )
  }
}
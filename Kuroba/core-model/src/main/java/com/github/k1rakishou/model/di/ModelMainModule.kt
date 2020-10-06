package com.github.k1rakishou.model.di

import android.app.Application
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.json.*
import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.common.Logger
import com.github.k1rakishou.model.di.annotation.*
import com.github.k1rakishou.model.parser.ArchivesJsonParser
import com.github.k1rakishou.model.repository.*
import com.github.k1rakishou.model.source.cache.ChanDescriptorCache
import com.github.k1rakishou.model.source.cache.GenericCacheSource
import com.github.k1rakishou.model.source.local.*
import com.github.k1rakishou.model.source.remote.ArchivesRemoteSource
import com.github.k1rakishou.model.source.remote.InlinedFileInfoRemoteSource
import com.github.k1rakishou.model.source.remote.MediaServiceLinkExtraContentRemoteSource
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
  fun provideDatabase(
    application: Application,
    @BetaOrDevBuild betaOrDev: Boolean,
    @LoggerTagPrefix loggerTag: String,
    logger: Logger
  ): KurobaDatabase {
    return KurobaDatabase.buildDatabase(
      application,
      betaOrDev,
      loggerTag,
      logger
    )
  }

  @Singleton
  @Provides
  fun provideLogger(@VerboseLogs verboseLogs: Boolean): Logger {
    return Logger(verboseLogs)
  }

  @Singleton
  @Provides
  fun provideGson(): Gson {
    val gson = Gson().newBuilder()

    val userSettingAdapter = RuntimeTypeAdapterFactory.of(
      JsonSetting::class.java,
      "type"
    ).registerSubtype(StringJsonSetting::class.java, "string")
      .registerSubtype(IntegerJsonSetting::class.java, "integer")
      .registerSubtype(LongJsonSetting::class.java, "long")
      .registerSubtype(BooleanJsonSetting::class.java, "boolean")

    return gson
      .registerTypeAdapterFactory(userSettingAdapter)
      .create()
  }

  @Singleton
  @Provides
  fun provideChanDescriptorCache(database: KurobaDatabase): ChanDescriptorCache {
    return ChanDescriptorCache(database)
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

  @Singleton
  @Provides
  fun provideThreadBookmarkLocalSource(
    database: KurobaDatabase,
    @LoggerTagPrefix loggerTag: String,
    @IsDevFlavor isDevFlavor: Boolean,
    logger: Logger,
    chanDescriptorCache: ChanDescriptorCache
  ): ThreadBookmarkLocalSource {
    return ThreadBookmarkLocalSource(
      database,
      loggerTag,
      isDevFlavor,
      logger,
      chanDescriptorCache
    )
  }

  @Singleton
  @Provides
  fun provideChanThreadViewableInfoLocalSource(
    database: KurobaDatabase,
    @LoggerTagPrefix loggerTag: String,
    @IsDevFlavor isDevFlavor: Boolean,
    logger: Logger,
    chanDescriptorCache: ChanDescriptorCache
  ): ChanThreadViewableInfoLocalSource {
    return ChanThreadViewableInfoLocalSource(
      database,
      loggerTag,
      isDevFlavor,
      logger,
      chanDescriptorCache
    )
  }

  @Singleton
  @Provides
  fun provideSiteLocalSource(
    database: KurobaDatabase,
    @LoggerTagPrefix loggerTag: String,
    @IsDevFlavor isDevFlavor: Boolean,
    logger: Logger,
    chanDescriptorCache: ChanDescriptorCache
  ): SiteLocalSource {
    return SiteLocalSource(
      database,
      loggerTag,
      isDevFlavor,
      logger,
      chanDescriptorCache
    )
  }

  @Singleton
  @Provides
  fun provideBoardLocalSource(
    database: KurobaDatabase,
    @LoggerTagPrefix loggerTag: String,
    @IsDevFlavor isDevFlavor: Boolean,
    logger: Logger,
    chanDescriptorCache: ChanDescriptorCache
  ): BoardLocalSource {
    return BoardLocalSource(
      database,
      loggerTag,
      isDevFlavor,
      logger,
      chanDescriptorCache
    )
  }

  @Singleton
  @Provides
  fun provideChanSavedReplyLocalSource(
    database: KurobaDatabase,
    @LoggerTagPrefix loggerTag: String,
    @IsDevFlavor isDevFlavor: Boolean,
    logger: Logger
  ): ChanSavedReplyLocalSource {
    return ChanSavedReplyLocalSource(
      database,
      loggerTag,
      isDevFlavor,
      logger
    )
  }

  @Singleton
  @Provides
  fun provideChanPostHideLocalSource(
    database: KurobaDatabase,
    @LoggerTagPrefix loggerTag: String,
    @IsDevFlavor isDevFlavor: Boolean,
    logger: Logger
  ): ChanPostHideLocalSource {
    return ChanPostHideLocalSource(
      database,
      loggerTag,
      isDevFlavor,
      logger
    )
  }

  @Singleton
  @Provides
  fun provideChanFilterLocalSource(
    database: KurobaDatabase,
    @LoggerTagPrefix loggerTag: String,
    @IsDevFlavor isDevFlavor: Boolean,
    logger: Logger
  ): ChanFilterLocalSource {
    return ChanFilterLocalSource(
      database,
      loggerTag,
      isDevFlavor,
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
    archivesJsonParser: ArchivesJsonParser,
    appConstants: AppConstants
  ): ArchivesRemoteSource {
    return ArchivesRemoteSource(okHttpClient, loggerTag, logger, archivesJsonParser, appConstants)
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
    @IsDevFlavor isDevFlavor: Boolean,
    @LoggerTagPrefix loggerTag: String,
    @AppCoroutineScope scope: CoroutineScope,
    appConstants: AppConstants
  ): ChanPostRepository {
    return ChanPostRepository(
      database,
      loggerTag,
      logger,
      isDevFlavor,
      scope,
      chanPostLocalSource,
      appConstants
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

  @Singleton
  @Provides
  fun provideBookmarksRepository(
    logger: Logger,
    database: KurobaDatabase,
    @AppCoroutineScope scope: CoroutineScope,
    @LoggerTagPrefix loggerTag: String,
    threadBookmarkLocalSource: ThreadBookmarkLocalSource
  ): BookmarksRepository {
    return BookmarksRepository(
      database,
      loggerTag,
      logger,
      scope,
      threadBookmarkLocalSource
    )
  }

  @Singleton
  @Provides
  fun provideChanThreadViewableInfoRepository(
    logger: Logger,
    database: KurobaDatabase,
    @AppCoroutineScope scope: CoroutineScope,
    @LoggerTagPrefix loggerTag: String,
    chanThreadViewableInfoLocalSource: ChanThreadViewableInfoLocalSource
  ): ChanThreadViewableInfoRepository {
    return ChanThreadViewableInfoRepository(
      database,
      loggerTag,
      logger,
      scope,
      chanThreadViewableInfoLocalSource
    )
  }

  @Singleton
  @Provides
  fun provideSiteRepository(
    logger: Logger,
    database: KurobaDatabase,
    @AppCoroutineScope scope: CoroutineScope,
    @LoggerTagPrefix loggerTag: String,
    siteLocalSource: SiteLocalSource
  ): SiteRepository {
    return SiteRepository(
      database,
      loggerTag,
      logger,
      scope,
      siteLocalSource
    )
  }

  @Singleton
  @Provides
  fun provideBoardRepository(
    logger: Logger,
    database: KurobaDatabase,
    @AppCoroutineScope scope: CoroutineScope,
    @LoggerTagPrefix loggerTag: String,
    boardLocalSource: BoardLocalSource
  ): BoardRepository {
    return BoardRepository(
      database,
      loggerTag,
      logger,
      scope,
      boardLocalSource
    )
  }

  @Singleton
  @Provides
  fun provideChanSavedReplyRepository(
    logger: Logger,
    database: KurobaDatabase,
    @AppCoroutineScope scope: CoroutineScope,
    @LoggerTagPrefix loggerTag: String,
    chanSavedReplyLocalSource: ChanSavedReplyLocalSource
  ): ChanSavedReplyRepository {
    return ChanSavedReplyRepository(
      database,
      loggerTag,
      logger,
      scope,
      chanSavedReplyLocalSource
    )
  }

  @Singleton
  @Provides
  fun provideChanPostHideRepository(
    logger: Logger,
    database: KurobaDatabase,
    @AppCoroutineScope scope: CoroutineScope,
    @LoggerTagPrefix loggerTag: String,
    chanPostHideLocalSource: ChanPostHideLocalSource
  ): ChanPostHideRepository {
    return ChanPostHideRepository(
      database,
      loggerTag,
      logger,
      scope,
      chanPostHideLocalSource
    )
  }

  @Singleton
  @Provides
  fun provideChanFilterRepository(
    logger: Logger,
    database: KurobaDatabase,
    @AppCoroutineScope scope: CoroutineScope,
    @LoggerTagPrefix loggerTag: String,
    chanFilterLocalSource: ChanFilterLocalSource
  ): ChanFilterRepository {
    return ChanFilterRepository(
      database,
      loggerTag,
      logger,
      scope,
      chanFilterLocalSource
    )
  }

}
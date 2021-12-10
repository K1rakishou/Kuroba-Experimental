package com.github.k1rakishou.model.di

import com.github.k1rakishou.json.BooleanJsonSetting
import com.github.k1rakishou.json.IntegerJsonSetting
import com.github.k1rakishou.json.JsonSetting
import com.github.k1rakishou.json.LongJsonSetting
import com.github.k1rakishou.json.RuntimeTypeAdapterFactory
import com.github.k1rakishou.json.StringJsonSetting
import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.repository.BoardRepository
import com.github.k1rakishou.model.repository.BookmarksRepository
import com.github.k1rakishou.model.repository.ChanCatalogSnapshotRepository
import com.github.k1rakishou.model.repository.ChanFilterRepository
import com.github.k1rakishou.model.repository.ChanFilterWatchRepository
import com.github.k1rakishou.model.repository.ChanPostHideRepository
import com.github.k1rakishou.model.repository.ChanPostImageRepository
import com.github.k1rakishou.model.repository.ChanPostRepository
import com.github.k1rakishou.model.repository.ChanSavedReplyRepository
import com.github.k1rakishou.model.repository.ChanThreadViewableInfoRepository
import com.github.k1rakishou.model.repository.CompositeCatalogRepository
import com.github.k1rakishou.model.repository.DatabaseMetaRepository
import com.github.k1rakishou.model.repository.HistoryNavigationRepository
import com.github.k1rakishou.model.repository.ImageDownloadRequestRepository
import com.github.k1rakishou.model.repository.MediaServiceLinkExtraContentRepository
import com.github.k1rakishou.model.repository.SeenPostRepository
import com.github.k1rakishou.model.repository.SiteRepository
import com.github.k1rakishou.model.repository.ThreadBookmarkGroupRepository
import com.github.k1rakishou.model.repository.ThreadDownloadRepository
import com.github.k1rakishou.model.source.cache.ChanCatalogSnapshotCache
import com.github.k1rakishou.model.source.cache.ChanDescriptorCache
import com.github.k1rakishou.model.source.cache.GenericSuspendableCacheSource
import com.github.k1rakishou.model.source.cache.ThreadBookmarkCache
import com.github.k1rakishou.model.source.cache.thread.ChanThreadsCache
import com.github.k1rakishou.model.source.local.BoardLocalSource
import com.github.k1rakishou.model.source.local.ChanCatalogSnapshotLocalSource
import com.github.k1rakishou.model.source.local.ChanFilterLocalSource
import com.github.k1rakishou.model.source.local.ChanFilterWatchLocalSource
import com.github.k1rakishou.model.source.local.ChanPostHideLocalSource
import com.github.k1rakishou.model.source.local.ChanPostImageLocalSource
import com.github.k1rakishou.model.source.local.ChanPostLocalSource
import com.github.k1rakishou.model.source.local.ChanSavedReplyLocalSource
import com.github.k1rakishou.model.source.local.ChanThreadViewableInfoLocalSource
import com.github.k1rakishou.model.source.local.CompositeCatalogLocalSource
import com.github.k1rakishou.model.source.local.DatabaseMetaLocalSource
import com.github.k1rakishou.model.source.local.ImageDownloadRequestLocalSource
import com.github.k1rakishou.model.source.local.MediaServiceLinkExtraContentLocalSource
import com.github.k1rakishou.model.source.local.NavHistoryLocalSource
import com.github.k1rakishou.model.source.local.SeenPostLocalSource
import com.github.k1rakishou.model.source.local.SiteLocalSource
import com.github.k1rakishou.model.source.local.ThreadBookmarkGroupLocalSource
import com.github.k1rakishou.model.source.local.ThreadBookmarkLocalSource
import com.github.k1rakishou.model.source.local.ThreadDownloadLocalSource
import com.github.k1rakishou.model.source.remote.MediaServiceLinkExtraContentRemoteSource
import com.google.gson.Gson
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
class ModelModule {

  @Singleton
  @Provides
  fun provideDatabase(
    dependencies: ModelComponent.Dependencies
  ): KurobaDatabase {
    return KurobaDatabase.buildDatabase(dependencies.application)
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

  @Provides
  @Singleton
  fun provideMoshi(): Moshi {
    return Moshi.Builder()
      .build()
  }

  @Singleton
  @Provides
  fun provideChanDescriptorCache(database: KurobaDatabase): ChanDescriptorCache {
    return ChanDescriptorCache(database)
  }

  @Singleton
  @Provides
  fun provideThreadBookmarkCache(): ThreadBookmarkCache {
    return ThreadBookmarkCache()
  }

  @Singleton
  @Provides
  fun provideChanCatalogSnapshotCache(): ChanCatalogSnapshotCache {
    return ChanCatalogSnapshotCache()
  }

  @Singleton
  @Provides
  fun provideChanThreadsCache(
    dependencies: ModelComponent.Dependencies,
    chanCatalogSnapshotCache: ChanCatalogSnapshotCache
  ): ChanThreadsCache {
    return ChanThreadsCache(
      dependencies.isDevFlavor,
      dependencies.isLowRamDevice,
      dependencies.appConstants.maxPostsCountInPostsCache,
      chanCatalogSnapshotCache
    )
  }

  /**
   * Local sources
   * */

  @Singleton
  @Provides
  fun provideMediaServiceLinkExtraContentLocalSource(
    database: KurobaDatabase,
  ): MediaServiceLinkExtraContentLocalSource {
    return MediaServiceLinkExtraContentLocalSource(
      database
    )
  }

  @Singleton
  @Provides
  fun provideSeenPostLocalSource(
    database: KurobaDatabase
  ): SeenPostLocalSource {
    return SeenPostLocalSource(
      database
    )
  }

  @Singleton
  @Provides
  fun provideChanPostLocalSource(
    database: KurobaDatabase
  ): ChanPostLocalSource {
    return ChanPostLocalSource(
      database
    )
  }

  @Singleton
  @Provides
  fun provideNavHistoryLocalSource(
    database: KurobaDatabase,
    moshi: Moshi
  ): NavHistoryLocalSource {
    return NavHistoryLocalSource(
      database,
      moshi
    )
  }

  @Singleton
  @Provides
  fun provideThreadBookmarkLocalSource(
    dependencies: ModelComponent.Dependencies,
    database: KurobaDatabase,
    chanDescriptorCache: ChanDescriptorCache,
    threadBookmarkCache: ThreadBookmarkCache
  ): ThreadBookmarkLocalSource {
    return ThreadBookmarkLocalSource(
      database,
      dependencies.isDevFlavor,
      chanDescriptorCache,
      threadBookmarkCache
    )
  }

  @Singleton
  @Provides
  fun provideChanThreadViewableInfoLocalSource(
    database: KurobaDatabase,
    chanDescriptorCache: ChanDescriptorCache
  ): ChanThreadViewableInfoLocalSource {
    return ChanThreadViewableInfoLocalSource(
      database,
      chanDescriptorCache
    )
  }

  @Singleton
  @Provides
  fun provideSiteLocalSource(
    database: KurobaDatabase,
    dependencies: ModelComponent.Dependencies,
    chanDescriptorCache: ChanDescriptorCache
  ): SiteLocalSource {
    return SiteLocalSource(
      database,
      dependencies.isDevFlavor,
      chanDescriptorCache
    )
  }

  @Singleton
  @Provides
  fun provideBoardLocalSource(
    database: KurobaDatabase,
    dependencies: ModelComponent.Dependencies,
    chanDescriptorCache: ChanDescriptorCache
  ): BoardLocalSource {
    return BoardLocalSource(
      database,
      dependencies.isDevFlavor,
      chanDescriptorCache
    )
  }

  @Singleton
  @Provides
  fun provideChanSavedReplyLocalSource(
    database: KurobaDatabase,
    dependencies: ModelComponent.Dependencies,
  ): ChanSavedReplyLocalSource {
    return ChanSavedReplyLocalSource(
      database,
      dependencies.isDevFlavor,
    )
  }

  @Singleton
  @Provides
  fun provideChanPostHideLocalSource(
    database: KurobaDatabase,
    dependencies: ModelComponent.Dependencies,
  ): ChanPostHideLocalSource {
    return ChanPostHideLocalSource(
      database,
      dependencies.isDevFlavor,
    )
  }

  @Singleton
  @Provides
  fun provideChanFilterLocalSource(
    database: KurobaDatabase,
    dependencies: ModelComponent.Dependencies,
  ): ChanFilterLocalSource {
    return ChanFilterLocalSource(
      database,
      dependencies.isDevFlavor,
    )
  }

  @Singleton
  @Provides
  fun provideThreadBookmarkGroupLocalSource(
    database: KurobaDatabase,
    moshi: Moshi,
    dependencies: ModelComponent.Dependencies,
    chanDescriptorCache: ChanDescriptorCache
  ): ThreadBookmarkGroupLocalSource {
    return ThreadBookmarkGroupLocalSource(
      database,
      moshi,
      dependencies.isDevFlavor,
      chanDescriptorCache
    )
  }

  @Singleton
  @Provides
  fun provideChanCatalogSnapshotLocalSource(
    database: KurobaDatabase,
    chanDescriptorCache: ChanDescriptorCache,
    chanCatalogSnapshotCache: ChanCatalogSnapshotCache
  ): ChanCatalogSnapshotLocalSource {
    return ChanCatalogSnapshotLocalSource(
      database,
      chanDescriptorCache,
      chanCatalogSnapshotCache
    )
  }

  @Singleton
  @Provides
  fun provideChanFilterWatchLocalSource(
    database: KurobaDatabase,
    chanDescriptorCache: ChanDescriptorCache
  ): ChanFilterWatchLocalSource {
    return ChanFilterWatchLocalSource(
      database,
      chanDescriptorCache
    )
  }

  @Singleton
  @Provides
  fun provideDatabaseMetaLocalSource(
    database: KurobaDatabase
  ): DatabaseMetaLocalSource {
    return DatabaseMetaLocalSource(database)
  }

  @Singleton
  @Provides
  fun provideChanPostImageLocalSource(
    database: KurobaDatabase,
    chanDescriptorCache: ChanDescriptorCache,
  ): ChanPostImageLocalSource {
    return ChanPostImageLocalSource(
      database,
      chanDescriptorCache
    )
  }

  @Singleton
  @Provides
  fun provideImageDownloadRequestLocalSource(
    database: KurobaDatabase
  ): ImageDownloadRequestLocalSource {
    return ImageDownloadRequestLocalSource(database)
  }

  @Singleton
  @Provides
  fun provideThreadDownloadLocalSource(
    database: KurobaDatabase
  ): ThreadDownloadLocalSource {
    return ThreadDownloadLocalSource(database)
  }

  @Singleton
  @Provides
  fun provideCompositeCatalogLocalSource(
    database: KurobaDatabase
  ): CompositeCatalogLocalSource {
    return CompositeCatalogLocalSource(database)
  }

  /**
   * Remote sources
   * */

  @Singleton
  @Provides
  fun provideMediaServiceLinkExtraContentRemoteSource(
    okHttpClient: OkHttpClient,
  ): MediaServiceLinkExtraContentRemoteSource {
    return MediaServiceLinkExtraContentRemoteSource(okHttpClient)
  }

  /**
   * Repositories
   * */

  @Singleton
  @Provides
  fun provideYoutubeLinkExtraContentRepository(
    dependencies: ModelComponent.Dependencies,
    database: KurobaDatabase,
    mediaServiceLinkExtraContentLocalSource: MediaServiceLinkExtraContentLocalSource,
    mediaServiceLinkExtraContentRemoteSource: MediaServiceLinkExtraContentRemoteSource,
  ): MediaServiceLinkExtraContentRepository {
    return MediaServiceLinkExtraContentRepository(
      database,
      dependencies.coroutineScope,
      GenericSuspendableCacheSource(),
      mediaServiceLinkExtraContentLocalSource,
      mediaServiceLinkExtraContentRemoteSource
    )
  }

  @Singleton
  @Provides
  fun provideSeenPostRepository(
    dependencies: ModelComponent.Dependencies,
    database: KurobaDatabase,
    seenPostLocalSource: SeenPostLocalSource,
  ): SeenPostRepository {
    return SeenPostRepository(
      database,
      dependencies.coroutineScope,
      seenPostLocalSource
    )
  }

  @Singleton
  @Provides
  fun provideChanPostRepository(
    dependencies: ModelComponent.Dependencies,
    database: KurobaDatabase,
    chanPostLocalSource: ChanPostLocalSource,
    chanThreadsCache: ChanThreadsCache,
    chanDescriptorCache: ChanDescriptorCache
  ): ChanPostRepository {
    return ChanPostRepository(
      database,
      dependencies.isDevFlavor,
      dependencies.coroutineScope,
      dependencies.appConstants,
      chanPostLocalSource,
      chanThreadsCache,
      chanDescriptorCache
    )
  }

  @Singleton
  @Provides
  fun provideHistoryNavigationRepository(
    dependencies: ModelComponent.Dependencies,
    database: KurobaDatabase,
    navHistoryLocalSource: NavHistoryLocalSource
  ): HistoryNavigationRepository {
    return HistoryNavigationRepository(
      database,
      dependencies.coroutineScope,
      navHistoryLocalSource
    )
  }

  @Singleton
  @Provides
  fun provideBookmarksRepository(
    dependencies: ModelComponent.Dependencies,
    database: KurobaDatabase,
    threadBookmarkLocalSource: ThreadBookmarkLocalSource
  ): BookmarksRepository {
    return BookmarksRepository(
      database,
      dependencies.coroutineScope,
      threadBookmarkLocalSource
    )
  }

  @Singleton
  @Provides
  fun provideChanThreadViewableInfoRepository(
    dependencies: ModelComponent.Dependencies,
    database: KurobaDatabase,
    chanThreadViewableInfoLocalSource: ChanThreadViewableInfoLocalSource
  ): ChanThreadViewableInfoRepository {
    return ChanThreadViewableInfoRepository(
      database,
      dependencies.coroutineScope,
      chanThreadViewableInfoLocalSource
    )
  }

  @Singleton
  @Provides
  fun provideSiteRepository(
    dependencies: ModelComponent.Dependencies,
    database: KurobaDatabase,
    siteLocalSource: SiteLocalSource
  ): SiteRepository {
    return SiteRepository(
      database,
      dependencies.coroutineScope,
      siteLocalSource
    )
  }

  @Singleton
  @Provides
  fun provideBoardRepository(
    dependencies: ModelComponent.Dependencies,
    database: KurobaDatabase,
    boardLocalSource: BoardLocalSource
  ): BoardRepository {
    return BoardRepository(
      database,
      dependencies.coroutineScope,
      boardLocalSource
    )
  }

  @Singleton
  @Provides
  fun provideChanSavedReplyRepository(
    dependencies: ModelComponent.Dependencies,
    database: KurobaDatabase,
    chanSavedReplyLocalSource: ChanSavedReplyLocalSource
  ): ChanSavedReplyRepository {
    return ChanSavedReplyRepository(
      database,
      dependencies.coroutineScope,
      chanSavedReplyLocalSource
    )
  }

  @Singleton
  @Provides
  fun provideChanPostHideRepository(
    dependencies: ModelComponent.Dependencies,
    database: KurobaDatabase,
    chanPostHideLocalSource: ChanPostHideLocalSource
  ): ChanPostHideRepository {
    return ChanPostHideRepository(
      database,
      dependencies.coroutineScope,
      chanPostHideLocalSource
    )
  }

  @Singleton
  @Provides
  fun provideChanFilterRepository(
    dependencies: ModelComponent.Dependencies,
    database: KurobaDatabase,
    chanFilterLocalSource: ChanFilterLocalSource
  ): ChanFilterRepository {
    return ChanFilterRepository(
      database,
      dependencies.coroutineScope,
      chanFilterLocalSource
    )
  }

  @Singleton
  @Provides
  fun provideThreadBookmarkGroupRepository(
    dependencies: ModelComponent.Dependencies,
    database: KurobaDatabase,
    localSource: ThreadBookmarkGroupLocalSource
  ): ThreadBookmarkGroupRepository {
    return ThreadBookmarkGroupRepository(
      database,
      dependencies.coroutineScope,
      localSource
    )
  }

  @Singleton
  @Provides
  fun provideChanCatalogSnapshotRepository(
    dependencies: ModelComponent.Dependencies,
    database: KurobaDatabase,
    localSource: ChanCatalogSnapshotLocalSource
  ): ChanCatalogSnapshotRepository {
    return ChanCatalogSnapshotRepository(
      database,
      dependencies.verboseLogs,
      dependencies.coroutineScope,
      localSource
    )
  }

  @Singleton
  @Provides
  fun provideChanFilterWatchRepository(
    database: KurobaDatabase,
    dependencies: ModelComponent.Dependencies,
    localSource: ChanFilterWatchLocalSource
  ): ChanFilterWatchRepository {
    return ChanFilterWatchRepository(
      database,
      dependencies.coroutineScope,
      localSource
    )
  }

  @Singleton
  @Provides
  fun provideDatabaseMetaRepository(
    database: KurobaDatabase,
    dependencies: ModelComponent.Dependencies,
    localSource: DatabaseMetaLocalSource
  ): DatabaseMetaRepository {
    return DatabaseMetaRepository(
      database,
      dependencies.coroutineScope,
      localSource
    )
  }

  @Singleton
  @Provides
  fun provideChanPostImageRepository(
    database: KurobaDatabase,
    dependencies: ModelComponent.Dependencies,
    localSource: ChanPostImageLocalSource
  ): ChanPostImageRepository {
    return ChanPostImageRepository(
      database,
      dependencies.isDevFlavor,
      dependencies.coroutineScope,
      localSource
    )
  }

  @Singleton
  @Provides
  fun provideImageDownloadRequestRepository(
    database: KurobaDatabase,
    dependencies: ModelComponent.Dependencies,
    localSource: ImageDownloadRequestLocalSource
  ): ImageDownloadRequestRepository {
    return ImageDownloadRequestRepository(
      database,
      dependencies.coroutineScope,
      localSource
    )
  }

  @Singleton
  @Provides
  fun provideThreadDownloadRepository(
    database: KurobaDatabase,
    dependencies: ModelComponent.Dependencies,
    localSource: ThreadDownloadLocalSource
  ): ThreadDownloadRepository {
    return ThreadDownloadRepository(
      database,
      dependencies.coroutineScope,
      localSource
    )
  }

  @Singleton
  @Provides
  fun provideCompositeCatalogRepository(
    database: KurobaDatabase,
    dependencies: ModelComponent.Dependencies,
    localSource: CompositeCatalogLocalSource
  ): CompositeCatalogRepository {
    return CompositeCatalogRepository(
      database,
      dependencies.coroutineScope,
      localSource
    )
  }

}
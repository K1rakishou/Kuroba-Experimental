package com.github.k1rakishou.model.di

import android.app.Application
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.dns.DnsOverHttpsSelectorFactory
import com.github.k1rakishou.common.dns.NormalDnsSelectorFactory
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
import com.github.k1rakishou.model.source.cache.thread.ChanThreadsCache
import com.google.gson.Gson
import dagger.BindsInstance
import dagger.Component
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Singleton
@Component(
  modules = [
    NetworkModule::class,
    ModelModule::class
  ]
)
interface ModelComponent {
  fun getGson(): Gson
  fun getDatabaseMetaRepository(): DatabaseMetaRepository
  fun getMediaServiceLinkExtraContentRepository(): MediaServiceLinkExtraContentRepository
  fun getSeenPostRepository(): SeenPostRepository
  fun getChanPostRepository(): ChanPostRepository
  fun getHistoryNavigationRepository(): HistoryNavigationRepository
  fun getBookmarksRepository(): BookmarksRepository
  fun getChanThreadViewableInfoRepository(): ChanThreadViewableInfoRepository
  fun getSiteRepository(): SiteRepository
  fun getBoardRepository(): BoardRepository
  fun getChanSavedReplyRepository(): ChanSavedReplyRepository
  fun getChanPostHideRepository(): ChanPostHideRepository
  fun getChanFilterRepository(): ChanFilterRepository
  fun getThreadBookmarkGroupRepository(): ThreadBookmarkGroupRepository
  fun getChanCatalogSnapshotRepository(): ChanCatalogSnapshotRepository
  fun getChanFilterWatchRepository(): ChanFilterWatchRepository
  fun getChanPostImageRepository(): ChanPostImageRepository
  fun getChanThreadsCache(): ChanThreadsCache
  fun getImageDownloadRequestRepository(): ImageDownloadRequestRepository
  fun getThreadDownloadRepository(): ThreadDownloadRepository
  fun getChanCatalogSnapshotCache(): ChanCatalogSnapshotCache
  fun getCompositeCatalogRepository(): CompositeCatalogRepository

  @Component.Builder
  interface Builder {
    @BindsInstance
    fun dependencies(deps: Dependencies): Builder
    fun build(): ModelComponent
  }

  class Dependencies(
    val application: Application,
    val coroutineScope: CoroutineScope,
    val verboseLogs: Boolean,
    val isDevFlavor: Boolean,
    val isLowRamDevice: Boolean,
    val okHttpUseDnsOverHttps: Boolean,
    val normalDnsSelectorFactory: NormalDnsSelectorFactory,
    val dnsOverHttpsSelectorFactory: DnsOverHttpsSelectorFactory,
    val okHttpProtocols: NetworkModule.OkHttpProtocolList,
    val appConstants: AppConstants
  )

}
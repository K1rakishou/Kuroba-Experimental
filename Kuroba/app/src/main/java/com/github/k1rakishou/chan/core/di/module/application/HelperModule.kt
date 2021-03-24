package com.github.k1rakishou.chan.core.di.module.application

import android.content.Context
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.cache.FileCacheV2
import com.github.k1rakishou.chan.core.helper.FilterEngine
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.manager.SavedReplyManager
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.core.site.loader.ChanThreadLoaderCoordinator
import com.github.k1rakishou.chan.ui.helper.picker.ImagePickHelper
import com.github.k1rakishou.chan.ui.helper.picker.LocalFilePicker
import com.github.k1rakishou.chan.ui.helper.picker.RemoteFilePicker
import com.github.k1rakishou.chan.ui.helper.picker.ShareFilePicker
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.model.repository.ChanCatalogSnapshotRepository
import com.github.k1rakishou.model.repository.ChanPostRepository
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
class HelperModule {

  @Provides
  @Singleton
  fun provideChanThreadLoaderCoordinator(
    proxiedOkHttpClient: RealProxiedOkHttpClient,
    savedReplyManager: SavedReplyManager,
    filterEngine: FilterEngine,
    chanPostRepository: ChanPostRepository,
    chanCatalogSnapshotRepository: ChanCatalogSnapshotRepository,
    appConstants: AppConstants,
    postFilterManager: PostFilterManager,
    boardManager: BoardManager,
    siteResolver: SiteResolver
  ): ChanThreadLoaderCoordinator {
    return ChanThreadLoaderCoordinator(
      proxiedOkHttpClient,
      savedReplyManager,
      filterEngine,
      chanPostRepository,
      chanCatalogSnapshotRepository,
      appConstants,
      postFilterManager,
      ChanSettings.verboseLogs.get(),
      boardManager,
      siteResolver
    )
  }

  @Provides
  @Singleton
  fun provideShareFilePicker(
    appConstants: AppConstants,
    appContext: Context,
    fileManager: FileManager,
    replyManager: ReplyManager
  ): ShareFilePicker {
    return ShareFilePicker(
      appConstants,
      fileManager,
      replyManager,
      appContext
    )
  }

  @Provides
  @Singleton
  fun provideLocalFilePicker(
    appConstants: AppConstants,
    fileManager: FileManager,
    replyManager: ReplyManager,
    applicationScope: CoroutineScope
  ): LocalFilePicker {
    return LocalFilePicker(
      appConstants,
      fileManager,
      replyManager,
      applicationScope
    )
  }

  @Provides
  @Singleton
  fun provideRemoteFilePicker(
    applicationScope: CoroutineScope,
    appConstants: AppConstants,
    fileCacheV2: FileCacheV2,
    fileManager: FileManager,
    replyManager: ReplyManager,
    cacheHandler: CacheHandler
  ): RemoteFilePicker {
    return RemoteFilePicker(
      appConstants,
      fileManager,
      replyManager,
      applicationScope,
      fileCacheV2,
      cacheHandler
    )
  }

  @Provides
  @Singleton
  fun provideImagePickHelper(
    appContext: Context,
    replyManager: ReplyManager,
    imageLoaderV2: ImageLoaderV2,
    shareFilePicker: ShareFilePicker,
    localFilePicker: LocalFilePicker,
    remoteFilePicker: RemoteFilePicker
  ): ImagePickHelper {
    return ImagePickHelper(
      appContext,
      replyManager,
      imageLoaderV2,
      shareFilePicker,
      localFilePicker,
      remoteFilePicker
    )
  }

}
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
import com.github.k1rakishou.chan.core.site.loader.ChanThreadLoaderCoordinator
import com.github.k1rakishou.chan.ui.helper.picker.ImagePickHelper
import com.github.k1rakishou.chan.ui.helper.picker.LocalFilePicker
import com.github.k1rakishou.chan.ui.helper.picker.RemoteFilePicker
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.core_logger.Logger
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
    boardManager: BoardManager
  ): ChanThreadLoaderCoordinator {
    Logger.d(AppModule.DI_TAG, "ChanThreadLoaderCoordinator")

    return ChanThreadLoaderCoordinator(
      proxiedOkHttpClient,
      savedReplyManager,
      filterEngine,
      chanPostRepository,
      chanCatalogSnapshotRepository,
      appConstants,
      postFilterManager,
      ChanSettings.verboseLogs.get(),
      boardManager
    )
  }

  @Provides
  @Singleton
  fun provideLocalFilePicker(
    applicationScope: CoroutineScope,
    appConstants: AppConstants,
    replyManager: ReplyManager,
    fileManager: FileManager
  ): LocalFilePicker {
    Logger.d(AppModule.DI_TAG, "LocalFilePicker")
    return LocalFilePicker(
      applicationScope,
      appConstants,
      replyManager,
      fileManager
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
    Logger.d(AppModule.DI_TAG, "RemoteFilePicker")
    return RemoteFilePicker(
      applicationScope,
      appConstants,
      fileCacheV2,
      fileManager,
      cacheHandler,
      replyManager
    )
  }

  @Provides
  @Singleton
  fun provideImagePickHelper(
    appContext: Context,
    appConstants: AppConstants,
    replyManager: ReplyManager,
    imageLoaderV2: ImageLoaderV2,
    localFilePicker: LocalFilePicker,
    remoteFilePicker: RemoteFilePicker
  ): ImagePickHelper {
    Logger.d(AppModule.DI_TAG, "ImagePickHelper")

    return ImagePickHelper(
      appContext,
      appConstants,
      replyManager,
      imageLoaderV2,
      localFilePicker,
      remoteFilePicker
    )
  }

}
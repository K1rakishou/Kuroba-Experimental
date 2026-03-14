package com.github.k1rakishou.chan.core.di.module.application

import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.repository.BoardFlagInfoRepository
import com.github.k1rakishou.chan.core.repository.CurrentlyDisplayedCatalogPostsRepository
import com.github.k1rakishou.chan.core.repository.ImportExportRepository
import com.github.k1rakishou.chan.core.repository.ThemeJsonFilesRepository
import com.github.k1rakishou.chan.core.site.ParserRepository
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.core.usecase.DownloadThemeJsonFilesUseCase
import com.github.k1rakishou.chan.core.usecase.ExportBackupFileUseCase
import com.github.k1rakishou.chan.core.usecase.ImportBackupFileUseCase
import com.github.k1rakishou.chan.core.usecase.LoadBoardFlagsUseCase
import com.github.k1rakishou.chan.features.posting.LastReplyRepository
import com.github.k1rakishou.chan.features.view.media.helper.ChanPostBackgroundColorStorage
import com.github.k1rakishou.core_logger.Logger.deps
import com.github.k1rakishou.v2.KurobaSettings
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class RepositoryModule {
  @Provides
  @Singleton
  fun provideImportExportRepository(
    exportBackupFileUseCase: ExportBackupFileUseCase,
    importBackupFileUseCase: ImportBackupFileUseCase
  ): ImportExportRepository {
    deps("ImportExportRepository")
    return ImportExportRepository(
      exportBackupFileUseCase,
      importBackupFileUseCase
    )
  }

  @Provides
  @Singleton
  fun provideParserRepository(
    kurobaSettings: KurobaSettings,
    archivesManager: ArchivesManager
  ): ParserRepository {
    deps("ParserRepository")
    return ParserRepository(
      kurobaSettings,
      archivesManager
    )
  }

  @Provides
  @Singleton
  fun provideLastReplyRepository(
    siteManager: SiteManager,
    boardManager: BoardManager
  ): LastReplyRepository {
    deps("LastReplyRepository")
    return LastReplyRepository(siteManager, boardManager)
  }

  @Provides
  @Singleton
  fun provideStaticBoardFlagInfoRepository(
    siteManager: SiteManager,
    boardManager: BoardManager,
    loadBoardFlagsUseCase: LoadBoardFlagsUseCase
  ): BoardFlagInfoRepository {
    deps("BoardFlagInfoRepository")
    return BoardFlagInfoRepository(
      siteManager,
      boardManager,
      loadBoardFlagsUseCase
    )
  }

  @Provides
  @Singleton
  fun provideDownloadThemeJsonFilesRepository(
    downloadThemeJsonFilesUseCase: DownloadThemeJsonFilesUseCase
  ): ThemeJsonFilesRepository {
    deps("DownloadThemeJsonFilesRepository")
    return ThemeJsonFilesRepository(downloadThemeJsonFilesUseCase)
  }

  @Provides
  @Singleton
  fun provideChanPostBackgroundColorStorage(
    boardManager: BoardManager,
    siteResolver: SiteResolver
  ): ChanPostBackgroundColorStorage {
    deps("ChanPostBackgroundColorStorage")
    return ChanPostBackgroundColorStorage(boardManager, siteResolver)
  }

  @Provides
  @Singleton
  fun provideCurrentlyDisplayedPostsRepository(): CurrentlyDisplayedCatalogPostsRepository {
    deps("CurrentlyDisplayedPostsRepository")
    return CurrentlyDisplayedCatalogPostsRepository()
  }
}

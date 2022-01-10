package com.github.k1rakishou.chan.core

import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.ChanFilterManager
import com.github.k1rakishou.chan.core.manager.HistoryNavigationManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.manager.ThreadBookmarkGroupManager
import com.github.k1rakishou.chan.core.watcher.BookmarkWatcherCoordinator
import com.github.k1rakishou.chan.core.watcher.FilterWatcherCoordinator
import com.github.k1rakishou.chan.features.thread_downloading.ThreadDownloadingCoordinator
import com.github.k1rakishou.model.data.site.ChanSiteData
import kotlinx.coroutines.CompletableDeferred

class AppDependenciesInitializer(
  private val siteManager: SiteManager,
  private val boardManager: BoardManager,
  private val bookmarksManager: BookmarksManager,
  private val threadBookmarkGroupManager: ThreadBookmarkGroupManager,
  private val historyNavigationManager: HistoryNavigationManager,
  private val bookmarkWatcherCoordinator: BookmarkWatcherCoordinator,
  private val filterWatcherCoordinator: FilterWatcherCoordinator,
  private val archivesManager: ArchivesManager,
  private val chanFilterManager: ChanFilterManager,
  private val threadDownloadingCoordinator: ThreadDownloadingCoordinator
) {

  fun init() {
    val allSitesDeferred = CompletableDeferred<List<ChanSiteData>>()

    siteManager.initialize(allSitesDeferred)
    boardManager.initialize(allSitesDeferred)

    // threadBookmarkGroupManager must be initialized before bookmarksManager because it listens
    // for events from bookmarksManager
    threadBookmarkGroupManager.initialize()
    bookmarksManager.initialize()
    historyNavigationManager.initialize()

    bookmarkWatcherCoordinator.initialize()
    filterWatcherCoordinator.initialize()
    threadDownloadingCoordinator.initialize()

    archivesManager.initialize()
    chanFilterManager.initialize()
  }

}
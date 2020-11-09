/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.core.presenter

import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.HistoryNavigationManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.util.ChanPostUtils
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import javax.inject.Inject

class BrowsePresenter @Inject constructor(
  private val historyNavigationManager: HistoryNavigationManager,
  private val bookmarksManager: BookmarksManager,
  private val siteManager: SiteManager,
  private val boardManager: BoardManager,
  private val chanThreadManager: ChanThreadManager
) {
  private var callback: Callback? = null
  private val compositeDisposable = CompositeDisposable()
  private var currentOpenedBoard: BoardDescriptor? = null

  fun create(controllerScope: CoroutineScope, callback: Callback?) {
    this.callback = callback

    controllerScope.launch {
      boardManager.listenForCurrentSelectedBoard()
        .asFlow()
        .collect { currentBoard ->
          val boardDescriptor = currentBoard.boardDescriptor

          if (currentOpenedBoard == boardDescriptor) {
            return@collect
          }

          if (boardDescriptor == null) {
            callback?.showSitesNotSetup()
          } else {
            callback?.loadBoard(boardDescriptor)
          }
        }
    }
  }

  fun destroy() {
    callback = null
    compositeDisposable.clear()
  }

  suspend fun loadWithDefaultBoard() {
    var firstActiveBoardDescriptor: BoardDescriptor? = null

    siteManager.viewActiveSitesOrderedWhile { chanSiteData, _ ->
      val boardDescriptor = boardManager.firstBoardDescriptor(chanSiteData.siteDescriptor)
      if (boardDescriptor != null) {
        firstActiveBoardDescriptor = boardDescriptor
        return@viewActiveSitesOrderedWhile false
      }

      return@viewActiveSitesOrderedWhile true
    }

    if (firstActiveBoardDescriptor != null) {
      loadBoard(firstActiveBoardDescriptor!!)
    } else {
      callback?.showSitesNotSetup()
    }
  }

  suspend fun loadBoard(boardDescriptor: BoardDescriptor) {
    if (callback == null) {
      return
    }

    currentOpenedBoard = boardDescriptor
    callback?.loadBoard(boardDescriptor)
  }

  fun bookmarkEveryThread(chanDescriptor: ChanDescriptor?) {
    if (chanDescriptor == null) {
      Logger.e(TAG, "bookmarkEveryThread() chanDescriptor == null")
      return
    }

    if (chanDescriptor is ChanDescriptor.ThreadDescriptor) {
      Logger.e(TAG, "bookmarkEveryThread() chanDescriptor is not catalog descriptor")
      return
    }

    chanDescriptor as ChanDescriptor.CatalogDescriptor
    val simpleThreadBookmarkList = mutableListOf<BookmarksManager.SimpleThreadBookmark>()

    val catalog = chanThreadManager.getChanCatalog(chanDescriptor)
    if (catalog == null) {
      Logger.e(TAG, "bookmarkEveryThread() Couldn't find catalog by descriptor: $chanDescriptor")
      return
    }

    catalog.iteratePostsOrdered { chanOriginalPost ->
      val threadDescriptor = chanDescriptor.toThreadDescriptor(chanOriginalPost.postNo())
      val title = ChanPostUtils.getTitle(chanOriginalPost, threadDescriptor)

      if (bookmarksManager.exists(threadDescriptor)) {
        Logger.d(TAG, "bookmarkEveryThread() bookmark for post ${title.take(50)} already exist")
        return@iteratePostsOrdered
      }

      val thumbnailUrl = chanOriginalPost.firstImage()?.actualThumbnailUrl

      simpleThreadBookmarkList += BookmarksManager.SimpleThreadBookmark(
        threadDescriptor = threadDescriptor,
        title = title,
        thumbnailUrl = thumbnailUrl
      )
    }

    bookmarksManager.createBookmarks(simpleThreadBookmarkList)
    Logger.d(TAG, "bookmarkEveryThread() done")
  }

  interface Callback {
    suspend fun loadBoard(boardDescriptor: BoardDescriptor)
    suspend fun showSitesNotSetup()
  }

  companion object {
    private const val TAG = "BrowsePresenter"
  }
}
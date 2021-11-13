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
package com.github.k1rakishou.chan.core.manager

import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.net.JsonReaderRequest
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.board.pages.BoardPage
import com.github.k1rakishou.model.data.board.pages.BoardPages
import com.github.k1rakishou.model.data.board.pages.ThreadNoTimeModPair
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.TimeUnit
import kotlin.collections.HashSet
import kotlin.coroutines.CoroutineContext

class PageRequestManager(
  private val siteManager: SiteManager,
  private val boardManager: BoardManager
) : CoroutineScope {
  private val requestedBoards = Collections.synchronizedSet(HashSet<BoardDescriptor>())
  private val savedBoards = Collections.synchronizedSet(HashSet<BoardDescriptor>())
  private val boardPagesMap: ConcurrentMap<BoardDescriptor, BoardPages> = ConcurrentHashMap()
  private val boardTimeMap: ConcurrentMap<BoardDescriptor, Long> = ConcurrentHashMap()
  private val notifyIntervals = ConcurrentHashMap<ChanDescriptor.ThreadDescriptor, Long>()

  private val _boardPagesUpdateFlow = MutableSharedFlow<BoardDescriptor>(extraBufferCapacity = 32)
  val boardPagesUpdateFlow: SharedFlow<BoardDescriptor>
    get() = _boardPagesUpdateFlow.asSharedFlow()

  override val coroutineContext: CoroutineContext
    get() = Dispatchers.Default + SupervisorJob() + CoroutineName("PageRequestManager")

  fun getBoardPages(boardDescriptor: BoardDescriptor, requestPagesIfNotCached: Boolean = true): BoardPages? {
    return getPages(
      boardDescriptor = boardDescriptor,
      requestPagesIfNotCached = requestPagesIfNotCached
    )
  }

  fun getPage(originalPostDescriptor: PostDescriptor?, requestPagesIfNotCached: Boolean = true): BoardPage? {
    if (originalPostDescriptor == null) {
      return null
    }

    if (!pagesRequestsSupported(originalPostDescriptor.boardDescriptor().siteDescriptor)) {
      return null
    }

    return findPage(
      boardDescriptor = originalPostDescriptor.boardDescriptor(),
      opNo = originalPostDescriptor.postNo,
      requestPagesIfNotCached = requestPagesIfNotCached
    )
  }

  fun getPage(threadDescriptor: ChanDescriptor.ThreadDescriptor?, requestPagesIfNotCached: Boolean = true): BoardPage? {
    if (threadDescriptor == null || threadDescriptor.threadNo < 0) {
      return null
    }

    if (!pagesRequestsSupported(threadDescriptor.boardDescriptor().siteDescriptor)) {
      return null
    }

    return findPage(
      boardDescriptor = threadDescriptor.boardDescriptor,
      opNo = threadDescriptor.threadNo,
      requestPagesIfNotCached = requestPagesIfNotCached
    )
  }

  fun getThreadNoTimeModPairList(
    threadDescriptorsToFind: Set<ChanDescriptor.ThreadDescriptor>
  ): Set<ThreadNoTimeModPair> {
    val threadNoTimeModPairSet = mutableSetOf<ThreadNoTimeModPair>()
    val threadDescriptorsToFindCopy = HashSet(threadDescriptorsToFind)

    for (td in threadDescriptorsToFind) {
      val catalog = boardPagesMap[td.boardDescriptor]
        ?: continue

      loop@ for (boardPage in catalog.boardPages) {
        for ((threadDescriptor, lastModified) in boardPage.threads) {
          if (threadDescriptor in threadDescriptorsToFindCopy) {
            threadNoTimeModPairSet += ThreadNoTimeModPair(threadDescriptor, lastModified)
            threadDescriptorsToFindCopy.remove(threadDescriptor)
            break@loop
          }
        }
      }
    }

    return threadNoTimeModPairSet
  }

  @Synchronized
  fun canAlertAboutThreadBeingOnLastPage(threadDescriptor: ChanDescriptor.ThreadDescriptor): Boolean {
    val boardPage = findPage(
      boardDescriptor = threadDescriptor.boardDescriptor,
      opNo = threadDescriptor.threadNo,
      requestPagesIfNotCached = true
    ) ?: return false

    if (!boardPage.isLastPage()) {
      return false
    }

    val now = System.currentTimeMillis()
    val lastNotifyTime = notifyIntervals[threadDescriptor] ?: -1L

    if (lastNotifyTime < 0) {
      notifyIntervals[threadDescriptor] = now
      return true
    }

    if (now - lastNotifyTime < LAST_PAGE_NOTIFICATION_INTERVAL) {
      return false
    }

    notifyIntervals[threadDescriptor] = now
    return true
  }

  fun forceUpdateForBoard(boardDescriptor: BoardDescriptor) {
    if (!pagesRequestsSupported(boardDescriptor.siteDescriptor)) {
      return
    }

    Logger.d(TAG, "Requesting existing board pages for /${boardDescriptor.boardCode}/, forced")
    launch { requestBoardInternal(boardDescriptor) }
  }

  private fun findPage(boardDescriptor: BoardDescriptor, opNo: Long, requestPagesIfNotCached: Boolean): BoardPage? {
    val pages = getPages(boardDescriptor, requestPagesIfNotCached)
      ?: return null

    for (page in pages.boardPages) {
      for ((threadDescriptor, _) in page.threads) {
        if (opNo == threadDescriptor.threadNo) {
          return page
        }
      }
    }

    return null
  }

  private fun getPages(boardDescriptor: BoardDescriptor, requestPagesIfNotCached: Boolean): BoardPages? {
    if (ChanSettings.neverShowPages.get()) {
      return null
    }

    if (savedBoards.contains(boardDescriptor)) {
      if (requestPagesIfNotCached) {
        // If we have it stored already, return the pages for it
        // also issue a new request if 3 minutes have passed
        shouldUpdate(boardDescriptor)
      }

      return boardPagesMap[boardDescriptor]
    }

    if (!requestPagesIfNotCached) {
      return null
    }

    val alreadyRequested = synchronized(this) {
      requestedBoards.contains(boardDescriptor)
    }

    if (alreadyRequested) {
      return null
    }

    launch {
      // Otherwise, get the site for the board and request the pages for it
      requestBoardInternal(boardDescriptor)
    }

    return null
  }

  private fun shouldUpdate(boardDescriptor: BoardDescriptor) {
    if (ChanSettings.neverShowPages.get()) {
      return
    }

    launch {
      siteManager.awaitUntilInitialized()

      val site = siteManager.bySiteDescriptor(boardDescriptor.siteDescriptor)
      if (site == null) {
        Logger.e(TAG, "Couldn't find site by siteDescriptor (${boardDescriptor.siteDescriptor})")
        return@launch
      }

      boardManager.awaitUntilInitialized()

      val board = boardManager.byBoardDescriptor(boardDescriptor)
      if (board == null) {
        Logger.e(TAG, "Couldn't find board by siteDescriptor (${boardDescriptor.siteDescriptor}) " +
          "and boardCode (${boardDescriptor.boardCode})")
        return@launch
      }

      val lastUpdate = boardTimeMap[board.boardDescriptor]
      val lastUpdateTime = lastUpdate ?: 0L

      if (lastUpdateTime + UPDATE_INTERVAL <= System.currentTimeMillis()) {
        requestBoardInternal(boardDescriptor)
      }
    }
  }

  private suspend fun requestBoardInternal(boardDescriptor: BoardDescriptor) {
    val contains = synchronized(this) {
      if (!requestedBoards.contains(boardDescriptor)) {
        requestedBoards.add(boardDescriptor)
        false
      } else {
        true
      }
    }

    if (contains) {
      return
    }

    try {
      Logger.d(TAG, "Requesting new board pages for /${boardDescriptor.boardCode}/")

      siteManager.awaitUntilInitialized()

      val site = siteManager.bySiteDescriptor(boardDescriptor.siteDescriptor)
      if (site == null) {
        Logger.e(TAG, "Couldn't find site by siteDescriptor (${boardDescriptor.siteDescriptor})")
        return
      }

      boardManager.awaitUntilInitialized()

      val board = boardManager.byBoardDescriptor(boardDescriptor)
      if (board == null) {
        Logger.e(TAG, "Couldn't find board by siteDescriptor (${boardDescriptor.siteDescriptor}) " +
          "and boardCode (${boardDescriptor.boardCode})")
        return
      }

      when (val response = site.actions().pages(board)) {
        is JsonReaderRequest.JsonReaderResponse.Success -> {
          onPagesReceived(response.result.boardDescriptor, response.result)
        }
        is JsonReaderRequest.JsonReaderResponse.ServerError -> {
          Logger.e(TAG, "Server error while trying to get board ($board) pages, " +
              "status code: ${response.statusCode}")
        }
        is JsonReaderRequest.JsonReaderResponse.UnknownServerError -> {
          Logger.e(TAG, "Unknown server error while trying to get board (${board}) pages", response.error)
        }
        is JsonReaderRequest.JsonReaderResponse.ParsingError -> {
          Logger.e(TAG, "Parsing error while trying to get board (${board}) pages", response.error)
        }
        null -> {
          // no-op
        }
      }
    } finally {
      synchronized(this) { requestedBoards.remove(boardDescriptor) }
    }
  }

  @Synchronized
  private fun onPagesReceived(
    boardDescriptor: BoardDescriptor,
    pages: BoardPages
  ) {
    Logger.d(TAG, "Got pages for ${boardDescriptor.siteName()}/${boardDescriptor.boardCode}/")

    savedBoards.add(boardDescriptor)
    boardTimeMap[boardDescriptor] = System.currentTimeMillis()
    boardPagesMap[boardDescriptor] = pages

    _boardPagesUpdateFlow.tryEmit(boardDescriptor)
  }

  private fun pagesRequestsSupported(siteDescriptor: SiteDescriptor): Boolean {
    return siteDescriptor.is4chan() || siteDescriptor.isDvach()
  }

  companion object {
    private const val TAG = "PageRequestManager"

    private val UPDATE_INTERVAL = TimeUnit.MINUTES.toMillis(5)
    private val LAST_PAGE_NOTIFICATION_INTERVAL = TimeUnit.MINUTES.toMillis(5)
  }
}
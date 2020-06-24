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
package com.github.adamantcheese.chan.core.manager

import com.github.adamantcheese.chan.core.database.DatabaseBoardManager
import com.github.adamantcheese.chan.core.database.DatabaseManager
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.net.JsonReaderRequest
import com.github.adamantcheese.chan.core.repository.SiteRepository
import com.github.adamantcheese.chan.core.site.sites.chan4.Chan4PagesRequest
import com.github.adamantcheese.chan.core.site.sites.chan4.Chan4PagesRequest.BoardPage
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.model.data.descriptor.BoardDescriptor
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

class PageRequestManager(
  private val databaseManager: DatabaseManager,
  private val databaseBoardManager: DatabaseBoardManager,
  private val siteRepository: SiteRepository
) : CoroutineScope {
  private val requestedBoards = Collections.synchronizedSet(HashSet<String>())
  private val savedBoards = Collections.synchronizedSet(HashSet<String>())
  private val boardPagesMap: ConcurrentMap<String, Chan4PagesRequest.BoardPages> = ConcurrentHashMap()
  private val boardTimeMap: ConcurrentMap<String, Long> = ConcurrentHashMap()
  private val callbackList: MutableList<PageCallback> = ArrayList()
  
  override val coroutineContext: CoroutineContext
    get() = Dispatchers.Default + CoroutineName("PageRequestManager")
  
  fun getPage(op: Post?): BoardPage? {
    return if (op == null) {
      null
    } else {
      findPage(op.board.boardDescriptor(), op.no)
    }
  }

  fun getPage(threadDescriptor: ChanDescriptor.ThreadDescriptor?): BoardPage? {
    return if (threadDescriptor == null) {
      null
    } else {
      if (threadDescriptor.threadNo < 0) {
        return null
      }
      
      findPage(threadDescriptor.boardDescriptor, threadDescriptor.threadNo)
    }
  }

  fun forceUpdateForBoard(boardDescriptor: BoardDescriptor) {
    Logger.d(TAG, "Requesting existing board pages, forced")
    
    launch { requestBoard(boardDescriptor) }
  }

  private fun findPage(boardDescriptor: BoardDescriptor, opNo: Long): BoardPage? {
    val pages = getPages(boardDescriptor)
      ?: return null
    
    for (page in pages.boardPages) {
      for (threadNoTimeModPair in page.threads) {
        if (opNo == threadNoTimeModPair.no) {
          return page
        }
      }
    }
    
    return null
  }

  private fun getPages(boardDescriptor: BoardDescriptor): Chan4PagesRequest.BoardPages? {
    if (savedBoards.contains(boardDescriptor.boardCode)) {
      // If we have it stored already, return the pages for it
      // also issue a new request if 3 minutes have passed
      shouldUpdate(boardDescriptor)
      return boardPagesMap[boardDescriptor.boardCode]
    }

    val alreadyRequested = synchronized(this) {
      requestedBoards.contains(boardDescriptor.boardCode)
    }

    if (alreadyRequested) {
      return null
    }
  
    launch {
      // Otherwise, get the site for the board and request the pages for it
      Logger.d(TAG, "Requesting new board pages")
      requestBoard(boardDescriptor)
    }

    return null
  }

  private fun shouldUpdate(boardDescriptor: BoardDescriptor) {
    launch {
      val site = siteRepository.bySiteDescriptor(boardDescriptor.siteDescriptor)
      if (site == null) {
        Logger.e(TAG, "Couldn't find site by siteDescriptor (${boardDescriptor.siteDescriptor})")
        return@launch
      }

      val board = databaseManager.runTask(databaseBoardManager.getBoard(site, boardDescriptor.boardCode))
      if (board == null) {
        Logger.e(TAG, "Couldn't find board by siteDescriptor (${boardDescriptor.siteDescriptor}) " +
          "and boardCode (${boardDescriptor.boardCode})")
        return@launch
      }

      // Had some null issues for some reason? arisuchan in particular?
      val lastUpdate = boardTimeMap[board.code]
      val lastUpdateTime = lastUpdate ?: 0L

      if (lastUpdateTime + TimeUnit.MINUTES.toMillis(3) <= System.currentTimeMillis()) {
        Logger.d(TAG, "Requesting existing board pages for /" + board.code + "/, timeout")
        requestBoard(boardDescriptor)
      }
    }
  }

  private suspend fun requestBoard(boardDescriptor: BoardDescriptor) {
    val contains = synchronized(this) {
      if (!requestedBoards.contains(boardDescriptor.boardCode)) {
        requestedBoards.add(boardDescriptor.boardCode)
        false
      } else {
        true
      }
    }

    if (contains) {
      return
    }

    val site = siteRepository.bySiteDescriptor(boardDescriptor.siteDescriptor)
    if (site == null) {
      Logger.e(TAG, "Couldn't find site by siteDescriptor (${boardDescriptor.siteDescriptor})")
      return
    }

    val board = databaseManager.runTask(databaseBoardManager.getBoard(site, boardDescriptor.boardCode))
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
    }
  }

  fun addListener(callback: PageCallback?) {
    if (callback != null) {
      callbackList.add(callback)
    }
  }

  fun removeListener(callback: PageCallback?) {
    if (callback != null) {
      callbackList.remove(callback)
    }
  }

  private suspend fun onPagesReceived(
    boardDescriptor: BoardDescriptor,
    pages: Chan4PagesRequest.BoardPages
  ) {
    Logger.d(TAG, "Got pages for ${boardDescriptor.siteName()}/${boardDescriptor.boardCode}/")
    
    savedBoards.add(boardDescriptor.boardCode)
    requestedBoards.remove(boardDescriptor.boardCode)
    boardTimeMap[boardDescriptor.boardCode] = System.currentTimeMillis()
    boardPagesMap[boardDescriptor.boardCode] = pages

    coroutineScope {
      launch(Dispatchers.Main) {
        for (callback in callbackList) {
          callback.onPagesReceived()
        }
      }
    }
  }

  interface PageCallback {
    fun onPagesReceived()
  }

  companion object {
    private const val TAG = "PageRequestManager"
  }
}
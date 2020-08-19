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
package com.github.adamantcheese.chan.core.presenter

import com.github.adamantcheese.chan.core.manager.BoardManager
import com.github.adamantcheese.chan.core.manager.BookmarksManager
import com.github.adamantcheese.chan.core.manager.HistoryNavigationManager
import com.github.adamantcheese.chan.core.model.ChanThread
import com.github.adamantcheese.chan.core.site.Site
import com.github.adamantcheese.chan.ui.helper.PostHelper
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.model.data.descriptor.BoardDescriptor
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor.CatalogDescriptor
import io.reactivex.disposables.CompositeDisposable
import java.util.*
import javax.inject.Inject

class BrowsePresenter @Inject constructor(
  boardManager: BoardManager,
  private val historyNavigationManager: HistoryNavigationManager,
  private val bookmarksManager: BookmarksManager
) : Observer {
  private var callback: Callback? = null
  private var hadBoards: Boolean
  private var currentBoardDescriptor: BoardDescriptor? = null
//  private val savedBoardsObservable = boardManager.savedBoardsObservable
  private val compositeDisposable = CompositeDisposable()

  init {
    hadBoards = hasBoards()
  }

  fun create(callback: Callback?) {
    this.callback = callback
//    savedBoardsObservable.addObserver(this)
  }

  fun destroy() {
    callback = null
    compositeDisposable.clear()
//    savedBoardsObservable.deleteObserver(this)
  }

  fun currentBoardDescriptor(): BoardDescriptor? {
    return currentBoardDescriptor
  }

  fun setBoard(boardDescriptor: BoardDescriptor) {
    loadBoard(boardDescriptor)
  }

  fun loadWithDefaultBoard(boardSetViaBoardSetup: Boolean) {
    val boardDescriptor = firstBoardDescriptor()
    if (boardDescriptor != null) {
      loadBoard(boardDescriptor, !boardSetViaBoardSetup)
    }
  }

  fun onBoardsFloatingMenuSiteClicked(site: Site) {
    callback?.loadSiteSetup(site)
  }

  override fun update(o: Observable, arg: Any?) {
//    if (o === savedBoardsObservable) {
//      if (!hadBoards && hasBoards()) {
//        hadBoards = true
//        loadWithDefaultBoard(true)
//      }
//    }
  }

  private fun hasBoards(): Boolean {
    return firstBoardDescriptor() != null
  }

  private fun firstBoardDescriptor(): BoardDescriptor? {
//    for (item in savedBoardsObservable.get()) {
//      if (item.boards.isNotEmpty()) {
//        return item.boards[0]?.boardDescriptor()
//      }
//    }

    return null
  }

  private fun loadBoard(boardDescriptor: BoardDescriptor, isDefaultBoard: Boolean = false) {
    if (callback == null) {
      return
    }

    if (!isDefaultBoard) {
      // Do not bring a board to the top of the navigation list if we are loading the default
      // board that we load on every app start. Because we want to have the last visited
      // thread/board on top not the default board.
      historyNavigationManager.moveNavElementToTop(
        CatalogDescriptor(boardDescriptor)
      )
    }

    if (boardDescriptor == currentBoardDescriptor) {
      return
    }

    currentBoardDescriptor = boardDescriptor
    callback!!.loadBoard(boardDescriptor)
  }

  fun bookmarkEveryThread(chanThread: ChanThread?) {
    if (chanThread == null) {
      Logger.e(TAG, "bookmarkEveryThread() chanThread == null")
      return
    }

    val chanDescriptor = chanThread.chanDescriptor
    if (chanDescriptor == null) {
      Logger.e(TAG, "bookmarkEveryThread() chanDescriptor == null")
      return
    }

    if (chanDescriptor is ChanDescriptor.ThreadDescriptor) {
      Logger.e(TAG, "bookmarkEveryThread() chanDescriptor is not catalog descriptor")
      return
    }

    for (post in chanThread.posts) {
      if (!post.isOP) {
        Logger.e(TAG, "bookmarkEveryThread() post is not OP")
        continue
      }

      val threadDescriptor = chanDescriptor.toThreadDescriptor(post.no)
      val title = PostHelper.getTitle(post, threadDescriptor)

      if (bookmarksManager.exists(threadDescriptor)) {
        Logger.d(TAG, "bookmarkEveryThread() bookmark for post ${title.take(50)} already exist")
        continue
      }

      val thumbnailUrl = post.firstImage()?.thumbnailUrl
      bookmarksManager.createBookmark(threadDescriptor, title, thumbnailUrl)

      Logger.d(TAG, "bookmarkEveryThread() created bookmark for post ${title.take(50)}")
    }

    Logger.d(TAG, "bookmarkEveryThread() done")
  }

  interface Callback {
    fun loadBoard(boardDescriptor: BoardDescriptor)
    fun loadSiteSetup(site: Site)
  }

  companion object {
    private const val TAG = "BrowsePresenter"
  }
}
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

import com.github.adamantcheese.chan.core.database.DatabaseManager
import com.github.adamantcheese.chan.core.manager.BoardManager
import com.github.adamantcheese.chan.core.manager.BookmarksManager
import com.github.adamantcheese.chan.core.manager.HistoryNavigationManager
import com.github.adamantcheese.chan.core.model.ChanThread
import com.github.adamantcheese.chan.core.model.orm.Board
import com.github.adamantcheese.chan.core.model.orm.Loadable
import com.github.adamantcheese.chan.core.site.Site
import com.github.adamantcheese.chan.ui.helper.PostHelper
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor.CatalogDescriptor
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor.ThreadDescriptor.Companion.create
import io.reactivex.disposables.CompositeDisposable
import java.util.*
import javax.inject.Inject

class BrowsePresenter @Inject constructor(
  private val databaseManager: DatabaseManager,
  boardManager: BoardManager,
  private val historyNavigationManager: HistoryNavigationManager,
  private val bookmarksManager: BookmarksManager
) : Observer {
  private var callback: Callback? = null
  private var hadBoards: Boolean
  private var currentBoard: Board? = null
  private val savedBoardsObservable = boardManager.savedBoardsObservable
  private val compositeDisposable = CompositeDisposable()

  init {
    hadBoards = hasBoards()
  }

  fun create(callback: Callback?) {
    this.callback = callback
    savedBoardsObservable.addObserver(this)
  }

  fun destroy() {
    callback = null
    compositeDisposable.clear()
    savedBoardsObservable.deleteObserver(this)
  }

  fun currentBoard(): Board? {
    return currentBoard
  }

  fun setBoard(board: Board) {
    loadBoard(board)
  }

  fun loadWithDefaultBoard(boardSetViaBoardSetup: Boolean) {
    val first = firstBoard()
    if (first != null) {
      loadBoard(first, !boardSetViaBoardSetup)
    }
  }

  fun onBoardsFloatingMenuSiteClicked(site: Site) {
    callback?.loadSiteSetup(site)
  }

  override fun update(o: Observable, arg: Any?) {
    if (o === savedBoardsObservable) {
      if (!hadBoards && hasBoards()) {
        hadBoards = true
        loadWithDefaultBoard(true)
      }
    }
  }

  private fun hasBoards(): Boolean {
    return firstBoard() != null
  }

  private fun firstBoard(): Board? {
    for (item in savedBoardsObservable.get()) {
      if (item.boards.isNotEmpty()) {
        return item.boards[0]
      }
    }

    return null
  }

  private fun getLoadableForBoard(board: Board): Loadable {
    return databaseManager.databaseLoadableManager.getOrCreateLoadable(Loadable.forCatalog(board))
  }

  private fun loadBoard(board: Board, isDefaultBoard: Boolean = false) {
    if (callback == null) {
      return
    }

    if (!isDefaultBoard) {
      // Do not bring a board to the top of the navigation list if we are loading the default
      // board that we load on every app start. Because we want to have the last visited
      // thread/board on top not the default board.
      historyNavigationManager.moveNavElementToTop(
        CatalogDescriptor(board.boardDescriptor())
      )
    }

    if (board == currentBoard) {
      return
    }

    currentBoard = board
    callback!!.loadBoard(getLoadableForBoard(board))
  }

  fun bookmarkEveryThread(chanThread: ChanThread?) {
    if (chanThread == null) {
      Logger.e(TAG, "bookmarkEveryThread() chanThread == null")
      return
    }

    val catalogLoadable = chanThread.loadable
    if (catalogLoadable == null) {
      Logger.e(TAG, "bookmarkEveryThread() catalogLoadable == null")
      return
    }

    if (!catalogLoadable.isCatalogMode) {
      Logger.e(TAG, "bookmarkEveryThread() not catalog loaded")
      return
    }

    for (post in chanThread.posts) {
      if (!post.isOP) {
        Logger.e(TAG, "bookmarkEveryThread() post is not OP")
        continue
      }

      val threadDescriptor = create(
        catalogLoadable.site.name(),
        catalogLoadable.boardCode,
        post.no
      )

      val title = PostHelper.getTitle(post, catalogLoadable)

      if (bookmarksManager.exists(threadDescriptor)) {
        Logger.d(TAG, "bookmarkEveryThread() bookmark for post ${title.take(50)} already exist")
        continue
      }

      val thumbnailUrl = post.firstImage()?.thumbnailUrl
      val loadable = Loadable.forThread(catalogLoadable.site, post.board, post.no, title)

      // We still need this so that we can open the thread by this bookmark later
      databaseManager.databaseLoadableManager.getOrCreateLoadable(loadable)
      bookmarksManager.createBookmark(threadDescriptor, title, thumbnailUrl)

      Logger.d(TAG, "bookmarkEveryThread() created bookmark for post ${title.take(50)}")
    }

    Logger.d(TAG, "bookmarkEveryThread() done")
  }

  interface Callback {
    fun loadBoard(loadable: Loadable)
    fun loadSiteSetup(site: Site)
  }

  companion object {
    private const val TAG = "BrowsePresenter"
  }
}
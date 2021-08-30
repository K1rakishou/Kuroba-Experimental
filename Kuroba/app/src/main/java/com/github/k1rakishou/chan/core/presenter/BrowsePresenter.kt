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

import android.content.Context
import android.graphics.Bitmap
import android.text.SpannableStringBuilder
import android.text.style.ImageSpan
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.model.data.options.ChanCacheOptions
import com.github.k1rakishou.model.data.options.ChanCacheUpdateOptions
import com.github.k1rakishou.model.data.options.ChanLoadOptions
import com.github.k1rakishou.model.data.options.ChanReadOptions
import com.github.k1rakishou.model.repository.ChanPostRepository
import com.github.k1rakishou.model.util.ChanPostUtils
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import javax.inject.Inject

class BrowsePresenter @Inject constructor(
  private val appScope: CoroutineScope,
  private val _bookmarksManager: Lazy<BookmarksManager>,
  private val _siteManager: Lazy<SiteManager>,
  private val _boardManager: Lazy<BoardManager>,
  private val _chanThreadManager: Lazy<ChanThreadManager>,
  private val _chanPostRepository: Lazy<ChanPostRepository>,
  private val _imageLoaderV2: Lazy<ImageLoaderV2>
) {
  private var callback: Callback? = null
  private var currentOpenedCatalog: ChanDescriptor.ICatalogDescriptor? = null

  private val bookmarksManager: BookmarksManager
    get() = _bookmarksManager.get()
  private val siteManager: SiteManager
    get() = _siteManager.get()
  private val boardManager: BoardManager
    get() = _boardManager.get()
  private val chanThreadManager: ChanThreadManager
    get() = _chanThreadManager.get()
  private val chanPostRepository: ChanPostRepository
    get() = _chanPostRepository.get()
  private val imageLoaderV2: ImageLoaderV2
    get() = _imageLoaderV2.get()

  fun create(controllerScope: CoroutineScope, callback: Callback?) {
    this.callback = callback

    controllerScope.launch {
      boardManager.listenForCurrentSelectedBoard()
        .asFlow()
        .collect { currentBoard ->
          val catalogDescriptor = currentBoard.catalogDescriptor

          if (currentOpenedCatalog == catalogDescriptor) {
            return@collect
          }

          if (catalogDescriptor == null) {
            callback?.showSitesNotSetup()
          } else {
            callback?.loadCatalog(catalogDescriptor)
          }
        }
    }
  }

  fun destroy() {
    callback = null
    currentOpenedCatalog = null
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
      loadCatalog(ChanDescriptor.CatalogDescriptor.create(firstActiveBoardDescriptor!!))
    } else {
      callback?.showSitesNotSetup()
    }
  }

  suspend fun loadCatalog(catalogDescriptor: ChanDescriptor.ICatalogDescriptor) {
    if (callback == null) {
      return
    }

    currentOpenedCatalog = catalogDescriptor
    callback?.loadCatalog(catalogDescriptor)
  }

  suspend fun bookmarkEveryThread(chanDescriptor: ChanDescriptor?) {
    if (chanDescriptor == null) {
      Logger.e(TAG, "bookmarkEveryThread() chanDescriptor == null")
      return
    }

    if (chanDescriptor is ChanDescriptor.ThreadDescriptor) {
      Logger.e(TAG, "bookmarkEveryThread() chanDescriptor is not catalog descriptor")
      return
    }

    chanDescriptor as ChanDescriptor.ICatalogDescriptor
    val simpleThreadBookmarkList = mutableListOf<BookmarksManager.SimpleThreadBookmark>()

    val catalog = chanThreadManager.getChanCatalog(chanDescriptor)
    if (catalog == null) {
      Logger.e(TAG, "bookmarkEveryThread() Couldn't find catalog by descriptor: $chanDescriptor")
      return
    }

    val threadsToCreateInDatabase = mutableListOf<ChanDescriptor.ThreadDescriptor>()

    catalog.iteratePostsOrdered { chanOriginalPost ->
      val threadDescriptor = chanDescriptor.toThreadDescriptor(chanOriginalPost.postNo())
      val title = ChanPostUtils.getTitle(chanOriginalPost, threadDescriptor)

      threadsToCreateInDatabase += threadDescriptor

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

    if (threadsToCreateInDatabase.isNotEmpty()) {
      chanPostRepository.createManyEmptyThreadsIfNotExist(threadsToCreateInDatabase)
        .safeUnwrap { error ->
          Logger.e(TAG, "bookmarkEveryThread() error", error)
          return
        }
    }

    bookmarksManager.createBookmarks(simpleThreadBookmarkList)
    Logger.d(TAG, "bookmarkEveryThread() done")
  }

  fun cacheEveryThreadClicked(chanDescriptor: ChanDescriptor?) {
    if (chanDescriptor == null) {
      Logger.e(TAG, "cacheEveryThreadClicked() chanDescriptor == null")
      return
    }

    if (chanDescriptor is ChanDescriptor.ThreadDescriptor) {
      Logger.e(TAG, "cacheEveryThreadClicked() chanDescriptor is not catalog descriptor")
      return
    }

    chanDescriptor as ChanDescriptor.ICatalogDescriptor

    val catalog = chanThreadManager.getChanCatalog(chanDescriptor)
    if (catalog == null) {
      Logger.e(TAG, "cacheEveryThreadClicked() Couldn't find catalog by descriptor: $chanDescriptor")
      return
    }

    val activeThreads = mutableSetOf<ChanDescriptor.ThreadDescriptor>()

    catalog.iteratePostsOrdered { chanOriginalPost ->
      appScope.launch {
        val threadDescriptor = ChanDescriptor.ThreadDescriptor.create(
          chanDescriptor,
          chanOriginalPost.postNo()
        )

        activeThreads += threadDescriptor

        chanThreadManager.loadThreadOrCatalog(
          page = null,
          compositeCatalogDescriptor = null,
          chanDescriptor = threadDescriptor,
          chanCacheUpdateOptions = ChanCacheUpdateOptions.UpdateCache,
          chanLoadOptions = ChanLoadOptions.retainAll(),
          chanCacheOptions = ChanCacheOptions.onlyCacheInMemory(),
          chanReadOptions = ChanReadOptions.default()
        )

        activeThreads.remove(threadDescriptor)

        Logger.d(TAG, "cacheEveryThreadClicked() $threadDescriptor cached, " +
          "threads left: ${activeThreads.size}")
      }
    }

    Logger.d(TAG, "cacheEveryThreadClicked() done")
  }

  suspend fun getCompositeCatalogNavigationSubtitle(
    context: Context,
    fontSizePx: Int,
    compositeCatalogDescriptor: ChanDescriptor.CompositeCatalogDescriptor,
    visibleDescriptorsCount: Int = 3
  ): CharSequence {
    val duplicates = hashSetOf<SiteDescriptor>()
    val spannableStringBuilder = SpannableStringBuilder()
    val catalogsBySites = compositeCatalogDescriptor.catalogDescriptors
      .groupBy { catalogDescriptor -> catalogDescriptor.siteDescriptor() }

    compositeCatalogDescriptor.catalogDescriptors
      .take(visibleDescriptorsCount)
      .forEach { catalogDescriptor ->
        val siteDescriptor = catalogDescriptor.siteDescriptor()

        if (!duplicates.add(siteDescriptor)) {
          return@forEach
        }

        val iconBitmap = siteManager.bySiteDescriptor(siteDescriptor)
          ?.icon()
          ?.getIconSuspend(context)
          ?.bitmap

        if (iconBitmap == null) {
          return@forEach
        }

        if (spannableStringBuilder.isNotEmpty()) {
          spannableStringBuilder.append("+")
        }

        val catalogs = catalogsBySites[siteDescriptor]
          ?: return@forEach

        val boardCodes = catalogs.joinToString(
          separator = ",",
          prefix = "/",
          postfix = "/",
          transform = { descriptor -> descriptor.boardDescriptor.boardCode }
        )

        spannableStringBuilder
          .append("  ", getIconSpan(iconBitmap, fontSizePx), 0)
          .append(boardCodes)
    }

    if (compositeCatalogDescriptor.catalogDescriptors.size > visibleDescriptorsCount) {
      val omittedCount = compositeCatalogDescriptor.catalogDescriptors.size - visibleDescriptorsCount

      spannableStringBuilder
        .append(" + ")
        .append(omittedCount.toString())
        .append(" more")
    }

    return spannableStringBuilder
  }

  private fun getIconSpan(icon: Bitmap, fontSizePx: Int): ImageSpan {
    val iconSpan = ImageSpan(AndroidUtils.getAppContext(), icon)
    val width = (fontSizePx.toFloat() / (icon.height.toFloat() / icon.width.toFloat())).toInt()

    iconSpan.drawable.setBounds(0, 0, width, fontSizePx)
    return iconSpan
  }

  interface Callback {
    suspend fun loadCatalog(catalogDescriptor: ChanDescriptor.ICatalogDescriptor)
    suspend fun showSitesNotSetup()
  }

  companion object {
    private const val TAG = "BrowsePresenter"
  }
}
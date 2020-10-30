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

import android.util.LruCache
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.site.loader.ChanThreadLoader
import com.github.k1rakishou.chan.core.site.loader.ChanThreadLoader.ChanLoaderCallback
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.CatalogDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.ThreadDescriptor
import com.github.k1rakishou.model.repository.ChanPostRepository
import com.google.gson.Gson

class ChanLoaderManager(
  private val gson: Gson,
  private val proxiedOkHttpClient: ProxiedOkHttpClient,
  private val appConstants: AppConstants,
  private val filterEngine: FilterEngine,
  private val chanPostRepository: ChanPostRepository,
  private val archivesManager: ArchivesManager,
  private val themeEngine: ThemeEngine,
  private val postFilterManager: PostFilterManager,
  private val bookmarksManager: BookmarksManager,
  private val siteManager: SiteManager,
  private val boardManager: BoardManager,
  private val savedReplyManager: SavedReplyManager,
) {
  private val threadLoadersCache = LruCache<ChanDescriptor, ChanThreadLoader>(THREAD_LOADERS_CACHE_SIZE)

  @get:Synchronized
  var currentCatalogDescriptor: CatalogDescriptor? = null
    private set

  @get:Synchronized
  var currentThreadDescriptor: ThreadDescriptor? = null
    private set

  @Synchronized
  fun obtain(
    chanDescriptor: ChanDescriptor,
    listener: ChanLoaderCallback
  ): ChanThreadLoader {
    BackgroundUtils.ensureMainThread()

    val chanLoader = if (chanDescriptor.isThreadDescriptor()) {
      var loader = threadLoadersCache[chanDescriptor]
      if (loader == null) {
        loader = createChanThreadLoader(chanDescriptor)
        threadLoadersCache.put(chanDescriptor, loader)
      }

      currentThreadDescriptor = chanDescriptor as ThreadDescriptor
      loader
    } else {
      val loader = createChanThreadLoader(chanDescriptor)
      currentCatalogDescriptor = chanDescriptor as CatalogDescriptor
      loader
    }

    chanLoader.addListener(listener)
    return chanLoader
  }

  @Synchronized
  fun release(
    chanLoader: ChanThreadLoader,
    listener: ChanLoaderCallback
  ) {
    BackgroundUtils.ensureMainThread()

    val chanDescriptor = chanLoader.chanDescriptor
    if (!chanDescriptor.isThreadDescriptor()) {
      chanLoader.removeListener(listener)
      return
    }

    threadLoadersCache[chanDescriptor]
      ?: throw IllegalStateException("The released loader does not exist (chanDescriptor=$chanDescriptor)")

    if (chanLoader.removeListener(listener)) {
      threadLoadersCache.put(chanDescriptor, chanLoader)
    }
  }

  @Synchronized
  fun getLoader(chanDescriptor: ChanDescriptor): ChanThreadLoader? {
    return threadLoadersCache[chanDescriptor]
  }

  private fun createChanThreadLoader(chanDescriptor: ChanDescriptor): ChanThreadLoader {
    return ChanThreadLoader(
      chanDescriptor = chanDescriptor,
      gson = gson,
      proxiedOkHttpClient = proxiedOkHttpClient,
      appConstants = appConstants,
      filterEngine = filterEngine,
      chanPostRepository = chanPostRepository,
      archivesManager = archivesManager,
      themeEngine = themeEngine,
      postFilterManager = postFilterManager,
      bookmarksManager = bookmarksManager,
      siteManager = siteManager,
      boardManager = boardManager,
      savedReplyManager = savedReplyManager
    )
  }

  companion object {
    const val THREAD_LOADERS_CACHE_SIZE = 25
  }

}
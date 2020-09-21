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
import com.github.k1rakishou.chan.core.site.loader.ChanThreadLoader
import com.github.k1rakishou.chan.core.site.loader.ChanThreadLoader.ChanLoaderCallback
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.CatalogDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.ThreadDescriptor

class ChanLoaderManager {
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
        loader = ChanThreadLoader(chanDescriptor)
        threadLoadersCache.put(chanDescriptor, loader)
      }

      currentThreadDescriptor = chanDescriptor as ThreadDescriptor
      loader
    } else {
      val loader = ChanThreadLoader(chanDescriptor)

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

    val foundChanLoader = threadLoadersCache[chanDescriptor]
    if (foundChanLoader == null) {
      throw IllegalStateException("The released loader does not exist (chanDescriptor=$chanDescriptor)")
    }

    if (chanLoader.removeListener(listener)) {
      threadLoadersCache.put(chanDescriptor, chanLoader)
    }
  }

  @Synchronized
  fun getLoader(chanDescriptor: ChanDescriptor): ChanThreadLoader? {
    return threadLoadersCache[chanDescriptor]
  }

  companion object {
    const val THREAD_LOADERS_CACHE_SIZE = 25
  }

}
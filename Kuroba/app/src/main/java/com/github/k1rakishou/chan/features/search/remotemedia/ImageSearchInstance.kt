package com.github.k1rakishou.chan.features.search.remotemedia

import androidx.annotation.DrawableRes
import com.github.k1rakishou.chan.features.search.remotemedia.instances.SearxInstance
import com.github.k1rakishou.chan.features.search.remotemedia.instances.YandexInstance
import com.github.k1rakishou.v2.KurobaSettings
import com.github.k1rakishou.v2.parameters.RemoteImageSearchSettings
import okhttp3.HttpUrl

abstract class ImageSearchInstance(
  protected val kurobaSettings: KurobaSettings,
  val type: RemoteImageSearchSettings.InstanceType,
  @DrawableRes val icon: Int
) {

  private var _rememberedFirstVisibleItemIndex: Int = 0
  val rememberedFirstVisibleItemIndex: Int
    get() = _rememberedFirstVisibleItemIndex

  private var _rememberedFirstVisibleItemScrollOffset: Int = 0
  val rememberedFirstVisibleItemScrollOffset: Int
    get() = _rememberedFirstVisibleItemScrollOffset

  private var _currentPage = 0
  val currentPage: Int
    get() = _currentPage

  private var _searchQuery: String? = null
  val searchQuery: String?
    get() = _searchQuery

  abstract val cookies: String?

  abstract fun baseUrl(): HttpUrl
  abstract suspend fun buildSearchUrl(baseUrl: HttpUrl, query: String, page: Int?): HttpUrl
  abstract suspend fun updateCookies(newCookies: String)

  fun updateLazyListState(firstVisibleItemIndex: Int, firstVisibleItemScrollOffset: Int) {
    _rememberedFirstVisibleItemIndex = firstVisibleItemIndex
    _rememberedFirstVisibleItemScrollOffset = firstVisibleItemScrollOffset
  }

  fun updateCurrentPage(page: Int) {
    _currentPage = page
  }

  fun updateSearchQuery(newQuery: String) {
    _searchQuery = newQuery
  }

  companion object {
    fun createAll(kurobaSettings: KurobaSettings): List<ImageSearchInstance> {
      return listOf(
        SearxInstance(kurobaSettings),
        YandexInstance(kurobaSettings)
      )
    }
  }
}
package com.github.k1rakishou.chan.features.reply_image_search

import androidx.annotation.DrawableRes
import com.github.k1rakishou.chan.features.reply_image_search.instances.SearxInstance
import com.github.k1rakishou.chan.features.reply_image_search.instances.YandexInstance
import com.github.k1rakishou.persist_state.ImageSearchInstanceType
import okhttp3.HttpUrl

abstract class ImageSearchInstance(
  val type: ImageSearchInstanceType,
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
  abstract fun updateCookies(newCookies: String)

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
    fun createAll(): List<ImageSearchInstance> {
      return listOf(SearxInstance(), YandexInstance())
    }
  }
}
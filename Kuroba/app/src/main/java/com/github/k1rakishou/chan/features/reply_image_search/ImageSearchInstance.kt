package com.github.k1rakishou.chan.features.reply_image_search

import androidx.annotation.DrawableRes
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.persist_state.PersistableChanState
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

abstract class ImageSearchInstance(
  val type: ImageSearchInstanceType,
  val baseUrl: HttpUrl,
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

  abstract fun buildSearchUrl(query: String, page: Int?): HttpUrl
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

class SearxInstance : ImageSearchInstance(
  type = ImageSearchInstanceType.Searx,
  baseUrl = "https://searx.prvcy.eu".toHttpUrl(),
  icon = R.drawable.searx_favicon
) {

  override val cookies: String? = null

  override fun updateCookies(newCookies: String) {
    // no-op
  }

  override fun buildSearchUrl(query: String, page: Int?): HttpUrl {
    return with(baseUrl.newBuilder()) {
      addPathSegment("search")
      addQueryParameter("q", query)
      addQueryParameter("categories", "images")
      addQueryParameter("language", "en-US")
      addQueryParameter("format", "json")

      if (page != null && page > 0) {
        addQueryParameter("pageno", "${page}")
      }

      build()
    }
  }

}

class YandexInstance : ImageSearchInstance(
  type = ImageSearchInstanceType.Yandex,
  baseUrl = "https://yandex.com".toHttpUrl(),
  icon = R.drawable.yandex_favicon
) {

  private var _cookies: String? = null
  override val cookies: String?
    get() = _cookies

  init {
    _cookies = PersistableChanState.yandexImageSearchCookies.get()
      .takeIf { cookiesString -> cookiesString.isNotEmpty() }
  }

  override fun updateCookies(newCookies: String) {
    _cookies = newCookies
    PersistableChanState.yandexImageSearchCookies.set(newCookies)
  }

  override fun buildSearchUrl(query: String, page: Int?): HttpUrl {
    return with(baseUrl.newBuilder()) {
      addPathSegment("images")
      addPathSegment("search")
      addQueryParameter("text", query)

      if (page != null) {
        addQueryParameter("p", "${page}")
      }

      build()
    }

  }

}

enum class ImageSearchInstanceType {
  Searx,
  Yandex
}
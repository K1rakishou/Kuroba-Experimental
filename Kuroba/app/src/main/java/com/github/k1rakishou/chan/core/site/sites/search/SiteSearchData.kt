package com.github.k1rakishou.chan.core.site.sites.search

import android.text.SpannableStringBuilder
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import okhttp3.HttpUrl
import org.joda.time.DateTime

interface SearchParams {
  val siteDescriptor: SiteDescriptor
}

data class Chan4SearchParams(
  override val siteDescriptor: SiteDescriptor,
  val query: String,
  val page: Int?
) : SearchParams {

  fun getCurrentPage(): Int = page ?: 0

}

data class FoolFuukaSearchParams(
  val boardDescriptor: BoardDescriptor,
  val query: String,
  val page: Int?
) : SearchParams {

  fun getCurrentPage(): Int = page ?: 1

  override val siteDescriptor: SiteDescriptor
    get() = boardDescriptor.siteDescriptor
}

sealed class SearchResult {
  data class Success(
    val query: String,
    val searchEntries: List<SearchEntry>,
    val nextPageCursor: PageCursor,
    val totalFoundEntries: Int?
  ) : SearchResult()

  data class Failure(
    val searchError: SearchError
  ) : SearchResult()
}

sealed class PageCursor {
  object Empty : PageCursor()
  class Page(val value: Int) : PageCursor()
  object End : PageCursor()
}

sealed class SearchError  {
  object NotImplemented : SearchError()
  data class SiteNotFound(val siteDescriptor: SiteDescriptor) : SearchError()
  data class ServerError(val statusCode: Int) : SearchError()
  data class UnknownError(val error: Throwable) : SearchError()
  data class HtmlParsingError(val message: String) : SearchError()
}

data class SearchEntryPost(
  val isOp: Boolean,
  val name: SpannableStringBuilder?,
  val subject: SpannableStringBuilder?,
  val postDescriptor: PostDescriptor,
  val dateTime: DateTime,
  val postImageUrlRawList: List<HttpUrl>,
  val commentRaw: SpannableStringBuilder?
)

data class SearchEntry(
  val posts: List<SearchEntryPost>
)
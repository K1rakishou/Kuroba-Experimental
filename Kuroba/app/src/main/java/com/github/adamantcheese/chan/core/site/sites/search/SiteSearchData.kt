package com.github.adamantcheese.chan.core.site.sites.search

import android.text.SpannableStringBuilder
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.github.adamantcheese.model.data.descriptor.SiteDescriptor
import okhttp3.HttpUrl
import org.joda.time.DateTime

data class SearchParams(val siteDescriptor: SiteDescriptor, val query: String, val page: Int?)

sealed class SearchResult {
  data class Success(
    val query: String,
    val searchEntries: List<SearchEntry>,
    val nextPageCursor: PageCursor,
    val totalFoundEntries: Int?
  ) : SearchResult()
  data class Failure(val searchError: SearchError) : SearchResult()
}

sealed class PageCursor {
  object Empty : PageCursor()
  class Page(val value: Int) : PageCursor()
  object End : PageCursor()
}

sealed class SearchError  {
  object NotImplemented : SearchError()
  class SiteNotFound(val siteDescriptor: SiteDescriptor) : SearchError()
  class ServerError(val statusCode: Int) : SearchError()
  class UnknownError(val error: Throwable) : SearchError()
  class HtmlParsingError(val message: String) : SearchError()
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

data class SearchEntryThread(
  val threadDescriptor: ChanDescriptor.ThreadDescriptor,
  val posts: List<SearchEntryPost>
)

data class SearchEntry(
  val thread: SearchEntryThread
)

class SearchEntryPostBuilder {
  var isOp: Boolean? = null
  var name: String? = null
  var subject: String? = null
  var postDescriptor: PostDescriptor? = null
  var dateTime: DateTime? = null
  val postImageUrlRawList = mutableListOf<HttpUrl>()
  var commentRaw: String? = null

  fun threadDescriptor(): ChanDescriptor.ThreadDescriptor {
    checkNotNull(isOp) { "isOp is null!" }
    checkNotNull(postDescriptor) { "postDescriptor is null!" }
    check(isOp!!) { "Must be OP!" }

    return postDescriptor!!.threadDescriptor()
  }

  fun hasMissingInfo(): Boolean {
    return isOp == null || postDescriptor == null || dateTime == null
  }

  fun hasPostDescriptor(): Boolean = postDescriptor != null
  fun hasDateTime(): Boolean = dateTime != null
  fun hasNameAndSubject(): Boolean = name != null && !subject.isNullOrBlank()

  fun toSearchEntryPost(): SearchEntryPost {
    if (hasMissingInfo()) {
      throw IllegalStateException("Some info is missing! isOp=$isOp, postDescriptor=$postDescriptor, " +
        "dateTime=$dateTime, commentRaw=$commentRaw")
    }

    return SearchEntryPost(
      isOp!!,
      name?.let { SpannableStringBuilder(it) },
      subject?.let { SpannableStringBuilder(it) },
      postDescriptor!!,
      dateTime!!,
      postImageUrlRawList,
      commentRaw?.let { SpannableStringBuilder(it) }
    )
  }

  override fun toString(): String {
    return "SearchEntryPostBuilder(isOp=$isOp, postDescriptor=$postDescriptor, dateTime=${dateTime?.millis}, " +
      "postImageUrlRawList=$postImageUrlRawList, commentRaw=$commentRaw)"
  }

}
package com.github.k1rakishou.chan.core.site.sites.dvach

import android.text.SpannableStringBuilder
import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.SiteEndpoints
import com.github.k1rakishou.chan.core.site.sites.search.DvachSearchParams
import com.github.k1rakishou.chan.core.site.sites.search.PageCursor
import com.github.k1rakishou.chan.core.site.sites.search.SearchEntry
import com.github.k1rakishou.chan.core.site.sites.search.SearchEntryPost
import com.github.k1rakishou.chan.core.site.sites.search.SearchError
import com.github.k1rakishou.chan.core.site.sites.search.SearchResult
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.suspendConvertIntoJsonObjectWithAdapter
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dagger.Lazy
import okhttp3.Request
import org.joda.time.DateTime
import org.jsoup.parser.Parser

class DvachSearchRequest(
  private val moshi: Lazy<Moshi>,
  private val request: Request,
  private val proxiedOkHttpClient: Lazy<RealProxiedOkHttpClient>,
  private val searchParams: DvachSearchParams,
  private val siteManager: SiteManager
) {

  suspend fun execute(): SearchResult {
    val dvachSearchResult = proxiedOkHttpClient.get()
      .okHttpClient()
      .suspendConvertIntoJsonObjectWithAdapter(
        request,
        moshi.get().adapter(DvachSearchResult::class.java)
      )

    val dvachSearch = if (dvachSearchResult is ModularResult.Error) {
      val error = dvachSearchResult.error
      return SearchResult.Failure(SearchError.UnknownError(error))
    } else {
      dvachSearchResult.valueOrNull()!!
    }

    return convertToSearchResult(dvachSearch)
  }

  private fun convertToSearchResult(dvachSearchResult: DvachSearchResult): SearchResult {
    if (dvachSearchResult.error != null) {
      return SearchResult.Failure(SearchError.SiteSpecificError(dvachSearchResult.error.message))
    }

    if (dvachSearchResult.posts.isNullOrEmpty()) {
      return SearchResult.Success(searchParams, emptyList(), PageCursor.End, null)
    }

    val endpoints = siteManager.bySiteDescriptor(Dvach.SITE_DESCRIPTOR)?.endpoints()
    val boardDescriptor = BoardDescriptor.create(Dvach.SITE_DESCRIPTOR, searchParams.boardCode)

    val searchPosts = dvachSearchResult.posts.map { dvachSearchPost ->
      val isOp = dvachSearchPost.parent == 0L

      val postDescriptor = if (isOp) {
        PostDescriptor.create(
          siteName = Dvach.SITE_DESCRIPTOR.siteName,
          boardCode = searchParams.boardCode,
          threadNo = dvachSearchPost.num,
          postNo = dvachSearchPost.num
        )
      } else {
        PostDescriptor.create(
          siteName = Dvach.SITE_DESCRIPTOR.siteName,
          boardCode = searchParams.boardCode,
          threadNo = dvachSearchPost.parent,
          postNo = dvachSearchPost.num
        )
      }

      val postImageUrlRawList = dvachSearchPost.files
        ?.mapNotNull { file ->
          if (file.path.isNullOrEmpty() || file.thumbnail.isNullOrEmpty()) {
            return@mapNotNull null
          }

          if (endpoints == null) {
            return@mapNotNull null
          }

          val args = SiteEndpoints.makeArgument("path", file.path, "thumbnail", file.thumbnail)
          return@mapNotNull endpoints.thumbnailUrl(boardDescriptor, false, 0, args)
        }
        ?: emptyList()

      return@map SearchEntryPost(
        isOp = isOp,
        name = SpannableStringBuilder(Parser.unescapeEntities(dvachSearchPost.name, false)),
        subject = SpannableStringBuilder(Parser.unescapeEntities(dvachSearchPost.subject, false)),
        postDescriptor = postDescriptor,
        dateTime = DateTime(dvachSearchPost.timestamp * 1000),
        postImageUrlRawList = postImageUrlRawList,
        commentRaw = SpannableStringBuilder(dvachSearchPost.comment)
      )
    }

    return SearchResult.Success(
      searchParams = searchParams,
      searchEntries = listOf(SearchEntry(searchPosts)),
      nextPageCursor = PageCursor.End,
      totalFoundEntries = searchPosts.size
    )
  }

  @JsonClass(generateAdapter = true)
  data class DvachSearchResult(
    val posts: List<DvachSearchPost>?,
    val error: DvachError?
  )

  @JsonClass(generateAdapter = true)
  data class DvachError(
    val code: Int,
    val message: String
  )

  @JsonClass(generateAdapter = true)
  data class DvachSearchPost(
    val num: Long,
    val parent: Long,
    val comment: String,
    val subject: String,
    val date: String,
    val name: String,
    val timestamp: Long,
    val files: List<DvachSearchPostFile>?
  )

  @JsonClass(generateAdapter = true)
  data class DvachSearchPostFile(
    val thumbnail: String?,
    val path: String?
  )

}
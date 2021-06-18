package com.github.k1rakishou.chan.core.site.sites.dvach

import android.text.SpannableStringBuilder
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.SiteEndpoints
import com.github.k1rakishou.chan.core.site.sites.search.DvachSearchParams
import com.github.k1rakishou.chan.core.site.sites.search.PageCursor
import com.github.k1rakishou.chan.core.site.sites.search.SearchEntry
import com.github.k1rakishou.chan.core.site.sites.search.SearchEntryPost
import com.github.k1rakishou.chan.core.site.sites.search.SearchError
import com.github.k1rakishou.chan.core.site.sites.search.SearchResult
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okio.buffer
import okio.source
import org.joda.time.DateTime
import org.jsoup.parser.Parser
import java.io.IOException
import java.io.InputStream

class DvachSearchRequest(
  private val moshi: Moshi,
  private val request: Request,
  private val proxiedOkHttpClient: ProxiedOkHttpClient,
  private val searchParams: DvachSearchParams,
  private val siteManager: SiteManager
) {

  suspend fun execute(): SearchResult {
    return withContext(Dispatchers.IO) {
      try {
        val response = proxiedOkHttpClient.okHttpClient().suspendCall(request)

        if (!response.isSuccessful) {
          throw IOException("Bad status code: ${response.code}")
        }

        if (response.body == null) {
          throw IOException("Response has no body")
        }

        return@withContext response.body!!.use { body ->
          return@use body.byteStream().use { inputStream ->
            return@use readJson(inputStream)
          }
        }
      } catch (error: Throwable) {
        return@withContext SearchResult.Failure(SearchError.UnknownError(error))
      }
    }
  }

  private fun readJson(inputStream: InputStream): SearchResult {
    val dvachSearchResultAdapter = moshi.adapter(DvachSearchResult::class.java)

    val dvachSearchResult = inputStream.source().buffer().use { buffer ->
      dvachSearchResultAdapter.fromJson(buffer)
    }

    if (dvachSearchResult == null || dvachSearchResult.posts.isEmpty()) {
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
    val posts: List<DvachSearchPost>
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
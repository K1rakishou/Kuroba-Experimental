package com.github.k1rakishou.chan.core.site.sites.fuuka

import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.site.sites.search.FuukaSearchParams
import com.github.k1rakishou.chan.core.site.sites.search.PageCursor
import com.github.k1rakishou.chan.core.site.sites.search.SearchEntry
import com.github.k1rakishou.chan.core.site.sites.search.SearchError
import com.github.k1rakishou.chan.core.site.sites.search.SearchResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCommandExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.nio.charset.StandardCharsets

class FuukaSearchRequest(
  private val verboseLogs: Boolean,
  private val searchParams: FuukaSearchParams,
  private val request: Request,
  private val proxiedOkHttpClient: ProxiedOkHttpClient
) {
  private val commandBuffer = FuukaSearchRequestParseCommandBufferBuilder().getBuilder().build()

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
            val url = request.url.toString()

            val htmlDocument = Jsoup.parse(inputStream, StandardCharsets.UTF_8.name(), url)
            return@use readHtml(url, htmlDocument)
          }
        }
      } catch (error: Throwable) {
        return@withContext SearchResult.Failure(SearchError.UnknownError(error))
      }
    }
  }

  suspend fun readHtml(url: String, document: Document): SearchResult {
    val collector = FuukaSearchRequestParseCommandBufferBuilder.FuukaSearchPageCollector(
      verboseLogs,
      searchParams.boardDescriptor
    )

    val parserCommandExecutor =
      KurobaHtmlParserCommandExecutor<FuukaSearchRequestParseCommandBufferBuilder.FuukaSearchPageCollector>()

    try {
      parserCommandExecutor.executeCommands(
        document,
        commandBuffer,
        collector,
        url
      )
    } catch (error: Throwable) {
      Logger.e(TAG, "parserCommandExecutor.executeCommands() error", error)
      return SearchResult.Failure(SearchError.ParsingError(error.errorMessageOrClassName()))
    }

    val searchEntries = collector.searchResults.mapNotNull { searchEntryPostBuilder ->
      if (searchEntryPostBuilder.hasMissingInfo()) {
        return@mapNotNull null
      }

      return@mapNotNull SearchEntry(listOf(searchEntryPostBuilder.toSearchEntryPost()))
    }

    val successResult = SearchResult.Success(
      searchParams,
      searchEntries,
      PageCursor.Page(searchParams.getCurrentPage() + 1),
      // Unknown, warosu doesn't show how many entries in total were found
      Int.MAX_VALUE
    )

    Logger.d(TAG, "searchParams=${searchParams}, foundEntriesPage=${successResult.searchEntries.size}, " +
      "pageCursor=${successResult.nextPageCursor}, totalFoundEntries=${successResult.totalFoundEntries}")

    return successResult
  }

  companion object {
    private const val TAG = "FuukaSearchRequest"
  }

}
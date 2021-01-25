package com.github.k1rakishou.chan.core.site.sites.fuuka

import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.net.HtmlReaderRequest
import com.github.k1rakishou.chan.core.site.sites.search.FuukaSearchParams
import com.github.k1rakishou.chan.core.site.sites.search.PageCursor
import com.github.k1rakishou.chan.core.site.sites.search.SearchEntry
import com.github.k1rakishou.chan.core.site.sites.search.SearchError
import com.github.k1rakishou.chan.core.site.sites.search.SearchResult
import com.github.k1rakishou.chan.utils.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCommandExecutor
import okhttp3.Request
import org.jsoup.nodes.Document

class FuukaSearchRequest(
  private val verboseLogs: Boolean,
  private val searchParams: FuukaSearchParams,
  request: Request,
  proxiedOkHttpClient: ProxiedOkHttpClient
) : HtmlReaderRequest<SearchResult>(request, proxiedOkHttpClient) {
  private val commandBuffer = FuukaSearchRequestParseCommandBufferBuilder().getBuilder().build()

  override suspend fun readHtml(document: Document): SearchResult {
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
        collector
      )
    } catch (error: Throwable) {
      Logger.e(TAG, "parserCommandExecutor.executeCommands() error", error)
      return SearchResult.Failure(SearchError.HtmlParsingError(error.errorMessageOrClassName()))
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
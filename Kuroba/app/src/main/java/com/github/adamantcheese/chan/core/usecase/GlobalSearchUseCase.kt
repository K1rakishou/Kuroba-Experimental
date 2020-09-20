package com.github.adamantcheese.chan.core.usecase

import android.text.SpannableString
import com.github.adamantcheese.chan.core.manager.SiteManager
import com.github.adamantcheese.chan.core.net.HtmlReaderRequest
import com.github.adamantcheese.chan.core.site.parser.search.Chan4SearchPostParser
import com.github.adamantcheese.chan.core.site.sites.search.SearchError
import com.github.adamantcheese.chan.core.site.sites.search.SearchParams
import com.github.adamantcheese.chan.core.site.sites.search.SearchResult
import com.github.adamantcheese.chan.ui.text.span.BackgroundColorSpanHashed
import com.github.adamantcheese.chan.ui.text.span.ForegroundColorSpanHashed
import com.github.adamantcheese.chan.ui.theme.Theme
import com.github.adamantcheese.chan.ui.theme.ThemeHelper
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.ModularResult.Companion.Try
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

class GlobalSearchUseCase(
  private val siteManager: SiteManager,
  private val themeHelper: ThemeHelper,
  private val chan4SearchPostParser: Chan4SearchPostParser
) : ISuspendUseCase<SearchParams, SearchResult> {
  override suspend fun execute(parameter: SearchParams): SearchResult {
    try {
      return withContext(Dispatchers.IO) { doSearch(parameter) }
    } catch (error: Throwable) {
      return SearchResult.Failure(SearchError.UnknownError(error))
    }
  }

  @Suppress("MoveVariableDeclarationIntoWhen")
  private suspend fun doSearch(parameter: SearchParams): SearchResult {
    siteManager.awaitUntilInitialized()

    val site = siteManager.bySiteDescriptor(parameter.siteDescriptor)
    if (site == null) {
      Logger.e(TAG, "Failed to find ${parameter.siteDescriptor}")
      return SearchResult.Failure(SearchError.SiteNotFound(parameter.siteDescriptor))
    }

    val result = Try { site.actions().search(parameter) }

    val htmlReaderResponse =  when (result) {
      is ModularResult.Value -> result.value
      is ModularResult.Error -> {
        Logger.e(TAG, "doSearch() Unknown error", result.error)
        return SearchResult.Failure(SearchError.UnknownError(result.error))
      }
    }

    when (htmlReaderResponse) {
      is HtmlReaderRequest.HtmlReaderResponse.Success -> {
        Logger.d(TAG, "doSearch() Success")
        return parseComments(htmlReaderResponse.result)
      }
      is HtmlReaderRequest.HtmlReaderResponse.ServerError -> {
        Logger.e(TAG, "doSearch() ServerError: ${htmlReaderResponse.statusCode}")
        return SearchResult.Failure(SearchError.ServerError(htmlReaderResponse.statusCode))
      }
      is HtmlReaderRequest.HtmlReaderResponse.UnknownServerError -> {
        Logger.e(TAG, "doSearch() UnknownServerError", htmlReaderResponse.error)
        return SearchResult.Failure(SearchError.UnknownError(htmlReaderResponse.error))
      }
      is HtmlReaderRequest.HtmlReaderResponse.ParsingError -> {
        Logger.d(TAG, "doSearch() ParsingError", htmlReaderResponse.error)
        return SearchResult.Failure(SearchError.UnknownError(htmlReaderResponse.error))
      }
    }
  }

  private fun parseComments(searchResult: SearchResult): SearchResult {
    if (searchResult is SearchResult.Failure) {
      return searchResult
    }

    searchResult as SearchResult.Success

    val theme = themeHelper.theme

    searchResult.searchEntries.forEach { searchEntry ->
      searchEntry.thread.posts.forEach { searchEntryPost ->
        searchEntryPost.commentRaw?.let { commentRaw ->
          val parsedComment = chan4SearchPostParser.parseComment(theme, commentRaw.toString()) ?: ""
          val spannedComment = SpannableString(parsedComment)

          findAllQueryEntriesInsideCommentAndMarkThem(searchResult.query, spannedComment, theme)
          findAllQuotesAndMarkThem(spannedComment, theme)

          commentRaw.clear()
          commentRaw.append(spannedComment)
        }
      }
    }

    return searchResult
  }

  private fun findAllQuotesAndMarkThem(spannedComment: SpannableString, theme: Theme) {
    val matcher = SIMPLE_QUOTE_PATTERN.matcher(spannedComment)

    while (matcher.find()) {
      val span = ForegroundColorSpanHashed(theme.linkColor)
      spannedComment.setSpan(span, matcher.start(), matcher.end(), 0)
    }
  }

  private fun findAllQueryEntriesInsideCommentAndMarkThem(
    query: String,
    parsedComment: SpannableString,
    theme: Theme
  ) {
    if (parsedComment.length < query.length) {
      return
    }

    var offset = 0
    val spans = mutableListOf<SpanToAdd>()

    while (offset < parsedComment.length) {
      if (query[0].equals(parsedComment[offset], ignoreCase = true)) {
        val compared = compare(query, parsedComment, offset)
        if (compared == query.length) {
          spans += SpanToAdd(offset, query.length, BackgroundColorSpanHashed(theme.accentColor.color))
        }

        offset += compared
        continue
      }

      ++offset
    }

    spans.forEach { spanToAdd ->
      parsedComment.setSpan(
        spanToAdd.span,
        spanToAdd.position,
        spanToAdd.position + spanToAdd.length,
        0
      )
    }
  }

  private fun compare(query: String, parsedComment: CharSequence, currentPosition: Int): Int {
    var compared = 0

    for (index in 1 until query.length) {
      if (!query[index].equals(parsedComment[currentPosition + index], ignoreCase = true)) {
        return compared + 1
      }

      ++compared
    }

    return compared + 1
  }

  private data class SpanToAdd(
    val position: Int,
    val length: Int,
    val span: BackgroundColorSpanHashed
  )

  companion object {
    private const val TAG = "GlobalSearchUseCase"

    private val SIMPLE_QUOTE_PATTERN = Pattern.compile(">>\\d+")
  }
}
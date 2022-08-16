package com.github.k1rakishou.chan.core.usecase

import android.text.SpannableString
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.parser.CommentParserHelper
import com.github.k1rakishou.chan.core.site.parser.search.SimpleCommentParser
import com.github.k1rakishou.chan.core.site.sites.search.Chan4SearchParams
import com.github.k1rakishou.chan.core.site.sites.search.DvachSearchParams
import com.github.k1rakishou.chan.core.site.sites.search.FoolFuukaSearchParams
import com.github.k1rakishou.chan.core.site.sites.search.FuukaSearchParams
import com.github.k1rakishou.chan.core.site.sites.search.SearchError
import com.github.k1rakishou.chan.core.site.sites.search.SearchParams
import com.github.k1rakishou.chan.core.site.sites.search.SearchResult
import com.github.k1rakishou.chan.utils.SpannableHelper
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_spannable.ForegroundColorSpanHashed
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

class GlobalSearchUseCase(
  private val siteManager: SiteManager,
  private val themeEngine: ThemeEngine,
  private val simpleCommentParser: Lazy<SimpleCommentParser>
) : ISuspendUseCase<SearchParams, SearchResult> {

  override suspend fun execute(parameter: SearchParams): SearchResult {
    return try {
      withContext(Dispatchers.IO) { doSearch(parameter) }
    } catch (error: Throwable) {
      SearchResult.Failure(SearchError.UnknownError(error))
    }
  }

  @Suppress("MoveVariableDeclarationIntoWhen")
  private suspend fun doSearch(parameter: SearchParams): SearchResult {
    siteManager.awaitUntilInitialized()

    val site = siteManager.bySiteDescriptor(parameter.siteDescriptor)
    if (site == null) {
      Logger.e(TAG, "doSearch() Failed to find ${parameter.siteDescriptor}")
      return SearchResult.Failure(SearchError.SiteNotFound(parameter.siteDescriptor))
    }

    val result = Try { site.actions().search(parameter) }

    when (result) {
      is ModularResult.Value -> {
        val searchResult = result.value

        if (searchResult is SearchResult.Success) {
          return processFoundSearchEntries(result.value as SearchResult.Success)
        }

        return searchResult
      }
      is ModularResult.Error -> {
        Logger.e(TAG, "doSearch() Unknown error", result.error)
        return SearchResult.Failure(SearchError.UnknownError(result.error))
      }
    }
  }

  private fun processFoundSearchEntries(searchResult: SearchResult.Success): SearchResult {
    val theme = themeEngine.chanTheme

    searchResult.searchEntries.forEach { searchEntry ->
      searchEntry.posts.forEach { searchEntryPost ->
        searchEntryPost.commentRaw?.let { commentRaw ->
          val parsedComment = simpleCommentParser.get().parseComment(commentRaw.toString()) ?: ""
          val spannedComment = SpannableString(parsedComment)

          SpannableHelper.findAllQueryEntriesInsideSpannableStringAndMarkThem(
            inputQueries = getAllQueries(searchResult.searchParams),
            spannableString = spannedComment,
            bgColor = theme.accentColor,
            minQueryLength = 1
          )

          findAllQuotesAndMarkThem(spannedComment, theme)
          findAllLinksAndMarkThem(spannedComment, theme)

          commentRaw.clear()
          commentRaw.append(spannedComment)
        }

        searchEntryPost.subject?.let { subject ->
          val spannedSubject = SpannableString(subject)

          SpannableHelper.findAllQueryEntriesInsideSpannableStringAndMarkThem(
            inputQueries = getAllQueries(searchResult.searchParams),
            spannableString = spannedSubject,
            bgColor = theme.accentColor,
            minQueryLength = 1
          )

          subject.clear()
          subject.append(spannedSubject)
        }
      }
    }

    return searchResult
  }

  private fun findAllQuotesAndMarkThem(spannedComment: SpannableString, theme: ChanTheme): Boolean {
    val matcher = SIMPLE_QUOTE_PATTERN.matcher(spannedComment)
    var found = false

    while (matcher.find()) {
      val span = ForegroundColorSpanHashed(theme.postQuoteColor)
      spannedComment.setSpan(span, matcher.start(), matcher.end(), 0)

      found = true
    }

    return found
  }

  private fun findAllLinksAndMarkThem(spannedComment: SpannableString, theme: ChanTheme): Boolean {
    val links = CommentParserHelper.LINK_EXTRACTOR.extractLinks(spannedComment)
    var found = false

    for (link in links) {
      val span = ForegroundColorSpanHashed(theme.postLinkColor)
      spannedComment.setSpan(span, link.beginIndex, link.endIndex, 0)

      found = true
    }

    return found
  }

  private fun getAllQueries(searchParams: SearchParams): Set<String> {
    val queries = mutableSetOf<String>()

    when (searchParams) {
      is Chan4SearchParams -> {
        queries += searchParams.query
      }
      is DvachSearchParams -> {
        queries += searchParams.query
      }
      is FuukaSearchParams -> {
        if (searchParams.query.isNotEmpty()) {
          queries += searchParams.query
        }

        if (searchParams.subject.isNotEmpty()) {
          queries += searchParams.subject
        }
      }
      is FoolFuukaSearchParams -> {
        if (searchParams.query.isNotEmpty()) {
          queries += searchParams.query
        }

        if (searchParams.subject.isNotEmpty()) {
          queries += searchParams.subject
        }
      }
      else -> throw IllegalStateException("Unknown searchParams type: ${searchParams.javaClass.simpleName}")
    }

    return queries
  }

  companion object {
    private const val TAG = "GlobalSearchUseCase"

    private val SIMPLE_QUOTE_PATTERN = Pattern.compile(">>\\d+")
  }
}
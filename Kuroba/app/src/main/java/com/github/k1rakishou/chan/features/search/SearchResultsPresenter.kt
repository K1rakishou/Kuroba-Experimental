package com.github.k1rakishou.chan.features.search

import android.graphics.Typeface
import android.text.SpannableString
import android.text.style.StyleSpan
import androidx.core.text.buildSpannedString
import com.github.k1rakishou.PersistableChanState
import com.github.k1rakishou.chan.core.base.BasePresenter
import com.github.k1rakishou.chan.core.site.sites.search.Chan4SearchParams
import com.github.k1rakishou.chan.core.site.sites.search.FoolFuukaSearchParams
import com.github.k1rakishou.chan.core.site.sites.search.PageCursor
import com.github.k1rakishou.chan.core.site.sites.search.SearchEntryPost
import com.github.k1rakishou.chan.core.site.sites.search.SearchError
import com.github.k1rakishou.chan.core.site.sites.search.SearchResult
import com.github.k1rakishou.chan.core.usecase.GlobalSearchUseCase
import com.github.k1rakishou.chan.features.search.data.CharSequenceMurMur
import com.github.k1rakishou.chan.features.search.data.CurrentQueryInfo
import com.github.k1rakishou.chan.features.search.data.ErrorInfo
import com.github.k1rakishou.chan.features.search.data.SearchParameters
import com.github.k1rakishou.chan.features.search.data.SearchPostInfo
import com.github.k1rakishou.chan.features.search.data.SearchResultsControllerState
import com.github.k1rakishou.chan.features.search.data.SearchResultsControllerStateData
import com.github.k1rakishou.chan.features.search.data.ThumbnailInfo
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.exhaustive
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_spannable.ColorizableForegroundColorSpan
import com.github.k1rakishou.core_themes.ChanThemeColorId
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.BehaviorProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormatterBuilder
import org.joda.time.format.ISODateTimeFormat
import java.util.*

internal class SearchResultsPresenter(
  private val siteDescriptor: SiteDescriptor,
  private val searchParameters: SearchParameters,
  private val globalSearchUseCase: GlobalSearchUseCase,
  private val themeEngine: ThemeEngine
) : BasePresenter<SearchResultsView>(), ThemeEngine.ThemeChangesListener {

  private val searchResultsControllerStateSubject =
    BehaviorProcessor.createDefault<SearchResultsControllerState>(SearchResultsControllerState.Uninitialized)
  private val searchResultsStateStorage = SearchResultsStateStorage

  @get:Synchronized
  @set:Synchronized
  private var currentPage: Int? = null

  override fun onCreate(view: SearchResultsView) {
    super.onCreate(view)
    searchParameters.checkValid()

    scope.launch {
      if (searchResultsStateStorage.searchResultsState != null) {
        setState(SearchResultsControllerState.Data(searchResultsStateStorage.searchResultsState!!))
        return@launch
      }

      doSearch()
    }

    themeEngine.addListener(this)
  }

  override fun onDestroy() {
    super.onDestroy()
    themeEngine.removeListener(this)
  }

  override fun onThemeChanged() {
    scope.launch {
      val dataState = (searchResultsControllerStateSubject.value as? SearchResultsControllerState.Data)?.data
      if (dataState == null) {
        return@launch
      }

      val themeHash = themeEngine.chanTheme.hashCode()

      // Update the post hash so redraw everything with new theme colors
      dataState.searchPostInfoList.forEach { searchPostInfo ->
        searchPostInfo.themeHash = themeHash
      }

      setState(SearchResultsControllerState.Data(dataState))
      searchResultsStateStorage.updateSearchResultsState(dataState)
    }
  }

  fun listenForStateChanges(): Flowable<SearchResultsControllerState> {
    return searchResultsControllerStateSubject
      .onBackpressureLatest()
      .observeOn(AndroidSchedulers.mainThread())
      .doOnError { error ->
        Logger.e(TAG, "Unknown error subscribed to searchResultsPresenter.listenForStateChanges()", error)
      }
      .onErrorReturn { error -> SearchResultsControllerState.Data(errorState(error.errorMessageOrClassName())) }
      .hide()
  }

  fun resetSavedState() {
    searchResultsStateStorage.resetSearchResultState()
  }

  fun resetLastRecyclerViewScrollState() {
    searchResultsStateStorage.resetLastRecyclerViewScrollState()
  }

  fun updateLastRecyclerViewScrollState(indexAndTop: PersistableChanState.IndexAndTop) {
    searchResultsStateStorage.updateLastRecyclerViewScrollState(indexAndTop)
  }

  fun lastRecyclerViewScrollStateOrNull(): PersistableChanState.IndexAndTop? {
    return searchResultsStateStorage.lastRecyclerViewScrollState
  }

  fun reloadCurrentPage() {
    scope.launch {
      val prevState = requireNotNull(searchResultsControllerStateSubject.value) { "Initial state was not set!" }
      val prevStateData = (prevState as? SearchResultsControllerState.Data)?.data

      if (prevStateData?.errorInfo != null) {
        // Get rid of the error and show loading indicator
        val stateWithoutError = prevStateData.copy(errorInfo = null)
        setState(SearchResultsControllerState.Data(stateWithoutError))
      }

      doSearch()
    }
  }

  fun loadNewPage(data: SearchResultsControllerStateData) {
    scope.launch {
      val nextPage = (data.nextPageCursor as? PageCursor.Page)?.value
        ?: return@launch

      currentPage = nextPage
      doSearch()
    }
  }

  private suspend fun doSearch() {
    withContext(Dispatchers.Default) {
      BackgroundUtils.ensureBackgroundThread()
      Logger.d(TAG, "doSearch() siteDescriptor=$siteDescriptor, searchParameters=$searchParameters, currentPage=$currentPage")

      val prevState = requireNotNull(searchResultsControllerStateSubject.value) { "Initial state was not set!" }
      val prevStateData = (prevState as? SearchResultsControllerState.Data)?.data

      val searchResult = executeRequest()
      if (searchResult is SearchResult.Failure) {
        if (prevStateData == null) {
          setState(SearchResultsControllerState.Data(errorState(searchFailureToErrorText(searchResult))))
          return@withContext
        }

        val newDataState = prevStateData.copy(errorInfo = ErrorInfo(searchFailureToErrorText(searchResult)))
        setState(SearchResultsControllerState.Data(newDataState))
        return@withContext
      }

      searchResult as SearchResult.Success

      if (searchResult.totalFoundEntries == null || searchResult.totalFoundEntries <= 0) {
        Logger.d(TAG, "doSearch() nothing found, query = ${searchResult.query}")
        setState(SearchResultsControllerState.NothingFound(searchResult.query))
        return@withContext
      }

      Logger.d(TAG, "doSearch() found = ${searchResult.searchEntries.size} results " +
        "and ${searchResult.totalFoundEntries} in total")
      val newStateData = createNewDataState(prevStateData, searchResult)

      setState(SearchResultsControllerState.Data(newStateData))
      searchResultsStateStorage.updateSearchResultsState(newStateData)
    }
  }

  private suspend fun executeRequest(): SearchResult {
    val requestSearchParams = when (val params = searchParameters) {
      is SearchParameters.SimpleQuerySearchParameters -> {
        Chan4SearchParams(siteDescriptor, params.query, currentPage)
      }
      is SearchParameters.FoolFuukaSearchParameters -> {
        FoolFuukaSearchParams(params.boardDescriptor!!, params.query, currentPage)
      }
    }.exhaustive

    return globalSearchUseCase.execute(requestSearchParams)
  }

  private fun createNewDataState(
    prevStateData: SearchResultsControllerStateData?,
    searchResult: SearchResult.Success
  ): SearchResultsControllerStateData {
    val combinedSearchPostInfoList = prevStateData?.searchPostInfoList?.toMutableList()
      ?: mutableListOf()

    val postDescriptorsSet = combinedSearchPostInfoList
      .map { searchPostInfo -> searchPostInfo.postDescriptor }
      .toSet()

    val themeHash = themeEngine.chanTheme.hashCode()

    searchResult.searchEntries.forEach { searchEntry ->
      searchEntry.posts.forEach { searchEntryPost ->
        if (searchEntryPost.postDescriptor in postDescriptorsSet) {
          // 4chan can show the same post multiple times so we need to filter out duplicates to avoid
          // crashes
          return@forEach
        }

        combinedSearchPostInfoList += SearchPostInfo(
          postDescriptor = searchEntryPost.postDescriptor,
          opInfo = createOpInfo(searchEntryPost),
          postInfo = createPostInfo(searchEntryPost),
          thumbnail = createThumbnailInfo(searchEntryPost),
          postComment = createPostComment(searchEntryPost),
          themeHash = themeHash
        )
      }
    }

    return SearchResultsControllerStateData(
      searchPostInfoList = combinedSearchPostInfoList,
      nextPageCursor = searchResult.nextPageCursor,
      errorInfo = null,
      currentQueryInfo = CurrentQueryInfo(searchResult.query, searchResult.totalFoundEntries)
    )
  }

  private fun createPostComment(searchEntryPost: SearchEntryPost): CharSequenceMurMur {
    if (searchEntryPost.commentRaw == null) {
      return CharSequenceMurMur.empty()
    }

    return CharSequenceMurMur.create(searchEntryPost.commentRaw.trim())
  }

  private fun createThumbnailInfo(searchEntryPost: SearchEntryPost): ThumbnailInfo? {
    if (searchEntryPost.postImageUrlRawList.isEmpty()) {
      return null
    }

    return ThumbnailInfo(searchEntryPost.postImageUrlRawList.first())
  }

  private fun createPostInfo(searchEntryPost: SearchEntryPost): CharSequenceMurMur {
    val name = searchEntryPost.name?.trim() ?: "Anonymous"
    val date = POST_DATE_TIME_FORMATTER.print(searchEntryPost.dateTime)
    val postNo = searchEntryPost.postDescriptor.postNo

    val text = "$name $date No. $postNo"
    return CharSequenceMurMur.create(text)
  }

  private fun createOpInfo(searchEntryPost: SearchEntryPost): CharSequenceMurMur? {
    if (!searchEntryPost.isOp) {
      return null
    }

    val boardCode = searchEntryPost.postDescriptor.boardDescriptor().boardCode
    val threadNo = searchEntryPost.postDescriptor.getThreadNo()
    val subject = searchEntryPost.subject?.trim()

    val text = buildSpannedString {
      append("Board: ")
      append(boldSpanned(boardCode))
      append(" ")
      append("Thread: ")
      append(boldSpanned(threadNo.toString()))

      if (subject != null) {
        append(" ")
        append(subjectSpanned(subject))
      }
    }

    return CharSequenceMurMur.create(text)
  }

  private fun boldSpanned(text: CharSequence): SpannableString {
    val spannedSubject = SpannableString(text)
    spannedSubject.setSpan(StyleSpan(Typeface.BOLD), 0, spannedSubject.length, 0)
    return spannedSubject
  }

  private fun subjectSpanned(text: CharSequence): SpannableString {
    val spannedSubject = SpannableString(text)

    spannedSubject.setSpan(
      ColorizableForegroundColorSpan(ChanThemeColorId.PostSubjectColor),
      0,
      spannedSubject.length,
      0
    )

    return spannedSubject
  }

  private fun searchFailureToErrorText(failure: SearchResult.Failure): String {
    return when (failure.searchError) {
      SearchError.NotImplemented -> "Not implemented"
      is SearchError.SiteNotFound -> "Site \"${siteDescriptor.siteName}\" was not found in the database"
      is SearchError.ServerError -> "Bad response status: ${failure.searchError.statusCode}"
      is SearchError.UnknownError -> "Unknown error: ${failure.searchError.error.errorMessageOrClassName()}"
      is SearchError.HtmlParsingError -> "Html parsing error: ${failure.searchError.message}"
    }
  }

  private fun errorState(errorText: String): SearchResultsControllerStateData {
    val prevState = requireNotNull(searchResultsControllerStateSubject.value) { "Initial state was not set!" }
    val prevStateData = (prevState as? SearchResultsControllerState.Data)?.data
      ?: SearchResultsControllerStateData()

    return prevStateData.copy(errorInfo = ErrorInfo(errorText))
  }

  private fun setState(state: SearchResultsControllerState) {
    searchResultsControllerStateSubject.onNext(state)
  }

  companion object {
    private const val TAG = "SearchResultsPresenter"

    private val POST_DATE_TIME_FORMATTER = DateTimeFormatterBuilder()
      .append(ISODateTimeFormat.date())
      .appendLiteral(' ')
      .append(ISODateTimeFormat.hourMinuteSecond())
      .toFormatter()
      .withZone(DateTimeZone.forTimeZone(TimeZone.getDefault()))
  }
}
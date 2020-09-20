package com.github.adamantcheese.chan.features.search

import android.graphics.Typeface
import android.text.SpannableString
import android.text.style.StyleSpan
import androidx.core.text.buildSpannedString
import com.github.adamantcheese.chan.Chan
import com.github.adamantcheese.chan.core.base.BasePresenter
import com.github.adamantcheese.chan.core.site.sites.search.*
import com.github.adamantcheese.chan.core.usecase.GlobalSearchUseCase
import com.github.adamantcheese.chan.features.search.data.*
import com.github.adamantcheese.chan.ui.text.span.ForegroundColorSpanHashed
import com.github.adamantcheese.chan.ui.theme.Theme
import com.github.adamantcheese.chan.ui.theme.ThemeHelper
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.common.errorMessageOrClassName
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.github.adamantcheese.model.data.descriptor.SiteDescriptor
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.BehaviorProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal class SearchResultsPresenter(
  private val siteDescriptor: SiteDescriptor,
  private val query: String
) : BasePresenter<SearchResultsView>() {

  @Inject
  lateinit var globalSearchUseCase: GlobalSearchUseCase
  @Inject
  lateinit var themeHelper: ThemeHelper

  private val searchResultsControllerStateSubject =
    BehaviorProcessor.createDefault<SearchResultsControllerState>(SearchResultsControllerState.InitialLoading)

  @get:Synchronized
  @set:Synchronized
  private var currentPage: Int? = null

  override fun onCreate(view: SearchResultsView) {
    super.onCreate(view)

    Chan.inject(this)
    require(query.length >= GlobalSearchPresenter.MIN_SEARCH_QUERY_LENGTH) { "Bad query length: \"$query\"" }

    scope.launch {
      doSearch()
    }
  }

  fun listenForStateChanges(): Flowable<SearchResultsControllerState> {
    return searchResultsControllerStateSubject
      .onBackpressureLatest()
      .distinctUntilChanged()
      .observeOn(AndroidSchedulers.mainThread())
      .doOnError { error ->
        Logger.e(TAG, "Unknown error subscribed to searchResultsPresenter.listenForStateChanges()", error)
      }
      .onErrorReturn { error -> SearchResultsControllerState.Data(errorState(error.errorMessageOrClassName())) }
      .hide()
  }

  fun onSearchPostClicked(postDescriptor: PostDescriptor) {
    // TODO(KurobaEx): redirect to a thread
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
      if (nextPage == null) {
        return@launch
      }

      currentPage = nextPage
      doSearch()
    }
  }

  private suspend fun doSearch() {
    withContext(Dispatchers.Default) {
      BackgroundUtils.ensureBackgroundThread()

      val prevState = requireNotNull(searchResultsControllerStateSubject.value) { "Initial state was not set!" }
      val prevStateData = (prevState as? SearchResultsControllerState.Data)?.data

      val searchResult = globalSearchUseCase.execute(SearchParams(siteDescriptor, query, currentPage))
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
        setState(SearchResultsControllerState.NothingFound(searchResult.query))
        return@withContext
      }

      val newStateData = createNewDataState(prevStateData, searchResult)
      setState(SearchResultsControllerState.Data(newStateData))
    }
  }

  private fun createNewDataState(
    prevStateData: SearchResultsControllerStateData?,
    searchResult: SearchResult.Success
  ): SearchResultsControllerStateData {
    val combinedSearchPostInfoList = prevStateData?.searchPostInfoList?.toMutableList()
      ?: mutableListOf()

    val theme = themeHelper.theme

    searchResult.searchEntries.forEach { searchEntry ->
      searchEntry.thread.posts.forEach { searchEntryPost ->
        combinedSearchPostInfoList += SearchPostInfo(
          postDescriptor = searchEntryPost.postDescriptor,
          opInfo = createOpInfo(searchEntryPost, theme),
          postInfo = createPostInfo(searchEntryPost),
          thumbnail = createThumbnailInfo(searchEntryPost),
          postComment = createPostComment(searchEntryPost)
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
    val date = searchEntryPost.dateTime.toString()
    val postNo = searchEntryPost.postDescriptor.postNo

    val text = "$name $date No. $postNo"
    return CharSequenceMurMur.create(text)
  }

  private fun createOpInfo(searchEntryPost: SearchEntryPost, theme: Theme): CharSequenceMurMur? {
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
        append(subjectSpanned(subject, theme))
      }
    }

    return CharSequenceMurMur.create(text)
  }

  private fun boldSpanned(text: CharSequence): SpannableString {
    val spannedSubject = SpannableString(text)
    spannedSubject.setSpan(StyleSpan(Typeface.BOLD), 0, spannedSubject.length, 0)
    return spannedSubject
  }

  private fun subjectSpanned(text: CharSequence, theme: Theme): SpannableString {
    val spannedSubject = SpannableString(text)
    spannedSubject.setSpan(ForegroundColorSpanHashed(theme.subjectColor), 0, spannedSubject.length, 0)
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
  }
}
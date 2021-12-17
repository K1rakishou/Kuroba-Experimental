package com.github.k1rakishou.chan.features.reply_image_search.searx

import androidx.compose.runtime.mutableStateOf
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.compose.AsyncData
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.usecase.SearxImageSearchUseCase
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.persist_state.PersistableChanState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject

class SearxImageSearchControllerViewModel : BaseViewModel() {
  @Inject
  lateinit var searxImageSearchUseCase: SearxImageSearchUseCase

  var baseSearchUrl = mutableStateOf(PersistableChanState.searxLastUsedInstanceUrl.get())
  var searchQuery = mutableStateOf("")
  var searchResults = mutableStateOf<AsyncData<List<SearxImage>>>(AsyncData.NotInitialized)

  private var _rememberedFirstVisibleItemIndex: Int = 0
  val rememberedFirstVisibleItemIndex: Int
    get() = _rememberedFirstVisibleItemIndex

  private var _rememberedFirstVisibleItemScrollOffset: Int = 0
  val rememberedFirstVisibleItemScrollOffset: Int
    get() = _rememberedFirstVisibleItemScrollOffset

  private var _currentPage = 0
  val currentPage: Int
    get() = _currentPage

  val searchErrorToastFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)

  private var activeSearchJob: Job? = null

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  override suspend fun onViewModelReady() {
    if (baseSearchUrl.value.isNullOrEmpty()) {
      baseSearchUrl.value = HINT_URL.toString()
    }
  }

  override fun onCleared() {
    super.onCleared()

    cleanup()
  }

  fun updatePrevLazyListState(firstVisibleItemIndex: Int, firstVisibleItemScrollOffset: Int) {
    _rememberedFirstVisibleItemIndex = firstVisibleItemIndex
    _rememberedFirstVisibleItemScrollOffset = firstVisibleItemScrollOffset
  }

  fun cleanup() {
    activeSearchJob?.cancel()
    activeSearchJob = null
  }

  fun search(page: Int) {
    this._currentPage = page

    activeSearchJob?.cancel()
    activeSearchJob = null

    activeSearchJob = mainScope.launch {
      val baseUrl = baseSearchUrl.value.toHttpUrlOrNull()
      if (baseUrl == null) {
        searchErrorToastFlow.tryEmit("Bad baseUrl: '${baseSearchUrl.value}'")
        return@launch
      }

      if (page == 1 || searchResults.value is AsyncData.NotInitialized) {
        PersistableChanState.searxLastUsedInstanceUrl.set(baseUrl.toString())
      }

      val query = searchQuery.value
      if (query.length < MIN_SEARCH_QUERY_LEN) {
        searchErrorToastFlow.tryEmit("Bad query length: query='$query', length=${query.length}, " +
          "must be $MIN_SEARCH_QUERY_LEN or more characters")
        return@launch
      }

      if (page == 1 || searchResults.value is AsyncData.NotInitialized) {
        _rememberedFirstVisibleItemIndex = 0
        _rememberedFirstVisibleItemScrollOffset = 0
        searchResults.value = AsyncData.Loading
      }

      val searchUrl = baseUrl.newBuilder()
        .addPathSegment("search")
        .addQueryParameter("q", query)
        .addQueryParameter("categories", "images")
        .addQueryParameter("language", "en-US")
        .addQueryParameter("format", "json")
        .addQueryParameter("pageno", "$page")
        .build()

      Logger.d(TAG, "search() searchUrl=${searchUrl}")
      val searxImagesResult = searxImageSearchUseCase.execute(searchUrl)

      val newSearxImages = if (searxImagesResult is ModularResult.Error) {
        searchResults.value = AsyncData.Error(searxImagesResult.error)
        return@launch
      } else {
        searxImagesResult as ModularResult.Value
        searxImagesResult.value
      }

      val prevSearxImages = (searchResults.value as? AsyncData.Data)?.data?.toList()
      if (prevSearxImages == null) {
        searchResults.value = AsyncData.Data(newSearxImages)
      } else {
        searchResults.value = AsyncData.Data(prevSearxImages + newSearxImages)
      }
    }
  }

  companion object {
    private const val TAG = "SearxImageSearchControllerViewModel"
    private const val MIN_SEARCH_QUERY_LEN = 3
    val HINT_URL = "https://searx.prvcy.eu".toHttpUrl()
  }
}
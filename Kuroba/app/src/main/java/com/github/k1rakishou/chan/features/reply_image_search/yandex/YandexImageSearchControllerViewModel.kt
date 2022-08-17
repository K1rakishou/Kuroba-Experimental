package com.github.k1rakishou.chan.features.reply_image_search.yandex

import androidx.compose.runtime.mutableStateOf
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.compose.AsyncData
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.usecase.YandexImageSearchUseCase
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import javax.inject.Inject

class YandexImageSearchControllerViewModel : BaseViewModel() {
  @Inject
  lateinit var yandexImageSearchUseCase: YandexImageSearchUseCase

  var searchQuery = mutableStateOf("")
  var searchResults = mutableStateOf<AsyncData<List<YandexImage>>>(AsyncData.NotInitialized)

  private var _rememberedFirstVisibleItemIndex: Int = 0
  val rememberedFirstVisibleItemIndex: Int
    get() = _rememberedFirstVisibleItemIndex

  private var _rememberedFirstVisibleItemScrollOffset: Int = 0
  val rememberedFirstVisibleItemScrollOffset: Int
    get() = _rememberedFirstVisibleItemScrollOffset

  val searchErrorToastFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)

  private var activeSearchJob: Job? = null

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  override suspend fun onViewModelReady() {
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

  fun search() {
    activeSearchJob?.cancel()
    activeSearchJob = null

    activeSearchJob = mainScope.launch {
      val query = searchQuery.value
      if (query.length < MIN_SEARCH_QUERY_LEN) {
        searchErrorToastFlow.tryEmit("Bad query length: query='$query', length=${query.length}, " +
          "must be $MIN_SEARCH_QUERY_LEN or more characters")
        return@launch
      }

      _rememberedFirstVisibleItemIndex = 0
      _rememberedFirstVisibleItemScrollOffset = 0
      searchResults.value = AsyncData.Loading

      val searchUrl = baseUrl.newBuilder()
        .addPathSegment("images")
        .addPathSegment("search")
        .addQueryParameter("text", query)
        .build()

      Logger.d(TAG, "search() searchUrl=${searchUrl}")
      val yandexImagesResult = yandexImageSearchUseCase.execute(searchUrl)

      val newYandexImages = if (yandexImagesResult is ModularResult.Error) {
        searchResults.value = AsyncData.Error(yandexImagesResult.error)
        return@launch
      } else {
        yandexImagesResult as ModularResult.Value
        yandexImagesResult.value.distinctBy { it.thumbnailUrl }
      }

      searchResults.value = AsyncData.Data(newYandexImages)
    }
  }

  companion object {
    private const val TAG = "YandexImageSearchControllerViewModel"
    private const val MIN_SEARCH_QUERY_LEN = 2

    private val baseUrl = "https://yandex.ru".toHttpUrl()
  }
}
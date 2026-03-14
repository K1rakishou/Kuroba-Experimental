package com.github.k1rakishou.chan.features.search.remotemedia

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.viewmodel.KurobaViewModel
import com.github.k1rakishou.chan.core.compose.AsyncUiData
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.di.module.shared.ViewModelAssistedFactory
import com.github.k1rakishou.chan.core.usecase.SearxImageSearchUseCase
import com.github.k1rakishou.chan.core.usecase.YandexImageSearchUseCase
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.common.FirewallDetectedException
import com.github.k1rakishou.common.FirewallType
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.v2.KurobaSettings
import com.github.k1rakishou.v2.parameters.RemoteImageSearchSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject

class ImageSearchControllerViewModel(
  private val savedStateHandle: SavedStateHandle,
  private val kurobaSettings: KurobaSettings,
  private val searxImageSearchUseCase: SearxImageSearchUseCase,
  private val yandexImageSearchUseCase: YandexImageSearchUseCase,
) : KurobaViewModel() {

  private val _lastUsedSearchInstance = mutableStateOf<RemoteImageSearchSettings.InstanceType?>(null)
  val lastUsedSearchInstance: State<RemoteImageSearchSettings.InstanceType?>
    get() = _lastUsedSearchInstance

  private val _searchInstances = mutableStateMapOf<RemoteImageSearchSettings.InstanceType, ImageSearchInstance>()
  val searchInstances: Map<RemoteImageSearchSettings.InstanceType, ImageSearchInstance>
    get() = _searchInstances

  private val _searchResults = mutableStateMapOf<RemoteImageSearchSettings.InstanceType, AsyncUiData<ImageResults>>()
  val searchResults: Map<RemoteImageSearchSettings.InstanceType, AsyncUiData<ImageResults>>
    get() = _searchResults

  private val _solvingCaptcha = MutableStateFlow<HttpUrl?>(null)
  val solvingCaptcha: StateFlow<HttpUrl?>
    get() = _solvingCaptcha.asStateFlow()

  val baseUrlError = mutableStateOf<String?>(null)
  val baseUrl = mutableStateOf("")
  val searchQuery = mutableStateOf("")
  val searchErrorToastFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)

  private var activeSearchJob: Job? = null

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  override suspend fun onViewModelReady() {
    Snapshot.withMutableSnapshot {
      ImageSearchInstance.createAll(kurobaSettings).forEach { imageSearchInstance ->
        _searchInstances[imageSearchInstance.type] = imageSearchInstance
        baseUrl.value = imageSearchInstance.baseUrl().toString()
      }

      val lastUsedSearchType = kurobaSettings.internal.remoteImageSearchSettings.read().lastUsedSearchType
        ?: RemoteImageSearchSettings.InstanceType.Yandex

      changeSearchInstance(lastUsedSearchType)
    }
  }

  override fun onCleared() {
    super.onCleared()

    cleanup()
  }

  suspend fun updateYandexSmartCaptchaCookies(newCookies: String) {
    val imageSearchInstance = getCurrentSearchInstance()
      ?: return

    imageSearchInstance.updateCookies(newCookies)
  }

  fun finishedSolvingCaptcha() {
    _solvingCaptcha.value = null
  }

  fun changeSearchInstance(newImageSearchInstanceType: RemoteImageSearchSettings.InstanceType) {
    _lastUsedSearchInstance.value = newImageSearchInstanceType

    val baseUrlFromSettings = kurobaSettings.internal.remoteImageSearchSettings.readBlocking()
      .byImageSearchInstanceType(newImageSearchInstanceType)
      ?.baseUrl
      ?.toHttpUrlOrNull()

    baseUrl.value = baseUrlFromSettings?.toString()
      ?: _searchInstances[newImageSearchInstanceType]!!.baseUrl().toString()

    onBaseUrlChanged(baseUrl.value)

    val prevQuery = getCurrentSearchInstance()?.searchQuery
    val newQuery = searchQuery.value

    val searchResults = _searchResults[newImageSearchInstanceType]
    if (
      (searchResults !is AsyncUiData.UiData || prevQuery != newQuery) &&
      newQuery.isNotEmpty() &&
      baseUrlFromSettings != null
    ) {
      onSearchQueryChanged(newQuery)
    }

    val prev = kurobaSettings.internal.remoteImageSearchSettings.readBlocking()
    if (prev.lastUsedSearchType != newImageSearchInstanceType) {
      kurobaSettings.internal.remoteImageSearchSettings.writeAsync(
        value = prev.copy(lastUsedSearchType = newImageSearchInstanceType)
      )
    }
  }

  fun updatePrevLazyListState(firstVisibleItemIndex: Int, firstVisibleItemScrollOffset: Int) {
    val imageSearchInstance = getCurrentSearchInstance()
      ?: return

    imageSearchInstance.updateLazyListState(firstVisibleItemIndex, firstVisibleItemScrollOffset)
  }

  fun cleanup() {
    activeSearchJob?.cancel()
    activeSearchJob = null
  }

  fun reload() {
    getCurrentSearchInstance()?.let { imageSearchInstance ->
      imageSearchInstance.updateCurrentPage(0)
      imageSearchInstance.updateLazyListState(0, 0)
    }

    onSearchQueryChanged(searchQuery.value)
  }

  fun reloadCurrentPage() {
    doSearchInternal(searchQuery.value)
  }

  fun onBaseUrlChanged(newBaseUrlRaw: String) {
    val imageSearchInstance = getCurrentSearchInstance()
      ?: return

    val newBaseUrl = newBaseUrlRaw.toHttpUrlOrNull()
    if (newBaseUrl == null) {
      baseUrlError.value = getString(R.string.image_search_controller_not_an_url_error)
      return
    }

    baseUrlError.value = null
    baseUrl.value = newBaseUrl.toString()

    runBlocking {
      kurobaSettings.internal.remoteImageSearchSettings.readBlocking().update(
        internalSettings = kurobaSettings.internal,
        instanceType = imageSearchInstance.type,
        updater = { old -> old.copy(baseUrl = newBaseUrl.toString()) },
        creator = {
          RemoteImageSearchSettings.InstanceSettings(
            instanceType = imageSearchInstance.type,
            baseUrl = newBaseUrl.toString(),
            cookies = null
          )
        }
      )
    }
  }

  fun onSearchQueryChanged(newQuery: String) {
    val imageSearchInstance = getCurrentSearchInstance()
      ?: return

    _searchResults[imageSearchInstance.type] = AsyncUiData.Loading
    imageSearchInstance.updateCurrentPage(0)
    imageSearchInstance.updateLazyListState(
      firstVisibleItemIndex = 0,
      firstVisibleItemScrollOffset = 0
    )

    doSearchInternal(
      query = newQuery,
      debounce = true
    )
  }

  fun onNewPageRequested(page: Int) {
    val imageSearchInstance = getCurrentSearchInstance()
      ?: return

    imageSearchInstance.updateCurrentPage(page)

    doSearchInternal(searchQuery.value)
  }

  private fun doSearchInternal(query: String, debounce: Boolean = false) {
    activeSearchJob?.cancel()
    activeSearchJob = null

    activeSearchJob = viewModelScope.launch {
      if (debounce) {
        delay(500L)
      }

      if (_solvingCaptcha.value != null) {
        return@launch
      }

      val baseUrl = baseUrl.value.toHttpUrlOrNull()
        ?: return@launch

      val currentImageSearchInstance = getCurrentSearchInstance()
        ?: return@launch

      currentImageSearchInstance.updateSearchQuery(query)

      if (query.isEmpty()) {
        _searchResults[currentImageSearchInstance.type] = AsyncUiData.NotInitialized
        return@launch
      }

      val searchUrl = currentImageSearchInstance.buildSearchUrl(
        baseUrl = baseUrl,
        query = query,
        page = currentImageSearchInstance.currentPage
      )

      val hasCookies = currentImageSearchInstance.cookies.isNotNullNorEmpty()

      Logger.d(TAG, "search() query=\'$query\', page=${currentImageSearchInstance.currentPage}, " +
        "hasCookies=${hasCookies}, searchUrl=${searchUrl}")

      val foundImagesResult = when (currentImageSearchInstance.type) {
        RemoteImageSearchSettings.InstanceType.Searx -> {
          searxImageSearchUseCase.execute(searchUrl)
        }
        RemoteImageSearchSettings.InstanceType.Yandex -> {
          val params = YandexImageSearchUseCase.Params(
            searchUrl = searchUrl,
            cookies = currentImageSearchInstance.cookies
          )

          yandexImageSearchUseCase.execute(params)
        }
      }

      val newFoundImages = if (foundImagesResult is ModularResult.Error) {
        val error = foundImagesResult.error

        if (error is FirewallDetectedException && error.firewallType == FirewallType.YandexSmartCaptcha) {
          _solvingCaptcha.emit(error.requestUrl)
          return@launch
        }

        _searchResults[currentImageSearchInstance.type] = AsyncUiData.Error(error)
        return@launch
      } else {
        foundImagesResult as ModularResult.Value
        foundImagesResult.value
      }

      Logger.d(TAG, "search() got ${newFoundImages.size} results")

      val prevImageResults = (_searchResults[currentImageSearchInstance.type] as? AsyncUiData.UiData)?.data
      _searchResults[currentImageSearchInstance.type] = when {
        prevImageResults == null -> {
          AsyncUiData.UiData(ImageResults(newFoundImages))
        }
        newFoundImages.isNotEmpty() -> {
          AsyncUiData.UiData(prevImageResults.append(newFoundImages))
        }
        else -> {
          AsyncUiData.UiData(prevImageResults.endReached())
        }
      }
    }
  }

  private fun getCurrentSearchInstance(): ImageSearchInstance? {
    val imageSearchInstanceType = _lastUsedSearchInstance.value
      ?: return null

    return _searchInstances[imageSearchInstanceType]
  }

  class ImageResults(
    val results: List<ImageSearchResult>,
    val endReached: Boolean = false
  ) {

    fun append(newFoundImages: List<ImageSearchResult>): ImageResults {
      return ImageResults(
        results = results + newFoundImages,
        endReached = endReached
      )
    }

    fun endReached(): ImageResults {
      return ImageResults(
        results = results,
        endReached = true
      )
    }

  }

  class ViewModelFactory @Inject constructor(
    private val kurobaSettings: KurobaSettings,
    private val searxImageSearchUseCase: SearxImageSearchUseCase,
    private val yandexImageSearchUseCase: YandexImageSearchUseCase,
  ) : ViewModelAssistedFactory<ImageSearchControllerViewModel> {
    override fun create(handle: SavedStateHandle): ImageSearchControllerViewModel {
      return ImageSearchControllerViewModel(
        savedStateHandle = handle,
        kurobaSettings = kurobaSettings,
        searxImageSearchUseCase = searxImageSearchUseCase,
        yandexImageSearchUseCase = yandexImageSearchUseCase
      )
    }
  }

  companion object {
    private const val TAG = "ImageSearchControllerViewModel"
  }
}
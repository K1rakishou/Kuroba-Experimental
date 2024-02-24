package com.github.k1rakishou.chan.features.site_archive

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.compose.AsyncData
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.di.module.viewmodel.ViewModelAssistedFactory
import com.github.k1rakishou.chan.core.manager.SeenPostsManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.sites.archive.NativeArchivePost
import com.github.k1rakishou.chan.core.site.sites.archive.NativeArchivePostList
import com.github.k1rakishou.common.BadStatusResponseException
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import kotlinx.coroutines.launch
import javax.inject.Inject

class BoardArchiveViewModel(
  private val savedStateHandle: SavedStateHandle,
  private val siteManager: SiteManager,
  private val seenPostsManager: SeenPostsManager
) : BaseViewModel() {
  private var _state = mutableStateOf<AsyncData<Unit>>(AsyncData.NotInitialized)
  val state: State<AsyncData<Unit>>
    get() = _state

  private var _archiveThreads = mutableStateListOf<ArchiveThread>()
  val archiveThreads: List<ArchiveThread>
    get() = _archiveThreads

  private var _endReached = mutableStateOf(false)
  val endReached: State<Boolean>
    get() = _endReached

  private var _page = mutableStateOf<Int?>(null)
  val page: State<Int?>
    get() = _page

  private val catalogDescriptor: ChanDescriptor.CatalogDescriptor
    get() = requireNotNull(savedStateHandle.get<ChanDescriptor.CatalogDescriptor>(CatalogDescriptorParam)) {
      "catalog_descriptor is null!"
    }

  var currentlySelectedThreadNo = mutableStateOf<Long?>(null)

  private var _rememberedFirstVisibleItemIndex: Int = 0
  val rememberedFirstVisibleItemIndex: Int
    get() = _rememberedFirstVisibleItemIndex

  private var _rememberedFirstVisibleItemScrollOffset: Int = 0
  val rememberedFirstVisibleItemScrollOffset: Int
    get() = _rememberedFirstVisibleItemScrollOffset

  val alreadyVisitedThreads = mutableStateMapOf<ChanDescriptor.ThreadDescriptor, Unit>()

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  override suspend fun onViewModelReady() {
    alreadyVisitedThreads.clear()

    viewModelScope.launch {
      seenPostsManager.seenThreadUpdatesFlow.collect { seenThread ->
        alreadyVisitedThreads.put(seenThread, Unit)
      }
    }

    loadPageOfArchiveThreads()
  }

  fun loadNextPageOfArchiveThreads() {
    viewModelScope.launch { loadPageOfArchiveThreads() }
  }

  private suspend fun loadPageOfArchiveThreads() {
    if (_endReached.value) {
      return
    }

    _state.value = AsyncData.Loading
    Logger.d(TAG, "loadPageOfArchiveThreads() catalogDescriptor: ${catalogDescriptor} page: ${page.value}")

    val nativeArchivePostListResult = siteManager.bySiteDescriptor(catalogDescriptor.siteDescriptor())
      ?.actions()
      ?.archive(catalogDescriptor.boardDescriptor, page.value)

    if (_archiveThreads.isEmpty() && nativeArchivePostListResult == null) {
      val exception = ArchiveNotSupportedException(catalogDescriptor.boardCode())
      _state.value = AsyncData.Error(exception)
      return
    }

    val nativeArchivePostList = if (nativeArchivePostListResult is ModularResult.Error) {
      val error = nativeArchivePostListResult.error

      if (error is BadStatusResponseException && error.status == 404) {
        val exception = ArchiveNotSupportedException(catalogDescriptor.boardCode())
        _state.value = AsyncData.Error(exception)
        return
      }

      _state.value = AsyncData.Error(error)
      return
    } else {
      nativeArchivePostListResult?.valueOrNull() ?: NativeArchivePostList()
    }

    val threadDescriptors = nativeArchivePostList.posts.map { nativeArchivePost ->
      when (nativeArchivePost) {
        is NativeArchivePost.Chan4NativeArchivePost -> {
          return@map ChanDescriptor.ThreadDescriptor.create(
            chanDescriptor = catalogDescriptor,
            threadNo = nativeArchivePost.threadNo
          )
        }
        is NativeArchivePost.DvachNativeArchivePost -> {
          return@map ChanDescriptor.ThreadDescriptor.create(
            chanDescriptor = catalogDescriptor,
            threadNo = nativeArchivePost.threadNo
          )
        }
      }
    }

    seenPostsManager.loadForCatalog(catalogDescriptor, threadDescriptors)

    threadDescriptors.forEach { threadDescriptor ->
      if (seenPostsManager.isThreadAlreadySeen(threadDescriptor)) {
        alreadyVisitedThreads[threadDescriptor] = Unit
      }
    }

    val archiveThreads = nativeArchivePostList.posts.mapIndexed { index, nativeArchivePost ->
      when (nativeArchivePost) {
        is NativeArchivePost.Chan4NativeArchivePost -> {
          val threadDescriptor = threadDescriptors[index]

          return@mapIndexed ArchiveThread(
            threadDescriptor = threadDescriptor,
            comment = nativeArchivePost.comment.toString()
          )
        }
        is NativeArchivePost.DvachNativeArchivePost -> {
          val threadDescriptor = threadDescriptors[index]

          return@mapIndexed ArchiveThread(
            threadDescriptor = threadDescriptor,
            comment = nativeArchivePost.comment.toString()
          )
        }
      }
    }

    val noResultsOrBadPage = nativeArchivePostList.nextPage == null
      || nativeArchivePostList.nextPage < 0
      || archiveThreads.isEmpty()

    if (noResultsOrBadPage) {
      _endReached.value = true
    }

    Logger.d(TAG, "loadPageOfArchiveThreads() page=${page.value} done, " +
      "loaded ${archiveThreads.size} threads, endReached=${_endReached.value}")

    _page.value = nativeArchivePostList.nextPage
    _archiveThreads.addAll(archiveThreads)
    _state.value = AsyncData.Data(Unit)
  }

  fun updatePrevLazyListState(firstVisibleItemIndex: Int, firstVisibleItemScrollOffset: Int) {
    _rememberedFirstVisibleItemIndex = firstVisibleItemIndex
    _rememberedFirstVisibleItemScrollOffset = firstVisibleItemScrollOffset
  }

  data class ArchiveThread(
    val threadDescriptor: ChanDescriptor.ThreadDescriptor,
    val comment: String
  ) {
    val threadNo: Long = threadDescriptor.threadNo
  }

  class ArchiveNotSupportedException(boardCode: String) : Exception("Board '/$boardCode/' has no archive")

  class ViewModelFactory @Inject constructor(
    private val siteManager: SiteManager,
    private val seenPostsManager: SeenPostsManager
  ) : ViewModelAssistedFactory<BoardArchiveViewModel> {
    override fun create(handle: SavedStateHandle): BoardArchiveViewModel {
      return BoardArchiveViewModel(
        savedStateHandle = handle,
        siteManager = siteManager,
        seenPostsManager = seenPostsManager
      )
    }
  }

  companion object {
    private const val TAG = "BoardArchiveViewModel"

    const val CatalogDescriptorParam = "catalog_descriptor"
  }
}
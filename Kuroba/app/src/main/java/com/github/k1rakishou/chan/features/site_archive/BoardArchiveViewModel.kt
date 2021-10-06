package com.github.k1rakishou.chan.features.site_archive

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.base.Debouncer
import com.github.k1rakishou.chan.core.compose.AsyncData
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.manager.SeenPostsManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.sites.archive.NativeArchivePost
import com.github.k1rakishou.common.BadStatusResponseException
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

class BoardArchiveViewModel(
  private val catalogDescriptor: ChanDescriptor.CatalogDescriptor
) : BaseViewModel() {

  @Inject
  lateinit var siteManager: SiteManager
  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var seenPostsManager: SeenPostsManager

  private val _state = MutableStateFlow(ViewModelState())
  private val searchDebouncer = Debouncer(false)
  private var _searchQuery = mutableStateOf<String?>(null)
  private var _archiveThreadsAsync: AsyncData<List<ArchiveThread>> = AsyncData.NotInitialized

  var currentlySelectedThreadNo = mutableStateOf<Long?>(null)

  private var _rememberedFirstVisibleItemIndex: Int = 0
  val rememberedFirstVisibleItemIndex: Int
    get() = _rememberedFirstVisibleItemIndex

  private var _rememberedFirstVisibleItemScrollOffset: Int = 0
  val rememberedFirstVisibleItemScrollOffset: Int
    get() = _rememberedFirstVisibleItemScrollOffset

  val alreadyVisitedThreads = mutableStateMapOf<ChanDescriptor.ThreadDescriptor, Unit>()

  val state: StateFlow<ViewModelState>
    get() = _state.asStateFlow()
  val searchQuery: State<String?>
    get() = _searchQuery

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  override suspend fun onViewModelReady() {
    _state.updateState { copy(archiveThreadsAsync = AsyncData.Loading) }

    mainScope.launch {
      seenPostsManager.seenThreadUpdatesFlow.collect { seenThread ->
        alreadyVisitedThreads.put(seenThread, Unit)
      }
    }

    val nativeArchivePostListResult = siteManager.bySiteDescriptor(catalogDescriptor.siteDescriptor())
      ?.actions()
      ?.archive(catalogDescriptor.boardDescriptor)

    if (nativeArchivePostListResult == null) {
      val exception = ArchiveNotSupportedException(catalogDescriptor.boardCode())
      _state.updateState { copy(archiveThreadsAsync = AsyncData.Error(exception)) }
      return
    }

    val nativeArchivePostList = if (nativeArchivePostListResult is ModularResult.Error) {
      val error = nativeArchivePostListResult.error

      if (error is BadStatusResponseException && error.status == 404) {
        val exception = ArchiveNotSupportedException(catalogDescriptor.boardCode())
        _state.updateState { copy(archiveThreadsAsync = AsyncData.Error(exception)) }
        return
      }

      _state.updateState { copy(archiveThreadsAsync = AsyncData.Error(error)) }
      return
    } else {
      nativeArchivePostListResult.valueOrNull()!!
    }

    val threadDescriptors = nativeArchivePostList.map { nativeArchivePost ->
      when (nativeArchivePost) {
        is NativeArchivePost.Chan4NativeArchivePost -> {
          return@map ChanDescriptor.ThreadDescriptor.Companion.create(
            chanDescriptor = catalogDescriptor,
            threadNo = nativeArchivePost.threadNo
          )
        }
      }
    }

    seenPostsManager.loadForCatalog(catalogDescriptor, threadDescriptors)
    alreadyVisitedThreads.clear()

    threadDescriptors.forEach { threadDescriptor ->
      if (seenPostsManager.isThreadAlreadySeen(threadDescriptor)) {
        alreadyVisitedThreads[threadDescriptor] = Unit
      }
    }

    val archiveThreads = nativeArchivePostList.mapIndexed { index, nativeArchivePost ->
      when (nativeArchivePost) {
        is NativeArchivePost.Chan4NativeArchivePost -> {
          val threadDescriptor = threadDescriptors[index]

          return@mapIndexed ArchiveThread(
            threadDescriptor = threadDescriptor,
            comment = nativeArchivePost.comment.toString()
          )
        }
      }
    }

    _archiveThreadsAsync = AsyncData.Data(archiveThreads)
    _state.updateState { copy(archiveThreadsAsync = AsyncData.Data(archiveThreads)) }
  }

  fun updatePrevLazyListState(firstVisibleItemIndex: Int, firstVisibleItemScrollOffset: Int) {
    _rememberedFirstVisibleItemIndex = firstVisibleItemIndex
    _rememberedFirstVisibleItemScrollOffset = firstVisibleItemScrollOffset
  }

  fun updateQueryAndReload(query: String?) {
    _searchQuery.value = query

    searchDebouncer.post({ reloadArchiveThreads(query) }, 125L)
  }

  private fun reloadArchiveThreads(query: String?) {
    if (query.isNullOrEmpty()) {
      if (_archiveThreadsAsync is AsyncData.Data) {
        _state.updateState { copy(archiveThreadsAsync = _archiveThreadsAsync) }
      }

      _searchQuery.value = null
      return
    }

    val archiveThreadsAsync = _archiveThreadsAsync
    if (archiveThreadsAsync !is AsyncData.Data) {
      _searchQuery.value = null
      return
    }

    val filteredArchiveThreads = archiveThreadsAsync.data.filter { archiveThread ->
      val threadNoStr = archiveThread.threadDescriptor.threadNo.toString()
      if (threadNoStr.contains(query, ignoreCase = true)) {
        return@filter true
      }

      if (archiveThread.comment.contains(query, ignoreCase = true)) {
        return@filter true
      }

      return@filter false
    }

    _searchQuery.value = query
    _state.updateState { copy(archiveThreadsAsync = AsyncData.Data(filteredArchiveThreads)) }
  }

  data class ViewModelState(
    val archiveThreadsAsync: AsyncData<List<ArchiveThread>> = AsyncData.NotInitialized
  )

  data class ArchiveThread(
    val threadDescriptor: ChanDescriptor.ThreadDescriptor,
    val comment: String
  ) {
    val threadNo: Long = threadDescriptor.threadNo
  }

  class ArchiveNotSupportedException(boardCode: String) : Exception("Board '/$boardCode/' has no archive")

  companion object {
    private const val TAG = "BoardArchiveViewModel"
  }
}
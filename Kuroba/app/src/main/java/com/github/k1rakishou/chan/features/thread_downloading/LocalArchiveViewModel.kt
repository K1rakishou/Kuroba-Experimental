package com.github.k1rakishou.chan.features.thread_downloading

import androidx.compose.runtime.*
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.BaseSelectionHelper
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.base.MutableCachedSharedFlow
import com.github.k1rakishou.chan.core.compose.AsyncData
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.manager.ThreadDownloadManager
import com.github.k1rakishou.chan.ui.view.bottom_menu_panel.BottomMenuPanelItem
import com.github.k1rakishou.chan.ui.view.bottom_menu_panel.BottomMenuPanelItemId
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.thread.ThreadDownload
import com.github.k1rakishou.model.repository.ChanPostRepository
import com.github.k1rakishou.model.util.ChanPostUtils
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.joda.time.format.DateTimeFormatterBuilder
import org.joda.time.format.ISODateTimeFormat
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

class LocalArchiveViewModel : BaseViewModel() {

  @Inject
  lateinit var threadDownloadManager: ThreadDownloadManager
  @Inject
  lateinit var chanPostRepository: ChanPostRepository

  private val cachedThreadDownloadViews = mutableListWithCap<ThreadDownloadView>(32)

  private val _state = MutableStateFlow(ViewModelState())
  val state: StateFlow<ViewModelState>
    get() = _state.asStateFlow()

  private var _searchQuery = mutableStateOf<String?>(null)
  val searchQuery: State<String?>
    get() = _searchQuery

  private var _selectionMode = MutableCachedSharedFlow<BaseSelectionHelper.SelectionEvent?>(
    extraBufferCapacity = 16,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )
  val selectionMode: SharedFlow<BaseSelectionHelper.SelectionEvent?>
    get() = _selectionMode.sharedFlow

  private val _bottomPanelMenuItemClickEventFlow = MutableSharedFlow<MenuItemClickEvent>(
    extraBufferCapacity = 16,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )
  val bottomPanelMenuItemClickEventFlow: SharedFlow<MenuItemClickEvent>
    get() = _bottomPanelMenuItemClickEventFlow.asSharedFlow()

  private val selectedItems = mutableMapOf<ChanDescriptor.ThreadDescriptor, MutableState<Boolean>>()

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  @OptIn(ExperimentalTime::class)
  override suspend fun onViewModelReady() {
    mainScope.launch {
      threadDownloadManager.threadDownloadUpdateFlow
        .debounce(Duration.seconds(1))
        .collect { reload() }
    }

    mainScope.launch {
      threadDownloadManager.threadsProcessedFlow
        .debounce(Duration.seconds(1))
        .collect { reload() }
    }

    reload()
  }

  @Composable
  fun collectSelectionModeAsState(): State<BaseSelectionHelper.SelectionEvent?> {
    return _selectionMode.collectAsState()
  }

  fun updateQueryAndReload(query: String?) {
    _searchQuery.value = query

    // If state is not data then do nothing
    if (_state.value.threadDownloadsAsync !is AsyncData.Data) {
      return
    }

    if (query == null) {
      _state.updateState { copy(threadDownloadsAsync = AsyncData.Data(cachedThreadDownloadViews.toList())) }
      return
    }

    val filteredThreadDownloadViews = if (query.isEmpty()) {
      cachedThreadDownloadViews.toList()
    } else {
      cachedThreadDownloadViews.filter { threadDownloadView ->
        if (threadDownloadView.threadSubject.contains(query, ignoreCase = true)) {
          return@filter true
        }

        if (threadDownloadView.threadDownloadInfo.contains(query, ignoreCase = true)) {
          return@filter true
        }

        return@filter false
      }.map { threadDownloadView -> threadDownloadView.copy() }
    }

    _state.updateState { copy(threadDownloadsAsync = AsyncData.Data(filteredThreadDownloadViews)) }
  }

  fun deleteDownloads(selectedItems: List<ChanDescriptor.ThreadDescriptor>) {
    mainScope.launch {
      threadDownloadManager.cancelDownloads(selectedItems)
      reload()
    }
  }

  fun stopDownloads(selectedItems: List<ChanDescriptor.ThreadDescriptor>) {
    mainScope.launch {
      selectedItems.forEach { threadDescriptor ->
        threadDownloadManager.updateThreadDownload(
          threadDescriptor = threadDescriptor,
          updaterFunc = { threadDownload ->
            if (threadDownload.status != ThreadDownload.Status.Running) {
              return@updateThreadDownload null
            }

            return@updateThreadDownload threadDownload.copy(status = ThreadDownload.Status.Stopped)
          }
        )
      }

      reload()
    }
  }

  fun startDownloads(selectedItems: List<ChanDescriptor.ThreadDescriptor>) {
    mainScope.launch {
      selectedItems.forEach { threadDescriptor ->
        threadDownloadManager.updateThreadDownload(
          threadDescriptor = threadDescriptor,
          updaterFunc = { threadDownload ->
            if (threadDownload.status != ThreadDownload.Status.Stopped) {
              return@updateThreadDownload null
            }

            return@updateThreadDownload threadDownload.copy(status = ThreadDownload.Status.Running)
          }
        )
      }

      reload()
    }
  }

  fun selectedItemsCount(): Int {
    return selectedItems.count { (_, selectionState) -> selectionState.value }
  }

  fun isInSelectionMode(): Boolean {
    if (selectedItems.isEmpty()) {
      return false
    }

    return selectedItems.any { (_, selectionState) -> selectionState.value }
  }

  fun toggleSelection(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    if (!selectedItems.containsKey(threadDescriptor)) {
      selectedItems.put(threadDescriptor, mutableStateOf(false))
    }

    var selectionState by selectedItems[threadDescriptor]!!
    selectionState = selectionState.not()

    updateSelectionModeFlag()
  }

  fun observeSelectionState(threadDescriptor: ChanDescriptor.ThreadDescriptor): State<Boolean> {
    if (!selectedItems.containsKey(threadDescriptor)) {
      selectedItems.put(threadDescriptor, mutableStateOf(false))
      updateSelectionModeFlag()
    }

    return selectedItems[threadDescriptor]!!
  }

  fun unselectAll(): Boolean {
    if (selectedItems.isEmpty()) {
      return false
    }

    selectedItems.clear()
    updateSelectionModeFlag()
    return true
  }

  fun getBottomPanelMenus(): List<BottomMenuPanelItem> {
    val currentlySelectedItems = getCurrentlySelectedItems()
    if (currentlySelectedItems.isEmpty()) {
      return emptyList()
    }

    val availableActions = checkAvailableActions(currentlySelectedItems)
    val itemsList = mutableListOf<BottomMenuPanelItem>()

    itemsList += BottomMenuPanelItem(
      ArchiveMenuItemId(ArchiveMenuItemType.Delete),
      R.drawable.ic_baseline_delete_outline_24,
      R.string.bottom_menu_item_delete,
      {
        val clickEvent = MenuItemClickEvent(ArchiveMenuItemType.Delete, getCurrentlySelectedItems())
        _bottomPanelMenuItemClickEventFlow.tryEmit(clickEvent)

        unselectAll()
      }
    )

    if (availableActions.canStop) {
      itemsList += BottomMenuPanelItem(
        ArchiveMenuItemId(ArchiveMenuItemType.Stop),
        R.drawable.ic_baseline_stop_24,
        R.string.bottom_menu_item_stop,
        {
          val clickEvent = MenuItemClickEvent(ArchiveMenuItemType.Stop, getCurrentlySelectedItems())
          _bottomPanelMenuItemClickEventFlow.tryEmit(clickEvent)

          unselectAll()
        }
      )
    }

    if (availableActions.canStart) {
      itemsList += BottomMenuPanelItem(
        ArchiveMenuItemId(ArchiveMenuItemType.Start),
        R.drawable.ic_file_download_white_24dp,
        R.string.bottom_menu_item_start,
        {
          val clickEvent = MenuItemClickEvent(ArchiveMenuItemType.Start, getCurrentlySelectedItems())
          _bottomPanelMenuItemClickEventFlow.tryEmit(clickEvent)

          unselectAll()
        }
      )
    }

    return itemsList
  }

  private fun checkAvailableActions(
    currentlySelectedItems: List<ChanDescriptor.ThreadDescriptor>
  ): AvailableActions {
    var canStop = false
    var canStart = false

    loop@ for (currentlySelectedItem in currentlySelectedItems) {
      for (cachedThreadDownloadView in cachedThreadDownloadViews) {
        if (cachedThreadDownloadView.threadDescriptor == currentlySelectedItem) {
          when (cachedThreadDownloadView.status) {
            ThreadDownload.Status.Running -> {
              canStop = true
            }
            ThreadDownload.Status.Stopped -> {
              canStart = true
            }
            ThreadDownload.Status.Completed -> {
              // no-op
            }
          }
        }

        if (canStart && canStop) {
          break@loop
        }
      }
    }

    return AvailableActions(canStop = canStop, canStart = canStart)
  }

  private fun getCurrentlySelectedItems(): List<ChanDescriptor.ThreadDescriptor> {
    return selectedItems
      .filter { (_, selectedItem) -> selectedItem.value }
      .map { (threadDescriptor, _) -> threadDescriptor }
  }

  private fun updateSelectionModeFlag() {
    val wasInSelectionMode = _selectionMode.cachedValue?.isIsSelectionMode() ?: false
    val nowInSelectionMode = isInSelectionMode()

    val newValue = when {
      wasInSelectionMode && nowInSelectionMode -> BaseSelectionHelper.SelectionEvent.ItemSelectionToggled
      nowInSelectionMode -> BaseSelectionHelper.SelectionEvent.EnteredSelectionMode
      else ->  BaseSelectionHelper.SelectionEvent.ExitedSelectionMode
    }

    _selectionMode.tryEmit(newValue)
  }

  private suspend fun reload() {
    threadDownloadManager.awaitUntilInitialized()
    chanPostRepository.awaitUntilInitialized()

    val threadDownloads = threadDownloadManager.getAllThreadDownloads()
      .sortedByDescending { threadDownload -> threadDownload.createdOn }

    val threadDatabaseIds = threadDownloads
      .map { threadDownload -> threadDownload.ownerThreadDatabaseId }

    val originalPostMap = chanPostRepository.getThreadOriginalPostsByDatabaseId(threadDatabaseIds)
      .safeUnwrap { error ->
        Logger.e(TAG, "getThreadOriginalPostsByDatabaseId() error", error)

        _state.updateState {
          cachedThreadDownloadViews.clear()
          copy(threadDownloadsAsync = AsyncData.Error(error))
        }

        return
      }
      .associateBy { chanPost -> chanPost.postDescriptor.threadDescriptor() }

    val threadDownloadViews = threadDownloads.map { threadDownload ->
      val threadDescriptor = threadDownload.threadDescriptor
      val originalPost = originalPostMap[threadDescriptor]
      val threadSubject = ChanPostUtils.getTitle(originalPost, threadDescriptor)

      val threadDownloadInfo = buildString {
        append(threadDescriptor.siteName())
        append("/")
        append(threadDescriptor.boardCode())
        append("/")

        append(", Thread No. ")
        append(threadDescriptor.threadNo)

        appendLine()

        val status = when (threadDownload.status) {
          ThreadDownload.Status.Running -> "Downloading"
          ThreadDownload.Status.Stopped -> "Stopped"
          ThreadDownload.Status.Completed -> "Completed"
        }

        append("Status: ")
        append(status)

        appendLine()

        append("Downloading media: ")
        append(threadDownload.downloadMedia)

        appendLine()

        append("Started: ")
        append(DATE_TIME_PRINTER.print(threadDownload.createdOn))

        if (threadDownload.lastUpdateTime != null) {
          appendLine()
          append("Updated: ")
          append(DATE_TIME_PRINTER.print(threadDownload.lastUpdateTime))
        }
      }

      return@map ThreadDownloadView(
        threadDescriptor = threadDownload.threadDescriptor,
        status = threadDownload.status,
        threadSubject = threadSubject,
        threadDownloadInfo = threadDownloadInfo,
        threadThumbnailUrl = threadDownload.threadThumbnailUrl,
        downloadResultMsg = threadDownload.downloadResultMsg
      )
    }

    cachedThreadDownloadViews.clear()

    threadDownloadViews.forEach { threadDownloadView ->
      cachedThreadDownloadViews += threadDownloadView.copy()
    }

    if (searchQuery.value != null) {
      // Do not update the main state when in search mode because it will reset the filtered entries
      return
    }

    _state.updateState {
      copy(threadDownloadsAsync = AsyncData.Data(threadDownloadViews))
    }
  }

  enum class ArchiveMenuItemType(val id: Int) {
    Delete(0),
    Stop(1),
    Start(2)
  }

  class ArchiveMenuItemId(val archiveMenuItemType: ArchiveMenuItemType) :
    BottomMenuPanelItemId {
    override fun id(): Int {
      return archiveMenuItemType.id
    }
  }

  class AvailableActions(
    var canStop: Boolean = false,
    var canStart: Boolean = false
  )

  data class MenuItemClickEvent(
    val archiveMenuItemType: ArchiveMenuItemType,
    val items: List<ChanDescriptor.ThreadDescriptor>
  )

  data class ViewModelState(
    val threadDownloadsAsync: AsyncData<List<ThreadDownloadView>> = AsyncData.NotInitialized
  )

  data class ThreadDownloadView(
    val threadDescriptor: ChanDescriptor.ThreadDescriptor,
    val status: ThreadDownload.Status = ThreadDownload.Status.Running,
    val threadSubject: String,
    val threadDownloadInfo: String,
    val threadThumbnailUrl: String?,
    val downloadResultMsg: String?
  )

  companion object {
    private const val TAG = "LocalArchiveViewModel"

    private val DATE_TIME_PRINTER = DateTimeFormatterBuilder()
      .append(ISODateTimeFormat.date())
      .appendLiteral(' ')
      .append(ISODateTimeFormat.hourMinuteSecond())
      .toFormatter()
      .withZoneUTC()
  }

}
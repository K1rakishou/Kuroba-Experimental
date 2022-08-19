package com.github.k1rakishou.chan.features.thread_downloading

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.base.DebouncingCoroutineExecutor
import com.github.k1rakishou.chan.core.base.ViewModelSelectionHelper
import com.github.k1rakishou.chan.core.compose.AsyncData
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.manager.ThreadDownloadManager
import com.github.k1rakishou.chan.core.usecase.ExportDownloadedThreadAsHtmlUseCase
import com.github.k1rakishou.chan.core.usecase.ExportDownloadedThreadMediaUseCase
import com.github.k1rakishou.chan.ui.view.bottom_menu_panel.BottomMenuPanelItem
import com.github.k1rakishou.chan.ui.view.bottom_menu_panel.BottomMenuPanelItemId
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.extractFileName
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.thread.ThreadDownload
import com.github.k1rakishou.model.repository.ChanPostImageRepository
import com.github.k1rakishou.model.repository.ChanPostRepository
import com.github.k1rakishou.model.util.ChanPostUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormatterBuilder
import org.joda.time.format.ISODateTimeFormat
import java.io.File
import java.util.*
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

class LocalArchiveViewModel : BaseViewModel() {

  @Inject
  lateinit var appConstants: AppConstants
  @Inject
  lateinit var threadDownloadManager: ThreadDownloadManager
  @Inject
  lateinit var chanPostRepository: ChanPostRepository
  @Inject
  lateinit var chanPostImageRepository: ChanPostImageRepository
  @Inject
  lateinit var threadDownloadProgressNotifier: ThreadDownloadProgressNotifier
  @Inject
  lateinit var exportDownloadedThreadAsHtmlUseCase: ExportDownloadedThreadAsHtmlUseCase
  @Inject
  lateinit var exportDownloadedThreadMediaUseCase: ExportDownloadedThreadMediaUseCase

  private val recalculateAdditionalInfoExecutor = DebouncingCoroutineExecutor(mainScope)
  private val cachedThreadDownloadViews = mutableListWithCap<ThreadDownloadView>(32)
  val viewModelSelectionHelper = ViewModelSelectionHelper<ChanDescriptor.ThreadDescriptor, MenuItemClickEvent>()

  private val _state = MutableStateFlow(ViewModelState())
  val state: StateFlow<ViewModelState>
    get() = _state.asStateFlow()

  private var _searchQuery = mutableStateOf<String?>(null)
  val searchQuery: State<String?>
    get() = _searchQuery

  var viewMode = mutableStateOf<ViewMode>(ViewMode.ShowAll)

  private val _controllerTitleInfoUpdatesFlow = MutableStateFlow<ControllerTitleInfo?>(null)
  val controllerTitleInfoUpdatesFlow: StateFlow<ControllerTitleInfo?>
    get() = _controllerTitleInfoUpdatesFlow.asStateFlow()

  private val additionalThreadDownloadStats = mutableMapOf<ChanDescriptor.ThreadDescriptor, MutableState<AdditionalThreadDownloadStats?>>()

  private var _rememberedFirstVisibleItemIndex: Int = 0
  val rememberedFirstVisibleItemIndex: Int
    get() = _rememberedFirstVisibleItemIndex

  private var _rememberedFirstVisibleItemScrollOffset: Int = 0
  val rememberedFirstVisibleItemScrollOffset: Int
    get() = _rememberedFirstVisibleItemScrollOffset

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  @OptIn(ExperimentalTime::class)
  override suspend fun onViewModelReady() {
    mainScope.launch {
      threadDownloadManager.threadDownloadUpdateFlow
        .debounce(Duration.seconds(1))
        .collect { refreshCacheAndReload() }
    }

    mainScope.launch {
      threadDownloadManager.threadsProcessedFlow
        .debounce(Duration.seconds(1))
        .collect { refreshCacheAndReload() }
    }

    refreshCacheAndReload()
  }

  fun updatePrevLazyListState(firstVisibleItemIndex: Int, firstVisibleItemScrollOffset: Int) {
    _rememberedFirstVisibleItemIndex = firstVisibleItemIndex
    _rememberedFirstVisibleItemScrollOffset = firstVisibleItemScrollOffset
  }

  fun collectDownloadProgressEventsAsState(
    threadDescriptor: ChanDescriptor.ThreadDescriptor
  ): Flow<ThreadDownloadProgressNotifier.Event> {
    return threadDownloadProgressNotifier.listenForProgress(threadDescriptor)
      .sample(16L)
  }

  @Composable
  fun collectAdditionalThreadDownloadStats(
    threadDescriptor: ChanDescriptor.ThreadDescriptor
  ): State<AdditionalThreadDownloadStats?> {
    return additionalThreadDownloadStats.getOrPut(
      threadDescriptor,
      defaultValue = { mutableStateOf(null) }
    )
  }

  fun updateQueryAndReload(query: String?) {
    _searchQuery.value = query

    // If state is not data then do nothing
    if (_state.value.threadDownloadsAsync !is AsyncData.Data) {
      return
    }

    reload()
  }

  fun reload() {
    val threadDownloadViews = filterThreadDownloads()

    _state.updateState {
      copy(threadDownloadsAsync = AsyncData.Data(threadDownloadViews))
    }

    if (additionalThreadDownloadStats.isEmpty()) {
      mainScope.launch {
        recalculateAdditionalInfo(threadDownloadViews)
      }
    } else {
      recalculateAdditionalInfoExecutor.post(1000L) {
        recalculateAdditionalInfo(threadDownloadViews)
      }
    }
  }

  private fun filterThreadDownloads(): List<ThreadDownloadView> {
   val query = _searchQuery.value

    val filteredThreadDownloadViews = cachedThreadDownloadViews
      .filter { threadDownloadView ->
        return@filter when (viewMode.value) {
          ViewMode.ShowCompleted -> threadDownloadView.status == ThreadDownload.Status.Completed
          ViewMode.ShowDownloading -> threadDownloadView.status != ThreadDownload.Status.Completed
          else -> true
        }
      }

    if (query.isNullOrEmpty()) {
      return filteredThreadDownloadViews.toList()
    }

    return filteredThreadDownloadViews
      .filter { threadDownloadView ->
        if (threadDownloadView.threadSubject.contains(query, ignoreCase = true)) {
          return@filter true
        }

        if (threadDownloadView.threadDownloadInfo.contains(query, ignoreCase = true)) {
          return@filter true
        }

        return@filter false
      }
      .map { threadDownloadView -> threadDownloadView.copy() }
  }

  fun deleteDownloads(selectedItems: List<ChanDescriptor.ThreadDescriptor>) {
    mainScope.launch {
      threadDownloadManager.cancelDownloads(selectedItems)
      refreshCacheAndReload()
    }
  }

  fun stopDownloads(selectedItems: List<ChanDescriptor.ThreadDescriptor>) {
    mainScope.launch {
      selectedItems.forEach { threadDescriptor ->
        threadDownloadManager.stopDownloading(threadDescriptor)
      }

      refreshCacheAndReload()
    }
  }

  fun startDownloads(selectedItems: List<ChanDescriptor.ThreadDescriptor>) {
    mainScope.launch {
      selectedItems.forEach { threadDescriptor ->
        threadDownloadManager.resumeDownloading(threadDescriptor)
      }

      refreshCacheAndReload()
    }
  }

  suspend fun hasNotCompletedDownloads(): Boolean {
    return threadDownloadManager.notCompletedThreadsCount() > 0
  }

  fun getBottomPanelMenus(): List<BottomMenuPanelItem> {
    val currentlySelectedItems = viewModelSelectionHelper.getCurrentlySelectedItems()
    if (currentlySelectedItems.isEmpty()) {
      return emptyList()
    }

    val availableActions = checkAvailableActions(currentlySelectedItems)
    val itemsList = mutableListOf<BottomMenuPanelItem>()

    itemsList += BottomMenuPanelItem(
      ArchiveMenuItemId(MenuItemType.Delete),
      R.drawable.ic_baseline_delete_outline_24,
      R.string.bottom_menu_item_delete,
      {
        val clickEvent = MenuItemClickEvent(
          menuItemType = MenuItemType.Delete,
          items = viewModelSelectionHelper.getCurrentlySelectedItems()
        )

        viewModelSelectionHelper.emitBottomPanelMenuItemClickEvent(clickEvent)
        viewModelSelectionHelper.unselectAll()
      }
    )

    if (availableActions.canStop) {
      itemsList += BottomMenuPanelItem(
        ArchiveMenuItemId(MenuItemType.Stop),
        R.drawable.ic_baseline_stop_24,
        R.string.bottom_menu_item_stop,
        {
          val clickEvent = MenuItemClickEvent(
            menuItemType = MenuItemType.Stop,
            items = viewModelSelectionHelper.getCurrentlySelectedItems()
          )

          viewModelSelectionHelper.emitBottomPanelMenuItemClickEvent(clickEvent)
          viewModelSelectionHelper.unselectAll()
        }
      )
    }

    if (availableActions.canStart) {
      itemsList += BottomMenuPanelItem(
        ArchiveMenuItemId(MenuItemType.Start),
        R.drawable.ic_file_download_white_24dp,
        R.string.bottom_menu_item_start,
        {
          val clickEvent = MenuItemClickEvent(
            menuItemType = MenuItemType.Start,
            items = viewModelSelectionHelper.getCurrentlySelectedItems()
          )

          viewModelSelectionHelper.emitBottomPanelMenuItemClickEvent(clickEvent)
          viewModelSelectionHelper.unselectAll()
        }
      )
    }

    if (currentlySelectedItems.size == 1) {
      itemsList += BottomMenuPanelItem(
        ArchiveMenuItemId(MenuItemType.Export),
        R.drawable.ic_baseline_share_24,
        R.string.bottom_menu_item_export,
        {
          val clickEvent = MenuItemClickEvent(
            menuItemType = MenuItemType.Export,
            items = viewModelSelectionHelper.getCurrentlySelectedItems()
          )

          viewModelSelectionHelper.emitBottomPanelMenuItemClickEvent(clickEvent)
          viewModelSelectionHelper.unselectAll()
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

  private suspend fun refreshCacheAndReload() {
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
      val threadDownloadInfo = formatThreadDownloadInfo(threadDescriptor, threadDownload)

      val directoryName = ThreadDownloadingDelegate.formatDirectoryName(threadDescriptor)
      val thumbnailUrl = threadDownload.threadThumbnailUrl?.toHttpUrlOrNull()
      val thumbnailOnDiskName = thumbnailUrl?.extractFileName()

      val thumbnailOnDiskFile = thumbnailOnDiskName?.let { fileName ->
        File(File(appConstants.threadDownloaderCacheDir, directoryName), fileName)
      }

      val thumbnailLocation = if (thumbnailOnDiskFile != null && thumbnailOnDiskFile.exists()) {
        ThreadDownloadThumbnailLocation.Local(thumbnailOnDiskFile)
      } else if (thumbnailUrl != null) {
        ThreadDownloadThumbnailLocation.Remote(thumbnailUrl)
      } else {
        null
      }

      return@map ThreadDownloadView(
        ownerThreadDatabaseId = threadDownload.ownerThreadDatabaseId,
        threadDescriptor = threadDownload.threadDescriptor,
        status = threadDownload.status,
        threadSubject = threadSubject,
        threadDownloadInfo = threadDownloadInfo,
        thumbnailLocation = thumbnailLocation,
        downloadResultMsg = threadDownload.downloadResultMsg
      )
    }

    cachedThreadDownloadViews.clear()

    threadDownloadViews.forEach { threadDownloadView ->
      cachedThreadDownloadViews += threadDownloadView.copy()
    }

    val active = threadDownloadViews.count { threadDownloadView -> threadDownloadView.status.isRunning() }
    val total = threadDownloadViews.size

    _controllerTitleInfoUpdatesFlow.tryEmit(ControllerTitleInfo(active, total))

    if (searchQuery.value != null) {
      // Do not update the main state when in search mode because it will reset the filtered entries
      return
    }

    reload()
  }

  private suspend fun recalculateAdditionalInfo(threadDownloadViews: List<ThreadDownloadView>) {
    threadDownloadViews.forEach { threadDownloadView ->
      val threadDescriptor = threadDownloadView.threadDescriptor

      val stats = withContext(Dispatchers.Default) {
        val directoryName = ThreadDownloadingDelegate.formatDirectoryName(threadDescriptor)
        val directory = File(appConstants.threadDownloaderCacheDir, directoryName)
        val ownerThreadDatabaseId = threadDownloadView.ownerThreadDatabaseId

        val files = directory.listFiles()
        val filesTotalSize = files?.sumOf { file -> file.length() } ?: 0L
        val mediaCount = files?.size?.div(2) ?: 0

        val postsCount = chanPostRepository.countThreadPosts(ownerThreadDatabaseId)
          .valueOrNull() ?: 0

        return@withContext AdditionalThreadDownloadStats(
          postsCount,
          mediaCount,
          filesTotalSize
        )
      }

      val state = additionalThreadDownloadStats.getOrPut(
        key = threadDescriptor,
        defaultValue = { mutableStateOf(stats) }
      )

      state.value = stats
    }
  }

  private fun formatThreadDownloadInfo(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    threadDownload: ThreadDownload
  ): String {
    return buildString {
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
  }

  suspend fun exportThreadAsHtml(
    outputFileUri: Uri,
    threadDescriptor: ChanDescriptor.ThreadDescriptor
  ): ModularResult<Unit> {
    val params = ExportDownloadedThreadAsHtmlUseCase.Params(outputFileUri, threadDescriptor)
    return exportDownloadedThreadAsHtmlUseCase.execute(params)
      .peekError { error -> Logger.e(TAG, "exportDownloadedThreadAsHtmlUseCase() error", error) }
  }

  suspend fun exportThreadMedia(
    outputDirectoryUri: Uri,
    directoryName: String,
    threadDescriptor: ChanDescriptor.ThreadDescriptor
  ): ModularResult<Unit> {
    val params = ExportDownloadedThreadMediaUseCase.Params(outputDirectoryUri, directoryName, threadDescriptor)
    return exportDownloadedThreadMediaUseCase.execute(params)
      .peekError { error -> Logger.e(TAG, "exportDownloadedThreadMediaUseCase() error", error) }
  }

  enum class ViewMode {
    ShowAll,
    ShowDownloading,
    ShowCompleted
  }

  class ArchiveMenuItemId(val menuItemType: MenuItemType) :
    BottomMenuPanelItemId {
    override fun id(): Int {
      return menuItemType.id
    }
  }

  class AvailableActions(
    var canStop: Boolean = false,
    var canStart: Boolean = false
  )

  data class ControllerTitleInfo(
    val activeDownloads: Int,
    val totalDownloads: Int
  )

  data class MenuItemClickEvent(
    val menuItemType: MenuItemType,
    val items: List<ChanDescriptor.ThreadDescriptor>
  )

  enum class MenuItemType(val id: Int) {
    Delete(0),
    Stop(1),
    Start(2),
    Export(3)
  }

  data class ViewModelState(
    val threadDownloadsAsync: AsyncData<List<ThreadDownloadView>> = AsyncData.NotInitialized
  )

  data class ThreadDownloadView(
    val ownerThreadDatabaseId: Long,
    val threadDescriptor: ChanDescriptor.ThreadDescriptor,
    val status: ThreadDownload.Status = ThreadDownload.Status.Running,
    val threadSubject: String,
    val threadDownloadInfo: String,
    val thumbnailLocation: ThreadDownloadThumbnailLocation?,
    val downloadResultMsg: String?
  )

  data class AdditionalThreadDownloadStats(
    val downloadedPostsCount: Int,
    val downloadedMediaCount: Int,
    val mediaTotalDiskSize: Long
  )

  sealed class ThreadDownloadThumbnailLocation {
    data class Remote(val url: HttpUrl) : ThreadDownloadThumbnailLocation()
    data class Local(val file: File) : ThreadDownloadThumbnailLocation()
  }

  companion object {
    private const val TAG = "LocalArchiveViewModel"

    private val DATE_TIME_PRINTER = DateTimeFormatterBuilder()
      .append(ISODateTimeFormat.date())
      .appendLiteral(' ')
      .append(ISODateTimeFormat.hourMinuteSecond())
      .toFormatter()
      .withZone(DateTimeZone.forTimeZone(TimeZone.getDefault()))
  }

}
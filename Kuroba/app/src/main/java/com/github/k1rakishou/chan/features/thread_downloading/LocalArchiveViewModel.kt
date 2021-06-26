package com.github.k1rakishou.chan.features.thread_downloading

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.compose.AsyncData
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.manager.ThreadDownloadManager
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.thread.ThreadDownload
import com.github.k1rakishou.model.repository.ChanPostRepository
import com.github.k1rakishou.model.util.ChanPostUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

  private val _state = MutableStateFlow(State())
  val state: StateFlow<State>
    get() = _state.asStateFlow()

  private var _searchQuery by mutableStateOf<String?>(null)
  val searchQuery: String?
    get() = _searchQuery

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

  fun updateQueryAndReload(query: String?) {
    _searchQuery = query

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

    if (searchQuery != null) {
      // Do not update the main state when in search mode because it will reset the filtered entries
      return
    }

    _state.updateState {
      copy(threadDownloadsAsync = AsyncData.Data(threadDownloadViews))
    }
  }

  data class State(
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
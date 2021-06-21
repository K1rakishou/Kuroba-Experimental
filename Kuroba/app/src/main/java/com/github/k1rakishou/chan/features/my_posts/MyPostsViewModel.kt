package com.github.k1rakishou.chan.features.my_posts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.base.DebouncingCoroutineExecutor
import com.github.k1rakishou.chan.core.compose.AsyncData
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.manager.SavedReplyManager
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanSavedReply
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.joda.time.format.DateTimeFormatterBuilder
import org.joda.time.format.ISODateTimeFormat
import javax.inject.Inject

class MyPostsViewModel : BaseViewModel() {
  @Inject
  lateinit var savedReplyManager: SavedReplyManager

  private val _myPostsViewModelState = MutableStateFlow(MyPostsViewModelState())
  private val searchQueryDebouncer = DebouncingCoroutineExecutor(mainScope)

  private var _searchQuery by mutableStateOf<String?>(null)
  val searchQuery: String?
    get() = _searchQuery

  val myPostsViewModelState: StateFlow<MyPostsViewModelState>
    get() = _myPostsViewModelState.asStateFlow()

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  override suspend fun onViewModelReady() {
    mainScope.launch {
      savedReplyManager.savedRepliesUpdateFlow
        .debounce(1_000L)
        .collect { reloadSavedReplies() }
    }

    _myPostsViewModelState.updateState { copy(savedRepliesGroupedAsync = AsyncData.Loading) }

    val result = savedReplyManager.loadAll()
    if (result.isError()) {
      _myPostsViewModelState.updateState { copy(savedRepliesGroupedAsync = AsyncData.Error(result.unwrapError())) }
      return
    }

    reloadSavedReplies()
  }

  fun updateQueryAndReload(newQuery: String?) {
    this._searchQuery = newQuery

    searchQueryDebouncer.post(150L, { reloadSavedReplies() })
  }

  private suspend fun reloadSavedReplies() {
    withContext(Dispatchers.Default) {
      _myPostsViewModelState.updateState { copy(savedRepliesGroupedAsync = AsyncData.Loading) }

      val allSavedReplies = savedReplyManager.getAll()
      if (allSavedReplies.isEmpty()) {
        _myPostsViewModelState.updateState { copy(savedRepliesGroupedAsync = AsyncData.Data(emptyList())) }
        return@withContext
      }

      val groupedSavedReplies = allSavedReplies.entries
        .filter { mapEntry -> mapEntry.value.isNotEmpty() }
        .sortedByDescending { (_, savedReplies) ->
          return@sortedByDescending savedReplies.maxByOrNull { it.createdOn.millis }
            ?.createdOn?.millis ?: 0L
        }
        .mapNotNull { (threadDescriptor, savedReplies) ->
          val firstSavedReply = savedReplies.firstOrNull()
            ?: return@mapNotNull null

          val headerThreadInfo = buildString {
            append("Thread No. ")
            append(threadDescriptor.threadNo)
          }

          val headerThreadSubject = firstSavedReply.subject

          val savedReplyDataList = processSavedReplies(savedReplies)
          if (savedReplyDataList.isEmpty()) {
            return@mapNotNull null
          }

          return@mapNotNull GroupedSavedReplies(
            threadDescriptor = threadDescriptor,
            headerThreadInfo = headerThreadInfo,
            headerThreadSubject = headerThreadSubject,
            savedReplyDataList = savedReplyDataList
          )
        }

      _myPostsViewModelState.updateState {
        copy(savedRepliesGroupedAsync = AsyncData.Data(groupedSavedReplies))
      }
    }
  }

  private fun processSavedReplies(savedReplies: List<ChanSavedReply>): List<SavedReplyData> {
    return savedReplies
      .sortedBy { chanSavedReply -> chanSavedReply.postDescriptor.postNo }
      .mapNotNull { savedReply ->
        val dateTime = if (savedReply.createdOn.millis <= 0) {
          null
        } else {
          DATE_TIME_PRINTER.print(savedReply.createdOn)
        }

        val postHeader = buildString {
          append("Post No. ")
          append(savedReply.postDescriptor.postNo)

          if (dateTime != null) {
            append(" ")
            append(dateTime)
          }
        }

        val comment = savedReply.comment ?: "<Empty comment>"

        if (_searchQuery.isNotNullNorEmpty()) {
          var matches = false

          if (!matches && postHeader.contains(_searchQuery!!, ignoreCase = true)) {
            matches = true
          }

          if (!matches && comment.contains(_searchQuery!!, ignoreCase = true)) {
            matches = true
          }

          if (!matches) {
            return@mapNotNull null
          }
        }

        return@mapNotNull SavedReplyData(
          postDescriptor = savedReply.postDescriptor,
          postHeader = postHeader,
          comment = comment,
          dateTime = dateTime
        )
      }
  }

  data class MyPostsViewModelState(
    val savedRepliesGroupedAsync: AsyncData<List<GroupedSavedReplies>> = AsyncData.NotInitialized
  )

  data class GroupedSavedReplies(
    val threadDescriptor: ChanDescriptor.ThreadDescriptor,
    val headerThreadInfo: String,
    val headerThreadSubject: String?,
    val savedReplyDataList: List<SavedReplyData>
  )

  data class SavedReplyData(
    val postDescriptor: PostDescriptor,
    val postHeader: String,
    val comment: String,
    val dateTime: String?
  )

  companion object {
    private val DATE_TIME_PRINTER = DateTimeFormatterBuilder()
      .append(ISODateTimeFormat.date())
      .appendLiteral(' ')
      .append(ISODateTimeFormat.hourMinuteSecond())
      .toFormatter()
      .withZoneUTC()
  }

}
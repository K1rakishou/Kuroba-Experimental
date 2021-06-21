package com.github.k1rakishou.chan.features.my_posts

import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.compose.AsyncData
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.manager.SavedReplyManager
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
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

  suspend fun reloadSavedReplies() {
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

          val headerTitle = buildString {
            append("Thread No. ")
            append(threadDescriptor.threadNo)

            if (firstSavedReply.subject.isNotNullNorBlank()) {
              appendLine()
              append(firstSavedReply.subject)
            }
          }

          val savedReplyDataList = savedReplies
            .sortedBy { chanSavedReply -> chanSavedReply.postDescriptor.postNo }
            .map { savedReply ->
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

              return@map SavedReplyData(
                postDescriptor = savedReply.postDescriptor,
                postHeader = postHeader,
                comment = savedReply.comment ?: "<Empty comment>",
                dateTime = dateTime
              )
            }

          return@mapNotNull GroupedSavedReplies(
            threadDescriptor = threadDescriptor,
            headerTitle = headerTitle,
            savedReplyDataList = savedReplyDataList
          )
        }

      _myPostsViewModelState.updateState { copy(savedRepliesGroupedAsync = AsyncData.Data(groupedSavedReplies)) }
    }
  }

  data class MyPostsViewModelState(
    val savedRepliesGroupedAsync: AsyncData<List<GroupedSavedReplies>> = AsyncData.NotInitialized
  )

  data class GroupedSavedReplies(
    val threadDescriptor: ChanDescriptor.ThreadDescriptor,
    val headerTitle: String,
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
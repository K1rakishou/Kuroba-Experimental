package com.github.k1rakishou.chan.features.bookmarks

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.ThreadBookmarkGroupManager
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.move
import com.github.k1rakishou.common.removeIfKt
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import kotlinx.coroutines.launch
import javax.inject.Inject

class BookmarkGroupSettingsControllerViewModel : BaseViewModel() {

  @Inject
  lateinit var threadBookmarkGroupManager: ThreadBookmarkGroupManager
  @Inject
  lateinit var bookmarksManager: BookmarksManager

  private var _loading = mutableStateOf(true)
  val loading: State<Boolean>
    get() = _loading

  val threadBookmarkGroupItems = mutableStateListOf<ThreadBookmarkGroupItem>()

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  override suspend fun onViewModelReady() {
  }

  fun reload() {
    mainScope.launch {
      val newThreadBookmarkGroupItems = mutableListOf<ThreadBookmarkGroupItem>()

      threadBookmarkGroupManager.viewBookmarkGroups { threadBookmarkGroup ->
        newThreadBookmarkGroupItems += ThreadBookmarkGroupItem(
          groupId = threadBookmarkGroup.groupId,
          groupName = threadBookmarkGroup.groupName,
          groupOrder = threadBookmarkGroup.groupOrder,
          groupEntriesCount = threadBookmarkGroup.getEntriesCount()
        )
      }

      threadBookmarkGroupItems.clear()

      if (newThreadBookmarkGroupItems.isNotEmpty()) {
        val sortedThreadBookmarkGroupItems = newThreadBookmarkGroupItems
          .sortedBy { threadBookmarkGroupItem -> threadBookmarkGroupItem.groupOrder }

        threadBookmarkGroupItems.addAll(sortedThreadBookmarkGroupItems)
      }

      _loading.value = false
    }
  }

  suspend fun moveBookmarkGroup(fromIndex: Int, toIndex: Int, fromGroupId: String, toGroupId: String) {
    if (threadBookmarkGroupManager.onBookmarkGroupMoving(fromGroupId, toGroupId)) {
      threadBookmarkGroupItems.move(fromIdx = fromIndex, toIdx = toIndex)
    }
  }

  suspend fun onMoveBookmarkGroupComplete() {
    threadBookmarkGroupManager.updateGroupOrders()
  }

  suspend fun removeBookmarkGroup(groupId: String): ModularResult<Unit> {
    val prevBookmarkDescriptorsInGroup = threadBookmarkGroupManager.getBookmarkDescriptorsInGroup(groupId)
    val removeResult = threadBookmarkGroupManager.removeBookmarkGroup(groupId)

    if (removeResult.valueOrNull() == true) {
      if (prevBookmarkDescriptorsInGroup.isNotEmpty()) {
        threadBookmarkGroupManager.createGroupEntries(prevBookmarkDescriptorsInGroup)
      }

      threadBookmarkGroupItems
        .removeIfKt { threadBookmarkGroupItem -> threadBookmarkGroupItem.groupId == groupId }
    }

    return removeResult.mapValue { Unit }
  }

  suspend fun moveBookmarksIntoGroup(
    groupId: String,
    bookmarksToMove: List<ChanDescriptor.ThreadDescriptor>
  ): ModularResult<Boolean> {
    return threadBookmarkGroupManager.moveBookmarksFromGroupToGroup(bookmarksToMove, groupId)
  }

  suspend fun createBookmarkGroup(groupName: String): ModularResult<Unit> {
    return threadBookmarkGroupManager.createBookmarkGroup(groupName)
  }

  suspend fun groupAlreadyExists(groupName: String): ModularResult<Boolean> {
    return threadBookmarkGroupManager.groupAlreadyExists(groupName)
  }

  data class ThreadBookmarkGroupItem(
    val groupId: String,
    val groupName: String,
    val groupOrder: Int,
    val groupEntriesCount: Int
  ) {
    fun isDefaultGroup(): Boolean = ThreadBookmarkGroupManager.isDefaultGroup(groupId)
  }
}
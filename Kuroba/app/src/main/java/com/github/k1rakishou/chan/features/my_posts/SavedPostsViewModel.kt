package com.github.k1rakishou.chan.features.my_posts

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.base.DebouncingCoroutineExecutor
import com.github.k1rakishou.chan.core.base.ViewModelSelectionHelper
import com.github.k1rakishou.chan.core.compose.AsyncData
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.manager.SavedReplyManager
import com.github.k1rakishou.chan.ui.view.bottom_menu_panel.BottomMenuPanelItem
import com.github.k1rakishou.chan.ui.view.bottom_menu_panel.BottomMenuPanelItemId
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
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormatterBuilder
import org.joda.time.format.ISODateTimeFormat
import java.util.*
import javax.inject.Inject

class SavedPostsViewModel : BaseViewModel() {
  @Inject
  lateinit var savedReplyManager: SavedReplyManager

  private val _myPostsViewModelState = MutableStateFlow(MyPostsViewModelState())
  private val searchQueryDebouncer = DebouncingCoroutineExecutor(mainScope)
  val viewModelSelectionHelper = ViewModelSelectionHelper<PostDescriptor, MenuItemClickEvent>()

  private var _searchQuery by mutableStateOf<String?>(null)
  val searchQuery: String?
    get() = _searchQuery

  val myPostsViewModelState: StateFlow<MyPostsViewModelState>
    get() = _myPostsViewModelState.asStateFlow()

  private var rememberedFirstVisibleItemIndex: Int = 0
  private var rememberedFirstVisibleItemScrollOffset: Int = 0

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  @Composable
  fun lazyListState(): LazyListState {
    val lazyListState = rememberLazyListState(
      initialFirstVisibleItemIndex = rememberedFirstVisibleItemIndex,
      initialFirstVisibleItemScrollOffset = rememberedFirstVisibleItemScrollOffset
    )

    rememberedFirstVisibleItemIndex = lazyListState.firstVisibleItemIndex
    rememberedFirstVisibleItemScrollOffset = lazyListState.firstVisibleItemScrollOffset

    return lazyListState
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

  fun deleteSavedPosts(postDescriptors: List<PostDescriptor>) {
    mainScope.launch {
      savedReplyManager.unsavePosts(postDescriptors)
    }
  }

  fun toggleGroupSelection(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    val savedRepliesGrouped = (myPostsViewModelState.value.savedRepliesGroupedAsync as? AsyncData.Data)?.data
    if (savedRepliesGrouped == null) {
      return
    }

    val toggledSavedRepliesGroup = savedRepliesGrouped
      .firstOrNull { savedRepliesGroup -> savedRepliesGroup.threadDescriptor == threadDescriptor }

    if (toggledSavedRepliesGroup == null) {
      return
    }

    val groupPostDescriptors = toggledSavedRepliesGroup.savedReplyDataList.map { it.postDescriptor }
    val allSelected = groupPostDescriptors
      .all { postDescriptor -> viewModelSelectionHelper.isSelected(postDescriptor) }

    groupPostDescriptors.forEach { postDescriptor ->
      if (allSelected) {
        viewModelSelectionHelper.unselect(postDescriptor)
      } else {
        viewModelSelectionHelper.select(postDescriptor)
      }
    }
  }

  fun toggleSelection(postDescriptor: PostDescriptor) {
    viewModelSelectionHelper.toggleSelection(postDescriptor)
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
            append(threadDescriptor.siteName())
            append("/")
            append(threadDescriptor.boardCode())
            append("/")
            append(", Thread No. ")
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

  fun getBottomPanelMenus(): List<BottomMenuPanelItem> {
    val currentlySelectedItems = viewModelSelectionHelper.getCurrentlySelectedItems()
    if (currentlySelectedItems.isEmpty()) {
      return emptyList()
    }

    val itemsList = mutableListOf<BottomMenuPanelItem>()

    itemsList += BottomMenuPanelItem(
      PostMenuItemId(MenuItemType.Delete),
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

    return itemsList
  }

  fun deleteAllSavedPosts() {
    mainScope.launch { savedReplyManager.deleteAll() }
  }

  class PostMenuItemId(val menuItemType: MenuItemType) :
    BottomMenuPanelItemId {
    override fun id(): Int {
      return menuItemType.id
    }
  }

  data class MenuItemClickEvent(
    val menuItemType: MenuItemType,
    val items: List<PostDescriptor>
  )

  enum class MenuItemType(val id: Int) {
    Delete(0)
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
      .withZone(DateTimeZone.forTimeZone(TimeZone.getDefault()))
  }

}
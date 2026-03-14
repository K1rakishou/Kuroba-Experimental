package com.github.k1rakishou.chan.features.posts

import android.content.Context
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.BaseSelectionHelper
import com.github.k1rakishou.chan.core.compose.AsyncUiData
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.StartActivityStartupHandlerHelper
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.features.toolbar.BackArrowMenuItem
import com.github.k1rakishou.chan.features.toolbar.CloseMenuItem
import com.github.k1rakishou.chan.features.toolbar.ToolbarMiddleContent
import com.github.k1rakishou.chan.features.toolbar.ToolbarText
import com.github.k1rakishou.chan.ui.compose.SelectableItem
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeErrorMessage
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeMessage
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeProgressIndicator
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.chan.ui.compose.lazylist.LazyColumnWithFastScroller
import com.github.k1rakishou.chan.ui.compose.providers.ComposeEntrypoint
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.providers.LocalContentPaddings
import com.github.k1rakishou.chan.ui.compose.textAsFlow
import com.github.k1rakishou.chan.ui.controller.base.Controller
import com.github.k1rakishou.chan.ui.controller.base.DeprecatedNavigationFlags
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.viewModelByKey
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

class SavedPostsController(
  context: Context,
  private val startActivityCallback: StartActivityStartupHandlerHelper.StartActivityCallbacks
) : Controller(context) {

  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  private val viewModel by viewModelByKey<SavedPostsViewModel>()

  override fun injectActivityDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onBack(): Boolean {
    if (viewModel.viewModelSelectionHelper.unselectAll()) {
      return true
    }

    return super.onBack()
  }

  override fun onCreate() {
    super.onCreate()

    updateNavigationFlags(
      newNavigationFlags = DeprecatedNavigationFlags(
        hasDrawer = true,
        hasBack = false
      )
    )

    toolbarState.enterDefaultMode(
      leftItem = BackArrowMenuItem(
        onClick = { requireNavController().popController() }
      ),
      middleContent = ToolbarMiddleContent.Title(
        title = ToolbarText.Id(R.string.controller_saved_posts)
      ),
      iconClickInterceptor = {
        viewModel.viewModelSelectionHelper.unselectAll()
        return@enterDefaultMode false
      },
      menuBuilder = {
        withMenuItem(
          drawableId = R.drawable.ic_search_white_24dp,
          onClick = { toolbarState.enterSearchMode() }
        )

        withOverflowMenu {
          withOverflowMenuItem(
            id = ACTION_DELETE_ALL_SAVED_POSTS,
            stringId = R.string.controller_saved_posts_delete_all,
            onClick = { onDeleteAllSavedPostsClicked() }
          )
        }
      }
    )

    controllerScope.launch {
      viewModel.viewModelSelectionHelper.selectionMode.collect { selectionEvent ->
        onNewSelectionEvent(selectionEvent)
      }
    }

    controllerScope.launch {
      viewModel.viewModelSelectionHelper.bottomPanelMenuItemClickEventFlow
        .collect { menuItemClickEvent ->
          onMenuItemClicked(menuItemClickEvent.menuItemType, menuItemClickEvent.items)
        }
    }

    controllerScope.launch {
      toolbarState.search.listenForSearchVisibilityUpdates()
        .onEach { searchVisible ->
          if (!searchVisible) {
            viewModel.updateSearchQuery(null)
            viewModel.updateQueryAndReload()
          }
        }
        .collect()
    }

    controllerScope.launch {
      toolbarState.search.listenForSearchState()
        .onEach { (_, query) ->
          viewModel.updateSearchQuery(query)
          viewModel.updateQueryAndReload()
        }
        .collect()
    }

    view = ComposeView(context).apply {
      setContent {
        ComposeEntrypoint {
          val chanTheme = LocalChanTheme.current

          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(chanTheme.backColorCompose)
          ) {
            BuildContent()
          }
        }
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    requireBottomPanelContract().hideBottomPanel(controllerKey)

    viewModel.updateQueryAndReload()
    viewModel.viewModelSelectionHelper.unselectAll()
  }

  @Composable
  private fun BoxScope.BuildContent() {
    val myPostsViewModelState by viewModel.myPostsViewModelState.collectAsState()

    val savedRepliesGrouped = when (val savedRepliesAsync = myPostsViewModelState.savedRepliesGroupedAsync) {
      AsyncUiData.NotInitialized -> return
      AsyncUiData.Loading -> {
        KurobaComposeProgressIndicator(
          modifier = Modifier.fillMaxSize()
        )

        return
      }
      is AsyncUiData.Error -> {
        KurobaComposeErrorMessage(
          modifier = Modifier.fillMaxSize(),
          error = savedRepliesAsync.throwable
        )

        return
      }
      is AsyncUiData.UiData -> savedRepliesAsync.data
    }

    BuildSavedRepliesList(
      savedRepliesGrouped = savedRepliesGrouped,
      onHeaderClicked = { threadDescriptor ->
        if (viewModel.viewModelSelectionHelper.isInSelectionMode()) {
          viewModel.toggleGroupSelection(threadDescriptor)

          return@BuildSavedRepliesList
        }

        withLayoutMode(
          phone = {
            requireNavController().popController {
              startActivityCallback.loadThreadAndMarkPost(
                postDescriptor = threadDescriptor.toOriginalPostDescriptor(),
                animated = true
              )
            }
          },
          tablet = {
            startActivityCallback.loadThreadAndMarkPost(
              postDescriptor = threadDescriptor.toOriginalPostDescriptor(),
              animated = true
            )
          }
        )
      },
      onReplyClicked = { postDescriptor ->
        if (viewModel.viewModelSelectionHelper.isInSelectionMode()) {
          viewModel.toggleSelection(postDescriptor)

          return@BuildSavedRepliesList
        }

        withLayoutMode(
          phone = {
            requireNavController().popController {
              startActivityCallback.loadThreadAndMarkPost(
                postDescriptor = postDescriptor,
                animated = true
              )
            }
          },
          tablet = {
            startActivityCallback.loadThreadAndMarkPost(
              postDescriptor = postDescriptor,
              animated = true
            )
          }
        )
      },
      onHeaderLongClicked = { threadDescriptor ->
        if (toolbarState.isInSearchMode()) {
          return@BuildSavedRepliesList
        }

        controllerViewOrNull()?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        viewModel.toggleGroupSelection(threadDescriptor)
      },
      onReplyLongClicked = { postDescriptor ->
        if (toolbarState.isInSearchMode()) {
          return@BuildSavedRepliesList
        }

        controllerViewOrNull()?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        viewModel.toggleSelection(postDescriptor)
      }
    )
  }

  @Composable
  private fun BuildSavedRepliesList(
    savedRepliesGrouped: List<SavedPostsViewModel.GroupedSavedReplies>,
    onHeaderClicked: (ChanDescriptor.ThreadDescriptor) -> Unit,
    onHeaderLongClicked: (ChanDescriptor.ThreadDescriptor) -> Unit,
    onReplyClicked: (PostDescriptor) -> Unit,
    onReplyLongClicked: (PostDescriptor) -> Unit,
  ) {
    val chanTheme = LocalChanTheme.current
    val layoutDirection = LocalLayoutDirection.current
    val contentPaddings = LocalContentPaddings.current

    val state = rememberLazyListState(
      initialFirstVisibleItemIndex = viewModel.rememberedFirstVisibleItemIndex,
      initialFirstVisibleItemScrollOffset = viewModel.rememberedFirstVisibleItemScrollOffset
    )

    DisposableEffect(
      key1 = Unit,
      effect = {
        onDispose {
          viewModel.updatePrevLazyListState(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)
        }
      }
    )

    LaunchedEffect(key1 = Unit) {
      toolbarState.search.searchQueryState.textAsFlow()
        .onEach {
          try {
            state.scrollToItem(0)
          } catch (_: Throwable) {

          }
        }
        .collect()
    }

    val paddingValues = remember(contentPaddings, layoutDirection) {
      contentPaddings
        .asPaddingValues(controllerKey)
    }

    LazyColumnWithFastScroller(
      state = state,
      modifier = Modifier
        .fillMaxSize(),
      contentPadding = paddingValues,
      draggableScrollbar = true
    ) {
      if (savedRepliesGrouped.isEmpty()) {
        val searchQuery = toolbarState.search.searchQueryState.text
        if (searchQuery.isNullOrEmpty()) {
          item(key = "nothing_found_message") {
            KurobaComposeMessage(
              modifier = Modifier.fillParentMaxSize(),
              message = stringResource(id = R.string.search_nothing_found)
            )
          }
        } else {
          item(key = "nothing_found_by_query_message_$searchQuery") {
            KurobaComposeMessage(
              modifier = Modifier.fillParentMaxSize(),
              message = stringResource(id = R.string.search_nothing_found_with_query, searchQuery)
            )
          }
        }

        return@LazyColumnWithFastScroller
      }

      savedRepliesGrouped.forEachIndexed { groupIndex, groupedSavedReplies ->
        item(key = "card_${groupIndex}") {
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .wrapContentHeight()
              .padding(2.dp)
              .animateItem()
          ) {
            Column(
              modifier = Modifier
                .fillMaxSize()
                .background(chanTheme.backColorSecondaryCompose)
                .padding(2.dp)
            ) {
              GroupedSavedReplyHeader(
                groupedSavedReplies = groupedSavedReplies,
                onHeaderClicked = onHeaderClicked,
                onHeaderLongClicked = onHeaderLongClicked
              )

              groupedSavedReplies.savedReplyDataList.forEach { groupedSavedReplyData ->
                Divider(
                  modifier = Modifier.padding(horizontal = 4.dp),
                  color = chanTheme.dividerColorCompose,
                  thickness = 1.dp
                )

                GroupedSavedReply(groupedSavedReplyData, chanTheme, onReplyClicked, onReplyLongClicked)
              }
            }
          }
        }
      }
    }
  }

  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  private fun GroupedSavedReplyHeader(
    groupedSavedReplies: SavedPostsViewModel.GroupedSavedReplies,
    onHeaderClicked: (ChanDescriptor.ThreadDescriptor) -> Unit,
    onHeaderLongClicked: (ChanDescriptor.ThreadDescriptor) -> Unit
  ) {
    val chanTheme = LocalChanTheme.current

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .combinedClickable(
          onClick = { onHeaderClicked(groupedSavedReplies.threadDescriptor) },
          onLongClick = { onHeaderLongClicked(groupedSavedReplies.threadDescriptor) }
        )
        .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
      ) {
        KurobaComposeText(
          text = groupedSavedReplies.headerThreadInfo,
          fontSize = 12.ktu,
          color = chanTheme.textColorHintCompose,
          modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
        )

        if (groupedSavedReplies.headerThreadSubject.isNotNullNorEmpty()) {
          KurobaComposeText(
            text = groupedSavedReplies.headerThreadSubject,
            fontSize = 14.ktu,
            color = chanTheme.postSubjectColorCompose,
            maxLines = 3,
            modifier = Modifier
              .fillMaxWidth()
              .wrapContentHeight()
          )

          Spacer(modifier = Modifier.height(2.dp))
        }
      }
    }
  }

  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  private fun GroupedSavedReply(
    groupedSavedReplyData: SavedPostsViewModel.SavedReplyData,
    chanTheme: ChanTheme,
    onReplyClicked: (PostDescriptor) -> Unit,
    onReplyLongClicked: (PostDescriptor) -> Unit,
  ) {
    val selectionEvent by viewModel.viewModelSelectionHelper.collectSelectionModeAsState()
    val isInSelectionMode = selectionEvent?.isIsSelectionMode() ?: false
    val postDescriptor = groupedSavedReplyData.postDescriptor

    SelectableItem(
      isInSelectionMode = isInSelectionMode,
      observeSelectionStateFunc = { viewModel.viewModelSelectionHelper.observeSelectionState(postDescriptor) },
      onSelectionChanged = { viewModel.viewModelSelectionHelper.toggleSelection(postDescriptor) }
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .defaultMinSize(minHeight = 42.dp)
          .combinedClickable(
            onClick = { onReplyClicked(postDescriptor) },
            onLongClick = { onReplyLongClicked(postDescriptor) }
          )
          .padding(horizontal = 4.dp, vertical = 2.dp)
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
        ) {
          KurobaComposeText(
            text = groupedSavedReplyData.postHeader,
            fontSize = 12.ktu,
            maxLines = 1,
            color = chanTheme.textColorHintCompose,
            modifier = Modifier
              .fillMaxWidth()
              .wrapContentHeight()
          )

          Spacer(modifier = Modifier.height(2.dp))

          KurobaComposeText(
            text = groupedSavedReplyData.comment,
            fontSize = 14.ktu,
            modifier = Modifier
              .fillMaxWidth()
              .wrapContentHeight()
          )
        }
      }
    }
  }

  private fun onMenuItemClicked(
    menuItemType: SavedPostsViewModel.MenuItemType,
    selectedItems: List<PostDescriptor>
  ) {
    if (selectedItems.isEmpty()) {
      return
    }

    when (menuItemType) {
      SavedPostsViewModel.MenuItemType.Delete -> {
        val title = getString(R.string.controller_saved_posts_delete_many_posts, selectedItems.size)
        val descriptionText = getString(R.string.controller_saved_posts_delete_many_posts_description)

        dialogFactory.createSimpleConfirmationDialog(
          context,
          titleText = title,
          descriptionText = descriptionText,
          negativeButtonText = getString(R.string.cancel),
          positiveButtonText = getString(R.string.delete),
          onPositiveButtonClickListener = {
            viewModel.deleteSavedPosts(selectedItems)
          }
        )
      }
    }
  }

  private fun onNewSelectionEvent(selectionEvent: BaseSelectionHelper.SelectionEvent?) {
    when (selectionEvent) {
      is BaseSelectionHelper.SelectionEvent.EnteredSelectionMode,
      is BaseSelectionHelper.SelectionEvent.ItemSelectionToggled -> {
        requireBottomPanelContract().showBottomPanel(controllerKey, viewModel.getBottomPanelMenus())
        enterSelectionModeOrUpdate()
      }
      BaseSelectionHelper.SelectionEvent.ExitedSelectionMode -> {
        requireBottomPanelContract().hideBottomPanel(controllerKey)

        if (toolbarState.isInSelectionMode()) {
          toolbarState.pop()
        }
      }
      null -> return
    }
  }

  private fun enterSelectionModeOrUpdate() {
    val selectedItemsCount = viewModel.viewModelSelectionHelper.selectedItemsCount()
    val totalItemsCount = (viewModel.myPostsViewModelState.value.savedRepliesGroupedAsync as? AsyncUiData.UiData)
      ?.data
      ?.sumOf { groupedSavedReplies -> groupedSavedReplies.savedReplyDataList.size }
      ?: 0

    if (!toolbarState.isInSelectionMode()) {
      toolbarState.enterSelectionMode(
        leftItem = CloseMenuItem(
          onClick = {
            if (toolbarState.isInSelectionMode()) {
              toolbarState.pop()
              viewModel.viewModelSelectionHelper.unselectAll()
            }
          }
        ),
        selectedItemsCount = selectedItemsCount,
        totalItemsCount = totalItemsCount,
      )
    }

    toolbarState.selection.updateCounters(
      selectedItemsCount = selectedItemsCount,
      totalItemsCount = totalItemsCount,
    )
  }

  private fun onDeleteAllSavedPostsClicked() {
    dialogFactory.createSimpleConfirmationDialog(
      context = context,
      titleText = getString(R.string.controller_saved_posts_delete_all_dialog_title),
      descriptionText = getString(R.string.controller_saved_posts_delete_all_dialog_description),
      negativeButtonText = getString(R.string.cancel),
      positiveButtonText = getString(R.string.delete),
      onPositiveButtonClickListener = { viewModel.deleteAllSavedPosts() }
    )
  }

  companion object {
    private const val ACTION_DELETE_ALL_SAVED_POSTS = 0
  }

}
package com.github.k1rakishou.chan.features.bookmarks

import android.content.Context
import android.text.Html
import android.text.SpannableStringBuilder
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.features.toolbar.BackArrowMenuItem
import com.github.k1rakishou.chan.features.toolbar.ToolbarMiddleContent
import com.github.k1rakishou.chan.features.toolbar.ToolbarText
import com.github.k1rakishou.chan.ui.compose.addBottom
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeDraggableElementContainer
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeFab
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeFabMargins
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeFabSize
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeIcon
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeProgressIndicator
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.components.kurobaClickable
import com.github.k1rakishou.chan.ui.compose.compose_task.rememberCancellableCoroutineTask
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.chan.ui.compose.lazylist.LazyColumnWithFastScroller
import com.github.k1rakishou.chan.ui.compose.providers.ComposeEntrypoint
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.providers.LocalContentPaddings
import com.github.k1rakishou.chan.ui.compose.reorder.ReorderableItem
import com.github.k1rakishou.chan.ui.compose.reorder.ReorderableLazyListState
import com.github.k1rakishou.chan.ui.compose.reorder.detectReorder
import com.github.k1rakishou.chan.ui.compose.reorder.rememberReorderableLazyListState
import com.github.k1rakishou.chan.ui.compose.reorder.reorderable
import com.github.k1rakishou.chan.ui.controller.base.Controller
import com.github.k1rakishou.chan.ui.controller.base.DeprecatedNavigationFlags
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.SpannableHelper
import com.github.k1rakishou.chan.utils.viewModelByKey
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import kotlinx.coroutines.launch
import javax.inject.Inject

class BookmarkGroupSettingsController(
  context: Context,
  private val bookmarksToMove: List<ChanDescriptor.ThreadDescriptor>? = null,
  private val refreshBookmarksFunc: () -> Unit
) : Controller(context) {

  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  private val isBookmarkMoveMode: Boolean
    get() = bookmarksToMove != null

  private val viewModel by viewModelByKey<BookmarkGroupSettingsControllerViewModel>()

  override fun injectActivityDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    updateNavigationFlags(
      newNavigationFlags = DeprecatedNavigationFlags()
    )

    val titleStringId = if (isBookmarkMoveMode) {
      R.string.bookmark_groups_controller_select_bookmark_group
    } else {
      R.string.bookmark_groups_controller_title
    }

    toolbarState.enterDefaultMode(
      leftItem = BackArrowMenuItem(
        onClick = { requireNavController().popController() }
      ),
      middleContent = ToolbarMiddleContent.Title(
        title = ToolbarText.Id(titleStringId)
      ),
      menuBuilder = {
        withMenuItem(ACTION_SHOW_HELP, R.drawable.ic_help_outline_white_24dp) { showGroupMatcherHelp() }
      }
    )

    viewModel.reload()

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

    refreshBookmarksFunc.invoke()
  }

  @Composable
  private fun BuildContent() {
    Box {
      BuildContentInternal(
        onCreateGroupClicked = { createBookmarkGroup() }
      )
    }
  }

  @Composable
  private fun BoxScope.BuildContentInternal(
    onCreateGroupClicked: () -> Unit
  ) {
    val chanTheme = LocalChanTheme.current
    val contentPaddings = LocalContentPaddings.current
    val layoutDirection = LocalLayoutDirection.current

    val loading by viewModel.loading
    if (loading) {
      KurobaComposeProgressIndicator(
        modifier = Modifier.fillMaxSize()
      )

      return
    }

    val threadBookmarkGroupItems = viewModel.threadBookmarkGroupItems

    if (threadBookmarkGroupItems.isEmpty()) {
      KurobaComposeText(
        modifier = Modifier
          .wrapContentHeight()
          .fillMaxWidth(),
        text = stringResource(id = R.string.bookmark_groups_controller_no_groups_created)
      )

      return
    }

    val paddingValues = remember(contentPaddings, layoutDirection) {
      contentPaddings
        .asPaddingValues(controllerKey)
        .addBottom(layoutDirection, KurobaComposeFabSize + KurobaComposeFabMargins)
    }

    val reorderTask = rememberCancellableCoroutineTask()
    val reorderableState = rememberReorderableLazyListState(
      onMove = { from, to ->
        reorderTask.launch {
          val fromGroupId = threadBookmarkGroupItems.getOrNull(from.index)?.groupId
            ?: return@launch
          val toGroupId = threadBookmarkGroupItems.getOrNull(to.index)?.groupId
            ?: return@launch

          viewModel.moveBookmarkGroup(from.index, to.index, fromGroupId, toGroupId)
        }
      },
      onDragEnd = { _, _ -> reorderTask.launch { viewModel.onMoveBookmarkGroupComplete() } }
    )

    Column(
      modifier = Modifier
        .wrapContentHeight()
        .align(Alignment.Center)
    ) {
      LazyColumnWithFastScroller(
        modifier = Modifier
          .fillMaxSize()
          .reorderable(reorderableState),
        state = reorderableState.listState,
        contentPadding = paddingValues,
        draggableScrollbar = false,
        content = {
          items(
            count = threadBookmarkGroupItems.size,
            key = { index -> threadBookmarkGroupItems.get(index).groupId },
            itemContent = { index ->
              val threadBookmarkGroupItem = threadBookmarkGroupItems.get(index)

              BuildThreadBookmarkGroupItem(
                threadBookmarkGroupItem = threadBookmarkGroupItem,
                reorderableState = reorderableState,
                bookmarkGroupClicked = { groupId -> bookmarkGroupClicked(groupId) },
                removeBookmarkGroupClicked = { groupId ->
                  controllerScope.launch { viewModel.removeBookmarkGroup(groupId) }
                },
                bookmarkGroupSettingsClicked = { groupId ->
                  val controller = BookmarkGroupPatternSettingsController(
                    context = context,
                    bookmarkGroupId = groupId
                  )

                  presentController(controller)
                },
                bookmarkWarningClicked = { groupId ->
                  showToast(getString(R.string.bookmark_groups_controller_group_has_no_matcher, groupId))
                }
              )
            }
          )
        }
      )
    }

    KurobaComposeFab(
      controllerKey = controllerKey,
      drawableId = R.drawable.ic_add_white_24dp,
      onClick = onCreateGroupClicked
    )
  }

  private fun bookmarkGroupClicked(groupId: String) {
    if (bookmarksToMove == null) {
      return
    }

    controllerScope.launch {
      val allSucceeded = viewModel.moveBookmarksIntoGroup(groupId, bookmarksToMove)
        .toastOnError(longToast = true)
        .safeUnwrap { return@launch }

      if (!allSucceeded) {
        showToast("Not all bookmarks were moved into the group '${groupId}'")
      }

      requireNavController().popController()
    }
  }

  private fun createBookmarkGroup(prevGroupName: String? = null) {
    dialogFactory.createSimpleDialogWithInput(
      context = context,
      titleText = getString(R.string.bookmark_groups_enter_new_group_name),
      inputType = DialogFactory.DialogInputType.String,
      defaultValue = prevGroupName,
      onValueEntered = { groupName ->
        controllerScope.launch {
          val groupIdWithName = viewModel.existingGroupIdAndName(groupName)
            .toastOnError(longToast = true)
            .safeUnwrap { error ->
              Logger.e(TAG, "existingGroupIdAndName($groupName) error", error)
              return@launch
            }

          if (groupIdWithName != null) {
            showToast(getString(R.string.bookmark_groups_group_already_exists,
              groupIdWithName.groupName, groupIdWithName.groupId))

            createBookmarkGroup(prevGroupName = groupName)
            return@launch
          }

          viewModel.createBookmarkGroup(groupName)
            .toastOnError(longToast = true)
            .onSuccess { showToast(R.string.bookmark_groups_group_will_become_visible_after) }
            .ignore()

          viewModel.reload()
        }
      }
    )
  }

  @Composable
  private fun LazyItemScope.BuildThreadBookmarkGroupItem(
    threadBookmarkGroupItem: BookmarkGroupSettingsControllerViewModel.ThreadBookmarkGroupItem,
    reorderableState: ReorderableLazyListState,
    bookmarkGroupClicked: (String) -> Unit,
    bookmarkGroupSettingsClicked: (String) -> Unit,
    removeBookmarkGroupClicked: (String) -> Unit,
    bookmarkWarningClicked: (String) -> Unit
  ) {
    val groupId = threadBookmarkGroupItem.groupId
    val removeBoardClickedRemembered = rememberUpdatedState(newValue = removeBookmarkGroupClicked)
    val bookmarkGroupClickedRemembered = rememberUpdatedState(newValue = bookmarkGroupClicked)
    val bookmarkGroupSettingsClickedRemembered = rememberUpdatedState(newValue = bookmarkGroupSettingsClicked)
    val bookmarkWarningClickedRemembered = rememberUpdatedState(newValue = bookmarkWarningClicked)

    val modifier = if (isBookmarkMoveMode) {
      Modifier.kurobaClickable(
        bounded = true,
        onClick = { bookmarkGroupClickedRemembered.value.invoke(groupId) }
      )
    } else {
      Modifier
    }

    ReorderableItem(
      reorderableState = reorderableState,
      key = groupId
    ) { isDragging ->
      KurobaComposeDraggableElementContainer(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
          .padding(horizontal = 8.dp, vertical = 4.dp)
          .then(modifier),
        isDragging = isDragging
      ) {
        Row(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
          if (!isBookmarkMoveMode && !threadBookmarkGroupItem.isDefaultGroup()) {
            Spacer(modifier = Modifier.width(8.dp))

            KurobaComposeIcon(
              modifier = Modifier
                .size(28.dp)
                .align(Alignment.CenterVertically)
                .kurobaClickable(
                  bounded = false,
                  onClick = { removeBoardClickedRemembered.value.invoke(groupId) }
                ),
              drawableId = R.drawable.ic_clear_white_24dp
            )

            Spacer(modifier = Modifier.width(8.dp))
          }

          val groupText = remember(key1 = removeBookmarkGroupClicked) {
            buildString {
              append(threadBookmarkGroupItem.groupName)
              appendLine()

              append("Bookmarks count: ")
              append(threadBookmarkGroupItem.groupEntriesCount)
            }
          }

          KurobaComposeText(
            modifier = Modifier
              .weight(1f)
              .padding(horizontal = 4.dp)
              .align(Alignment.CenterVertically),
            fontSize = 16.ktu,
            text = groupText
          )

          if (!isBookmarkMoveMode) {
            if (threadBookmarkGroupItem.hasNoMatcher && !threadBookmarkGroupItem.isDefaultGroup()) {
              Spacer(modifier = Modifier.width(8.dp))

              KurobaComposeIcon(
                modifier = Modifier
                  .size(28.dp)
                  .align(Alignment.CenterVertically)
                  .kurobaClickable(
                    bounded = false,
                    onClick = { bookmarkWarningClickedRemembered.value.invoke(groupId) }
                  ),
                drawableId = R.drawable.ic_alert
              )
            }

            if (!threadBookmarkGroupItem.isDefaultGroup()) {
              Spacer(modifier = Modifier.width(8.dp))

              KurobaComposeIcon(
                modifier = Modifier
                  .size(28.dp)
                  .align(Alignment.CenterVertically)
                  .kurobaClickable(
                    bounded = false,
                    onClick = { bookmarkGroupSettingsClickedRemembered.value.invoke(groupId) }
                  ),
                drawableId = R.drawable.ic_settings_white_24dp
              )
            }

            Spacer(modifier = Modifier.width(8.dp))

            KurobaComposeIcon(
              modifier = Modifier
                .size(28.dp)
                .align(Alignment.CenterVertically)
                .detectReorder(reorderableState),
              drawableId = R.drawable.ic_baseline_reorder_24
            )

            Spacer(modifier = Modifier.width(8.dp))
          }
        }
      }
    }
  }

  private fun showGroupMatcherHelp() {
    val message = SpannableHelper.convertHtmlStringTagsIntoSpans(
      message = SpannableStringBuilder.valueOf(Html.fromHtml(getString(R.string.bookmark_group_settings_matcher_help))),
      chanTheme = themeEngine.chanTheme
    )

    DialogFactory.Builder.newBuilder(context, dialogFactory)
      .withTitle(R.string.bookmark_group_settings_matcher_help_title)
      .withDescription(message)
      .create()
  }

  companion object {
    private const val TAG = "BookmarkGroupSettingsController"

    private const val ACTION_SHOW_HELP = 0
  }
}
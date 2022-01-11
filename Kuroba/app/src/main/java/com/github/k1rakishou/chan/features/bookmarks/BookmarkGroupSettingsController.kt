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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.ui.compose.ComposeHelpers.consumeClicks
import com.github.k1rakishou.chan.ui.compose.ComposeHelpers.simpleVerticalScrollbar
import com.github.k1rakishou.chan.ui.compose.KurobaComposeCardView
import com.github.k1rakishou.chan.ui.compose.KurobaComposeIcon
import com.github.k1rakishou.chan.ui.compose.KurobaComposeProgressIndicator
import com.github.k1rakishou.chan.ui.compose.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.KurobaComposeTextBarButton
import com.github.k1rakishou.chan.ui.compose.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.kurobaClickable
import com.github.k1rakishou.chan.ui.compose.reorder.ReorderableState
import com.github.k1rakishou.chan.ui.compose.reorder.detectReorder
import com.github.k1rakishou.chan.ui.compose.reorder.draggedItem
import com.github.k1rakishou.chan.ui.compose.reorder.rememberReorderState
import com.github.k1rakishou.chan.ui.compose.reorder.reorderable
import com.github.k1rakishou.chan.ui.controller.BaseFloatingComposeController
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.SpannableHelper
import com.github.k1rakishou.chan.utils.viewModelByKey
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import kotlinx.coroutines.launch
import javax.inject.Inject

class BookmarkGroupSettingsController(
  context: Context,
  private val bookmarksToMove: List<ChanDescriptor.ThreadDescriptor>? = null,
  private val refreshBookmarksFunc: () -> Unit
) : BaseFloatingComposeController(context) {

  @Inject
  lateinit var dialogFactory: DialogFactory

  private val isBookmarkMoveMode: Boolean
    get() = bookmarksToMove != null

  private val viewModel by lazy {
    requireComponentActivity().viewModelByKey<BookmarkGroupSettingsControllerViewModel>()
  }

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    viewModel.reload()
  }

  override fun onDestroy() {
    super.onDestroy()

    refreshBookmarksFunc.invoke()
  }

  @Composable
  override fun BoxScope.BuildContent() {
    val chanTheme = LocalChanTheme.current
    val focusManager = LocalFocusManager.current

    Box(modifier = Modifier
      .consumeClicks()
      .background(chanTheme.backColorCompose)
    ) {
      BuildContentInternal(
        onHelpClicked = { showGroupMatcherHelp() },
        onCloseClicked = {
          focusManager.clearFocus(force = true)
          pop()
        },
        onCreateGroupClicked = { createBookmarkGroup() }
      )
    }
  }

  @Composable
  private fun BoxScope.BuildContentInternal(
    onHelpClicked: () -> Unit,
    onCloseClicked: () -> Unit,
    onCreateGroupClicked: () -> Unit
  ) {
    val reoderableState = rememberReorderState()
    val onHelpClickedRemembered = rememberUpdatedState(newValue = onHelpClicked)
    val onCloseClickedRemembered = rememberUpdatedState(newValue = onCloseClicked)
    val onCreateGroupClickedRemembered = rememberUpdatedState(newValue = onCreateGroupClicked)

    val loading by viewModel.loading
    if (loading) {
      KurobaComposeProgressIndicator()
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

    Column(
      modifier = Modifier
        .wrapContentHeight()
        .align(Alignment.Center)
    ) {
      Spacer(modifier = Modifier.height(8.dp))

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
      ) {
        if (isBookmarkMoveMode) {
          KurobaComposeText(
            modifier = Modifier
              .wrapContentHeight()
              .weight(1f),
            textAlign = TextAlign.Center,
            text = stringResource(id = R.string.bookmark_groups_controller_select_bookmark_group)
          )
        } else {
          KurobaComposeText(
            modifier = Modifier
              .wrapContentHeight()
              .weight(1f),
            textAlign = TextAlign.Center,
            text = stringResource(id = R.string.bookmark_groups_controller_title)
          )
        }

        KurobaComposeIcon(
          modifier = Modifier
            .kurobaClickable(onClick = { onHelpClickedRemembered.value.invoke() }),
          drawableId = R.drawable.ic_help_outline_white_24dp
        )

        Spacer(modifier = Modifier.width(8.dp))
      }

      Spacer(modifier = Modifier.height(8.dp))

      val chanTheme = LocalChanTheme.current

      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
          .simpleVerticalScrollbar(reoderableState.listState, chanTheme)
          .reorderable(
            state = reoderableState,
            onMove = { from, to ->
              val fromGroupId = threadBookmarkGroupItems.getOrNull(from)?.groupId
                ?: return@reorderable
              val toGroupId = threadBookmarkGroupItems.getOrNull(to)?.groupId
                ?: return@reorderable

              viewModel.moveBookmarkGroup(from, to, fromGroupId, toGroupId)
            },
            onDragEnd = { _, _ -> viewModel.onMoveBookmarkGroupComplete() }
          ),
        state = reoderableState.listState,
        content = {
          items(
            count = threadBookmarkGroupItems.size,
            key = { index -> threadBookmarkGroupItems.get(index).groupId },
            itemContent = { index ->
              val threadBookmarkGroupItem = threadBookmarkGroupItems.get(index)
              BuildThreadBookmarkGroupItem(
                threadBookmarkGroupItem = threadBookmarkGroupItem,
                reoderableState = reoderableState,
                bookmarkGroupClicked = { groupId -> bookmarkGroupClicked(groupId) },
                removeBookmarkGroupClicked = { groupId ->
                  mainScope.launch { viewModel.removeBookmarkGroup(groupId) }
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

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
      ) {
        Spacer(modifier = Modifier.weight(1f))

        KurobaComposeTextBarButton(
          onClick = { onCloseClickedRemembered.value.invoke() },
          text = stringResource(id = R.string.close)
        )

        Spacer(modifier = Modifier.width(8.dp))

        KurobaComposeTextBarButton(
          onClick = { onCreateGroupClickedRemembered.value.invoke() },
          text = stringResource(id = R.string.bookmark_groups_controller_create_new_group)
        )

        Spacer(modifier = Modifier.width(8.dp))
      }
    }
  }

  private fun bookmarkGroupClicked(groupId: String) {
    if (bookmarksToMove == null) {
      return
    }

    mainScope.launch {
      val allSucceeded = viewModel.moveBookmarksIntoGroup(groupId, bookmarksToMove)
        .toastOnError(longToast = true)
        .safeUnwrap { return@launch }

      if (!allSucceeded) {
        showToast("Not all bookmarks were moved into the group '${groupId}'")
      }

      pop()
    }
  }

  private fun createBookmarkGroup(prevGroupName: String? = null) {
    dialogFactory.createSimpleDialogWithInput(
      context = context,
      titleText = getString(R.string.bookmark_groups_enter_new_group_name),
      inputType = DialogFactory.DialogInputType.String,
      defaultValue = prevGroupName,
      onValueEntered = { groupName ->
        mainScope.launch {
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
            .peekValue { showToast(R.string.bookmark_groups_group_will_become_visible_after) }
            .ignore()

          viewModel.reload()
        }
      }
    )
  }

  @Composable
  private fun BuildThreadBookmarkGroupItem(
    threadBookmarkGroupItem: BookmarkGroupSettingsControllerViewModel.ThreadBookmarkGroupItem,
    reoderableState: ReorderableState,
    bookmarkGroupClicked: (String) -> Unit,
    bookmarkGroupSettingsClicked: (String) -> Unit,
    removeBookmarkGroupClicked: (String) -> Unit,
    bookmarkWarningClicked: (String) -> Unit
  ) {
    val chanTheme = LocalChanTheme.current
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
      Modifier.draggedItem(reoderableState.offsetByKey(groupId))
    }

    KurobaComposeCardView(
      modifier = Modifier
        .fillMaxWidth()
        .height(48.dp)
        .padding(4.dp)
        .then(modifier),
      backgroundColor = chanTheme.backColorSecondaryCompose
    ) {
      Row(modifier = Modifier.fillMaxSize()) {
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
          fontSize = 16.sp,
          modifier = Modifier
            .weight(1f)
            .padding(horizontal = 4.dp)
            .align(Alignment.CenterVertically),
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
              .detectReorder(reoderableState),
            drawableId = R.drawable.ic_baseline_reorder_24
          )

          Spacer(modifier = Modifier.width(8.dp))
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
  }
}
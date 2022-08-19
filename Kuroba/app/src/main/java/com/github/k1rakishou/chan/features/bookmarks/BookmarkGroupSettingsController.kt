package com.github.k1rakishou.chan.features.bookmarks

import android.content.Context
import android.text.Html
import android.text.SpannableStringBuilder
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.ui.compose.ComposeHelpers.simpleVerticalScrollbar
import com.github.k1rakishou.chan.ui.compose.KurobaComposeCardView
import com.github.k1rakishou.chan.ui.compose.KurobaComposeIcon
import com.github.k1rakishou.chan.ui.compose.KurobaComposeProgressIndicator
import com.github.k1rakishou.chan.ui.compose.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.ProvideChanTheme
import com.github.k1rakishou.chan.ui.compose.kurobaClickable
import com.github.k1rakishou.chan.ui.compose.reorder.ReorderableState
import com.github.k1rakishou.chan.ui.compose.reorder.detectReorder
import com.github.k1rakishou.chan.ui.compose.reorder.draggedItem
import com.github.k1rakishou.chan.ui.compose.reorder.rememberReorderState
import com.github.k1rakishou.chan.ui.compose.reorder.reorderable
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
) : Controller(context), WindowInsetsListener {

  @Inject
  lateinit var dialogFactory: DialogFactory
  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  private val bottomPadding = mutableStateOf(0)

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

    val titleStringId = if (isBookmarkMoveMode) {
      R.string.bookmark_groups_controller_select_bookmark_group
    } else {
      R.string.bookmark_groups_controller_title
    }

    navigation.setTitle(titleStringId)
    navigation.swipeable = false

    navigation
      .buildMenu(context)
      .withItem(ACTION_SHOW_HELP, R.drawable.ic_help_outline_white_24dp, { showGroupMatcherHelp() })
      .build()

    onInsetsChanged()
    globalWindowInsetsManager.addInsetsUpdatesListener(this)
    viewModel.reload()

    view = ComposeView(context).apply {
      setContent {
        ProvideChanTheme(themeEngine) {
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

    globalWindowInsetsManager.removeInsetsUpdatesListener(this)
    refreshBookmarksFunc.invoke()
  }

  override fun onInsetsChanged() {
    val bottomPaddingDp = calculateBottomPaddingForRecyclerInDp(
      globalWindowInsetsManager = globalWindowInsetsManager,
      mainControllerCallbacks = null
    )

    bottomPadding.value = bottomPaddingDp
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
    val reoderableState = rememberReorderState()
    val onCreateGroupClickedRemembered = rememberUpdatedState(newValue = onCreateGroupClicked)
    val chanTheme = LocalChanTheme.current

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

    val bottomPd by bottomPadding
    val paddingValues = remember(key1 = bottomPd) { PaddingValues(bottom = bottomPd.dp + FAB_SIZE + FAB_MARGIN) }

    Column(
      modifier = Modifier
        .wrapContentHeight()
        .align(Alignment.Center)
    ) {
      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
          .simpleVerticalScrollbar(reoderableState.listState, chanTheme, paddingValues)
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
        contentPadding = paddingValues,
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
    }

    FloatingActionButton(
      modifier = Modifier
        .size(FAB_SIZE)
        .align(Alignment.BottomEnd)
        .offset(x = -FAB_MARGIN, y = -(bottomPd.dp + (FAB_MARGIN / 2))),
      backgroundColor = chanTheme.accentColorCompose,
      contentColor = Color.White,
      onClick = { onCreateGroupClickedRemembered.value.invoke() }
    ) {
      Icon(
        painter = painterResource(id = R.drawable.ic_add_white_24dp),
        contentDescription = null
      )
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

      navigationController?.popController()
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

    private const val ACTION_SHOW_HELP = 0

    private val FAB_SIZE = 52.dp
    private val FAB_MARGIN = 16.dp
  }
}
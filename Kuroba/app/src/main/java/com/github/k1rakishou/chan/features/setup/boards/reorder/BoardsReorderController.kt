package com.github.k1rakishou.chan.features.setup.boards.reorder

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.FloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.controller.ControllerComponent
import com.github.k1rakishou.chan.features.setup.boards.add.AddBoardsController
import com.github.k1rakishou.chan.features.toolbar.BackArrowMenuItem
import com.github.k1rakishou.chan.features.toolbar.CloseMenuItem
import com.github.k1rakishou.chan.features.toolbar.ToolbarMenuBuilder
import com.github.k1rakishou.chan.features.toolbar.ToolbarMiddleContent
import com.github.k1rakishou.chan.features.toolbar.ToolbarText
import com.github.k1rakishou.chan.features.toolbar.state.ToolbarStateKind
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeCheckbox
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeDraggableElementContainer
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeErrorMessage
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeFabMargins
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeFabSize
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeIcon
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeMessage
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeProgressIndicator
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.components.kurobaClickable
import com.github.k1rakishou.chan.ui.compose.compose_task.rememberCancellableCoroutineTask
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.chan.ui.compose.lazylist.LazyColumnWithFastScroller
import com.github.k1rakishou.chan.ui.compose.panel.KurobaIconPanel
import com.github.k1rakishou.chan.ui.compose.panel.KurobaIconPanelState
import com.github.k1rakishou.chan.ui.compose.panel.rememberKurobaIconPanelState
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.providers.LocalContentPaddings
import com.github.k1rakishou.chan.ui.compose.reorder.ReorderableItem
import com.github.k1rakishou.chan.ui.compose.reorder.ReorderableLazyListState
import com.github.k1rakishou.chan.ui.compose.reorder.detectReorder
import com.github.k1rakishou.chan.ui.compose.reorder.rememberReorderableLazyListState
import com.github.k1rakishou.chan.ui.compose.reorder.reorderable
import com.github.k1rakishou.chan.ui.compose.snackbar.SnackbarScope
import com.github.k1rakishou.chan.ui.controller.base.BaseComposeController
import com.github.k1rakishou.chan.ui.controller.base.DeprecatedNavigationFlags
import com.github.k1rakishou.chan.utils.ViewModelScope
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor

class BoardsReorderController(
  context: Context,
  siteDescriptor: SiteDescriptor
) : BaseComposeController<BoardsReorderControllerViewModel, BoardsReorderControllerParams>(
  context = context,
  viewModelClass = BoardsReorderControllerViewModel::class.java,
  viewModelParams = BoardsReorderControllerParams(siteDescriptor)
) {
  override val viewModelScope: ViewModelScope
    get() = ViewModelScope.ControllerScope(this)

  override val layoutAnchor: SnackbarScope.LayoutAnchor
    get() = SnackbarScope.LayoutAnchor.Catalog

  override fun injectControllerDependencies(component: ControllerComponent) {
    component.inject(this)
  }

  override fun onBack(): Boolean {
    if (viewModel.onBackPressed()) {
      return true
    }

    return super.onBack()
  }

  override fun setupNavigation() {
    updateNavigationFlags(
      newNavigationFlags = DeprecatedNavigationFlags()
    )

    toolbarState.enterDefaultMode(
      leftItem = BackArrowMenuItem(
        onClick = { requireNavController().popController() }
      ),
      middleContent = ToolbarMiddleContent.Title(
        title = ToolbarText.String(
          appResources.string(R.string.controller_boards_reorder_title, viewModel.siteDescriptor.siteName)
        )
      ),
      menuBuilder = defaultToolbarMenuBuilder(viewModel)
    )
  }

  @Composable
  override fun ScreenContent() {
    val contentPaddings = LocalContentPaddings.current
    val chanTheme = LocalChanTheme.current

    val errorMut by viewModel.error
    val error = errorMut

    val loading by viewModel.loading
    val reorderableBoards = viewModel.reorderableBoards
    val selectedBoards = viewModel.selectedBoards
    val isInSelectionMode by remember { derivedStateOf { selectedBoards.isNotEmpty() } }

    val reorderTask = rememberCancellableCoroutineTask()
    val reorderableState = rememberReorderableLazyListState(
      onMove = { from, to ->
        val fromBoardDescriptor = reorderableBoards.getOrNull(from.index)?.boardDescriptor
          ?: return@rememberReorderableLazyListState
        val toBoardDescriptor = reorderableBoards.getOrNull(to.index)?.boardDescriptor
          ?: return@rememberReorderableLazyListState

        reorderTask.launch {
          viewModel.moveBoard(fromBoardDescriptor, toBoardDescriptor)
        }
      },
      onDragEnd = { _, _ -> reorderTask.launch { viewModel.moveBoardEnd() } }
    )

    val panelState = rememberKurobaIconPanelState(
      controllerKey = controllerKey,
      visible = false,
      orientation = KurobaIconPanelState.Orientation.Horizontal,
      menuItems = remember {
        listOf(
          KurobaIconPanelState.MenuItem(
            id = PANEL_DELETE_ITEM_ID,
            iconId = R.drawable.ic_baseline_delete_outline_24,
            textId = R.string.delete
          )
        )
      }
    )

    LaunchedEffect(key1 = isInSelectionMode) {
      if (isInSelectionMode) {
        panelState.show()
        enterSelectionModeOrUpdate()
      } else {
        panelState.hide()
        toolbarState.popIfInState(ToolbarStateKind.Selection)
      }
    }

    Box(
      modifier = Modifier.fillMaxSize()
    ) {
      LazyColumnWithFastScroller(
        modifier = Modifier
          .fillMaxSize()
          .padding(top = contentPaddings.calculateTopPadding())
          .reorderable(reorderableState),
        state = reorderableState.listState,
        draggableScrollbar = false
      ) {
        if (error != null) {
          item(key = "error_message") {
            KurobaComposeErrorMessage(
              modifier = Modifier.fillParentMaxSize(),
              error = error
            )
          }

          return@LazyColumnWithFastScroller
        }

        if (reorderableBoards.isEmpty()) {
          if (loading) {
            item(key = "boards_first_load") {
              KurobaComposeProgressIndicator(modifier = Modifier.fillParentMaxSize())
            }
          } else {
            item(key = "no_boards_for_site") {
              KurobaComposeMessage(
                modifier = Modifier.fillParentMaxSize(),
                message = stringResource(R.string.controller_boards_reorder_no_boards)
              )
            }
          }

          return@LazyColumnWithFastScroller
        }

        items(
          count = reorderableBoards.size,
          key = { idx -> reorderableBoards.getOrNull(idx)?.boardDescriptor ?: "null" },
          itemContent = { idx ->
            val reorderableBoard = reorderableBoards.getOrNull(idx)
              ?: return@items

            ReorderableBoardElement(
              index = idx,
              isInSelectionMode = isInSelectionMode,
              isSelected = viewModel.isReorderableBoardSelected(reorderableBoard.boardDescriptor),
              reorderableState = reorderableState,
              reorderableBoard = reorderableBoard,
              onSelectionChanged = { boardDescriptor, selected ->
                viewModel.onBoardSelectionChanged(boardDescriptor, selected)
                enterSelectionModeOrUpdate()
              }
            )
          }
        )
      }

      KurobaIconPanel(
        modifier = Modifier
          .wrapContentSize()
          .align(Alignment.BottomCenter),
        panelState = panelState,
        onMenuItemClicked = { clickedMenuId ->
          when (clickedMenuId) {
            PANEL_DELETE_ITEM_ID -> viewModel.onDeleteBoardsClicked()
          }
        }
      )

      AnimatedContent(
        modifier = Modifier
          .size(KurobaComposeFabSize)
          .align(Alignment.BottomEnd)
          .offset {
            return@offset IntOffset(
              x = -(KurobaComposeFabMargins.roundToPx()),
              y = -(contentPaddings.calculateBottomPadding() + (KurobaComposeFabMargins / 2))
                .roundToPx()
            )
          },
        targetState = isInSelectionMode,
        contentAlignment = Alignment.Center,
        transitionSpec = {
          scaleIn()
            .togetherWith(scaleOut())
        }
      ) { selectionMode ->
        if (selectionMode) {
          Spacer(modifier = Modifier.fillMaxSize())
          return@AnimatedContent
        }

        FloatingActionButton(
          modifier = Modifier
            .fillMaxSize(),
          backgroundColor = chanTheme.accentColorCompose,
          contentColor = ThemeEngine.resolveTextColor(chanTheme.accentColorCompose),
          onClick = {
            val controller = AddBoardsController(
              context = context,
              siteDescriptor = viewModel.siteDescriptor,
              refreshBoardsFunc = { viewModel.displayActiveBoards() }
            )

            requireNavController().pushController(controller)
          },
          content = {
            KurobaComposeIcon(drawableId = R.drawable.ic_add_white_24dp)
          }
        )
      }
    }
  }

  @Composable
  private fun ReorderableBoardElement(
    index: Int,
    isInSelectionMode: Boolean,
    isSelected: Boolean,
    reorderableState: ReorderableLazyListState,
    reorderableBoard: BoardsReorderControllerViewModel.ReorderableBoard,
    onSelectionChanged: (BoardDescriptor, Boolean) -> Unit
  ) {
    val chanTheme = LocalChanTheme.current

    val index = remember(key1 = index) {
      buildAnnotatedString {
        pushStyle(SpanStyle(color = chanTheme.textColorSecondaryCompose))
        append("#${index + 1}")
      }
    }

    val title = remember(key1 = reorderableBoard.boardName) {
      buildAnnotatedString {
        pushStyle(SpanStyle(color = chanTheme.textColorPrimaryCompose))
        append(reorderableBoard.boardName)
      }
    }

    val description = remember(key1 = reorderableBoard.description) {
      buildAnnotatedString {
        pushStyle(SpanStyle(color = chanTheme.textColorSecondaryCompose))
        append(reorderableBoard.description)
      }
    }

    ReorderableItem(
      modifier = Modifier
        .kurobaClickable(
          bounded = true,
          onClick = {
            if (isInSelectionMode) {
              onSelectionChanged(reorderableBoard.boardDescriptor, !isSelected)
            }
          },
          onLongClick = {
            if (!isInSelectionMode) {
              onSelectionChanged(reorderableBoard.boardDescriptor, true)
            }
          }
        ),
      state = reorderableState,
      key = reorderableBoard.boardDescriptor
    ) { isDragging ->
      KurobaComposeDraggableElementContainer(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight(),
        isDragging = isDragging
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          KurobaComposeText(
            modifier = Modifier.padding(end = 16.dp),
            text = index,
            fontSize = 14.ktu
          )

          Column(
            modifier = Modifier.weight(1f)
          ) {
            KurobaComposeText(text = title, fontSize = 18.ktu)

            if (description.text.isNotBlank()) {
              KurobaComposeText(text = description, fontSize = 14.ktu)
            }
          }

          Crossfade(
            modifier = Modifier.size(48.dp),
            targetState = isInSelectionMode
          ) { inSelectionMode ->
            if (inSelectionMode) {
              KurobaComposeCheckbox(
                modifier = Modifier
                  .fillMaxSize(),
                currentlyChecked = isSelected,
                onCheckChanged = { checked -> onSelectionChanged(reorderableBoard.boardDescriptor, checked) }
              )
            } else {
              KurobaComposeIcon(
                modifier = Modifier
                  .fillMaxSize()
                  .detectReorder(reorderableState),
                drawableId = R.drawable.ic_drag_handle_list_white_24dp
              )
            }
          }
        }
      }
    }
  }

  private fun enterSelectionModeOrUpdate() {
    val selectedItemsCount = viewModel.selectedBoards.size
    val totalItemsCount = viewModel.reorderableBoards.size

    if (!toolbarState.isInSelectionMode()) {
      toolbarState.enterSelectionMode(
        leftItem = CloseMenuItem(
          onClick = { viewModel.unselectAll() }
        ),
        selectedItemsCount = selectedItemsCount,
        totalItemsCount = totalItemsCount,
        menuBuilder = selectionToolbarMenuBuilder(viewModel)
      )
    }

    toolbarState.selection.updateCounters(
      selectedItemsCount = selectedItemsCount,
      totalItemsCount = totalItemsCount
    )
  }

  private fun selectionToolbarMenuBuilder(
    viewModel: BoardsReorderControllerViewModel
  ): ToolbarMenuBuilder.() -> Unit = {
    withMenuItem(
      id = ACTION_TOGGLE_SELECTION,
      drawableId = R.drawable.ic_select_all_white_24dp
    ) { viewModel.toggleSelectionForAllBoards() }
  }

  private fun defaultToolbarMenuBuilder(
    viewModel: BoardsReorderControllerViewModel
  ): ToolbarMenuBuilder.() -> Unit = {
    if (!viewModel.isSyntheticSite()) {
      withMenuItem(
        id = ACTION_REFRESH,
        drawableId = R.drawable.ic_refresh_white_24dp,
        onClick = { viewModel.updateBoardsFromServerAndDisplayActive() }
      )
    }

    withOverflowMenu {
      withOverflowMenuItem(
        id = ACTION_SORT_BOARDS_ALPHABETICALLY,
        stringId = R.string.controller_boards_reorder_sort_boards_alphabetically,
        onClick = { viewModel.sortBoardsAlphabetically() }
      )

      withOverflowMenuItem(
        id = ACTION_DELETE_ALL_BOARDS,
        stringId = R.string.controller_boards_reorder_delete_all_boards,
        onClick = { viewModel.deactivateAllBoards() }
      )
    }
  }

  companion object {
    private const val ACTION_SORT_BOARDS_ALPHABETICALLY = 0
    private const val ACTION_DELETE_ALL_BOARDS = 1
    private const val ACTION_REFRESH = 2
    private const val ACTION_TOGGLE_SELECTION = 3

    private const val PANEL_DELETE_ITEM_ID = 0
  }

}
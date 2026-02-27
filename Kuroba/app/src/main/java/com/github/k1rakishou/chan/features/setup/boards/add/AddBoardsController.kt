package com.github.k1rakishou.chan.features.setup.boards.add

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.compose.AsyncUiData
import com.github.k1rakishou.chan.core.di.component.controller.ControllerComponent
import com.github.k1rakishou.chan.features.toolbar.BackArrowMenuItem
import com.github.k1rakishou.chan.features.toolbar.ToolbarMiddleContent
import com.github.k1rakishou.chan.features.toolbar.ToolbarText
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeCheckbox
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeErrorMessage
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeMessage
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeProgressIndicator
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.components.KurobaSearchInput
import com.github.k1rakishou.chan.ui.compose.components.kurobaClickable
import com.github.k1rakishou.chan.ui.compose.forEachTextValue
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.chan.ui.compose.lazylist.LazyColumnWithFastScroller
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.scaffold.ListScaffoldShared
import com.github.k1rakishou.chan.ui.compose.scaffold.NormalLazyListScaffoldBuilder
import com.github.k1rakishou.chan.ui.compose.snackbar.SnackbarContainer
import com.github.k1rakishou.chan.ui.compose.snackbar.SnackbarScope
import com.github.k1rakishou.chan.ui.controller.base.BaseComposeController
import com.github.k1rakishou.chan.ui.controller.base.DeprecatedNavigationFlags
import com.github.k1rakishou.chan.utils.ComposeAnnotatedStringHelper
import com.github.k1rakishou.chan.utils.ComposeAnnotatedStringHelperImpl
import com.github.k1rakishou.chan.utils.ViewModelScope
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.core_themes.resolveTextColor
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.collectLatest

class AddBoardsController(
  context: Context,
  siteDescriptor: SiteDescriptor,
  private val refreshBoardsFunc: () -> Unit
) : BaseComposeController<AddBoardsControllerViewModel, AddBoardsControllerParams>(
  context = context,
  viewModelClass = AddBoardsControllerViewModel::class.java,
  viewModelParams = AddBoardsControllerParams(siteDescriptor)
) {
  override val viewModelScope: ViewModelScope
    get() = ViewModelScope.ControllerScope(this)

  override val layoutAnchor: SnackbarScope.LayoutAnchor
    get() = SnackbarScope.LayoutAnchor.Catalog

  override fun injectControllerDependencies(component: ControllerComponent) {
    component.inject(this)
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
        title = ToolbarText.String(appResources.string(R.string.add_boards_controller_toolbar_title)),
        subtitle = null
      )
    )
  }

  @Composable
  override fun ScreenContent() {
    Column(
      modifier = Modifier
        .fillMaxSize()
    ) {
      val chanTheme = LocalChanTheme.current

      val uiStateMut by viewModel.uiState
      val uiState = uiStateMut

      val boardsForSelection = viewModel.boardsForSelection
      val checkedBoards = viewModel.checkedBoards
      val processing by viewModel.processing
      val currentSearchQuery by viewModel.currentSearchQuery
      val totalBoardsCount by viewModel.totalBoardsCount
      val totalMatchedBySearchQueryCount by viewModel.totalMatchedBySearchQueryCount

      val lazyListState = rememberLazyListState()
      val searchQueryState = rememberTextFieldState()

      val maxDisplayedBoards = AddBoardsControllerViewModel.MAX_DISPLAYED_BOARDS

      LaunchedEffect(key1 = Unit) {
        searchQueryState.forEachTextValue { text ->
          viewModel.onSearchQueryUpdated(text.toString())
        }
      }

      LaunchedEffect(key1 = Unit) {
        viewModel.resetScrollEventFlow
          .collectLatest {
            try {
              awaitFrame()
              lazyListState.scrollToItem(0)
            } catch (ignored: Throwable) {
              // no-op
            }
          }
      }

      Box(
        modifier = Modifier.fillMaxSize()
      ) {
        with(NormalLazyListScaffoldBuilder()) {
          Content(
            boxScope = this@Box,
            controllerKey = controllerKey,
            header = {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .background(chanTheme.backColorCompose)
              ) {
                val kurobaSearchInputColor = if (ThemeEngine.isDarkColor(chanTheme.backColorCompose)) {
                  Color.White
                } else {
                  Color.Black
                }

                KurobaSearchInput(
                  modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(vertical = 8.dp),
                  displayClearButton = true,
                  color = kurobaSearchInputColor,
                  searchQueryState = searchQueryState
                )
              }
            },
            body = { paddings ->
              LazyColumnWithFastScroller(
                modifier = Modifier.fillMaxSize(),
                state = lazyListState,
                contentPadding = paddings
              ) {
                when (uiState) {
                  AsyncUiData.NotInitialized -> {
                    return@LazyColumnWithFastScroller
                  }
                  AsyncUiData.Loading -> {
                    item(key = "progress_indicator") {
                      KurobaComposeProgressIndicator(
                        modifier = Modifier.fillParentMaxSize()
                      )
                    }

                    return@LazyColumnWithFastScroller
                  }
                  is AsyncUiData.Error -> {
                    item(key = "error_message") {
                      KurobaComposeErrorMessage(
                        modifier = Modifier.fillParentMaxSize(),
                        error = uiState.throwable
                      )
                    }

                    return@LazyColumnWithFastScroller
                  }
                  is AsyncUiData.UiData<*> -> {
                    // no-op
                  }
                }

                if (boardsForSelection.isEmpty()) {
                  item(key = "nothing_found") {
                    KurobaComposeMessage(
                      modifier = Modifier.fillParentMaxSize(),
                      message = stringResource(
                        R.string.add_boards_controller_nothing_found_by_query,
                        currentSearchQuery
                      )
                    )
                  }

                  return@LazyColumnWithFastScroller
                }

                if (totalBoardsCount > 0) {
                  item(key = "found_boards_info") {
                    FoundBoardsInfo(
                      modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                      currentSearchQuery = currentSearchQuery,
                      boardsForSelectionCount = boardsForSelection.size,
                      totalBoardsCount = totalBoardsCount,
                      totalMatchedBySearchQueryCount = totalMatchedBySearchQueryCount,
                      maxDisplayedBoards = maxDisplayedBoards
                    )
                  }
                }

                items(
                  count = boardsForSelection.size,
                  key = { idx -> boardsForSelection.getOrNull(idx)?.composeKey() ?: "null" },
                  itemContent = { idx ->
                    val boardForSelection = boardsForSelection.getOrNull(idx)
                      ?: return@items

                    BoardForSelectionElement(
                      index = idx,
                      currentSearchQuery = currentSearchQuery,
                      enabled = !processing,
                      checked = boardForSelection.boardDescriptor in checkedBoards,
                      boardForSelection = boardForSelection,
                      onCheckChanged = { boardDescriptor, check ->
                        viewModel.onBoardCheckStateChanged(boardDescriptor, check)
                      }
                    )
                  }
                )
              }
            },
            footer = { bottomPadding ->
              val toggleAllButton = ListScaffoldShared.Button(
                text = stringResource(R.string.add_boards_controller_toggle_all),
                fontSize = 18.ktu,
                enabled = !processing,
                onClick = {
                  viewModel.toggleAll()
                }
              )

              val positiveButton = if (checkedBoards.isEmpty()) {
                null
              } else {
                ListScaffoldShared.Button(
                  text = stringResource(R.string.add_boards_controller_add_boards, checkedBoards.size),
                  fontSize = 18.ktu,
                  enabled = !processing,
                  onClick = {
                    if (checkedBoards.size < maxDisplayedBoards) {
                      viewModel.activateCheckedBoards(
                        onDone = { closeScreen() }
                      )
                      return@Button
                    }

                    dialogFactory.createSimpleConfirmationDialog(
                      context = context,
                      titleText = appResources.string(
                        R.string.add_boards_controller_adding_too_many_boards_dialog_title,
                        checkedBoards.size
                      ),
                      descriptionText = appResources.string(
                        R.string.add_boards_controller_adding_too_many_boards_dialog_description,
                        checkedBoards.size
                      ),
                      negativeButtonText = appResources.string(R.string.no),
                      positiveButtonText = appResources.string(R.string.yes),
                      onPositiveButtonClickListener = {
                        viewModel.activateCheckedBoards(
                          onDone = { closeScreen() }
                        )
                      }
                    )
                  }
                )
              }

              Column(
                modifier = Modifier
                  .background(chanTheme.primaryColorCompose)
              ) {
                Footer(
                  modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                  extractButton = toggleAllButton,
                  negativeButton = null,
                  positiveButton = positiveButton
                )

                Spacer(modifier = Modifier.height(bottomPadding))
              }
            }
          )
        }

        SnackbarContainer(
          modifier = Modifier.fillMaxSize(),
          snackbarScope = snackbarScope
        )
      }
    }
  }

  @Composable
  private fun BoardForSelectionElement(
    index: Int,
    currentSearchQuery: String,
    enabled: Boolean,
    checked: Boolean,
    boardForSelection: AddBoardsControllerViewModel.BoardForSelection,
    onCheckChanged: (BoardDescriptor, Boolean) -> Unit
  ) {
    val chanTheme = LocalChanTheme.current

    val index = remember(index, chanTheme.textColorSecondaryCompose) {
      buildAnnotatedString {
        pushStyle(SpanStyle(color = chanTheme.textColorSecondaryCompose))
        append("#${index + 1}")
      }
    }

    val title = remember(
      currentSearchQuery,
      boardForSelection.boardName,
      chanTheme.textColorPrimaryCompose,
      chanTheme.accentColorCompose
    ) {
      buildAnnotatedString {
        pushStyle(SpanStyle(color = chanTheme.textColorPrimaryCompose))
        append(boardForSelection.boardName)

        with(ComposeAnnotatedStringHelperImpl()) {
          val textMark = ComposeAnnotatedStringHelper.TextMark(
            pattern = currentSearchQuery,
            backgroundColor = chanTheme.accentColorCompose,
            textColor = ThemeEngine.resolveTextColor(chanTheme.accentColorCompose)
          )

          markText(
            text = boardForSelection.boardName,
            textMarks = listOf(textMark)
          )
        }
      }
    }

    val description = remember(key1 = currentSearchQuery, key2 = boardForSelection.description) {
      buildAnnotatedString {
        pushStyle(SpanStyle(color = chanTheme.textColorSecondaryCompose))
        append(boardForSelection.description)

        with(ComposeAnnotatedStringHelperImpl()) {
          val textMark = ComposeAnnotatedStringHelper.TextMark(
            pattern = currentSearchQuery,
            backgroundColor = chanTheme.accentColorCompose,
            textColor = ThemeEngine.resolveTextColor(chanTheme.accentColorCompose)
          )

          markText(
            text = boardForSelection.description,
            textMarks = listOf(textMark)
          )
        }
      }
    }

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 64.dp)
        .kurobaClickable(
          bounded = true,
          enabled = enabled,
          onClick = {
            onCheckChanged(boardForSelection.boardDescriptor, !checked)
          }
        )
        .drawBehind {
          if (checked) {
            drawRect(chanTheme.postHighlightedColorCompose)
          }
        }
        .graphicsLayer {
          alpha = if (enabled) 1f else 0.6f
        }
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

        if (boardForSelection.workSafe == false) {
          Spacer(modifier = Modifier.height(4.dp))

          KurobaComposeText(
            modifier = Modifier
              .background(color = chanTheme.accentColorCompose)
              .padding(horizontal = 4.dp, vertical = 2.dp),
            text = stringResource(R.string.controller_board_nsfw_board_tag),
            color = chanTheme.accentColorCompose.resolveTextColor(),
            fontSize = 12.ktu
          )
        }
      }

      KurobaComposeCheckbox(
        modifier = Modifier
          .wrapContentSize()
          .padding(horizontal = 16.dp, vertical = 8.dp),
        enabled = enabled,
        currentlyChecked = checked,
        onCheckChanged = { nowChecked ->
          onCheckChanged(boardForSelection.boardDescriptor, nowChecked)
        }
      )
    }
  }

  @Composable
  private fun FoundBoardsInfo(
    modifier: Modifier,
    currentSearchQuery: String,
    boardsForSelectionCount: Int,
    totalBoardsCount: Int,
    totalMatchedBySearchQueryCount: Int,
    maxDisplayedBoards: Int
  ) {
    val chanTheme = LocalChanTheme.current

    val text = remember(key1 = boardsForSelectionCount, key2 = totalBoardsCount, key3 = currentSearchQuery) {
      buildString {
        if (currentSearchQuery.isEmpty()) {
          if (totalBoardsCount > maxDisplayedBoards) {
            append(appResources.string(
              R.string.add_boards_controller_found_boards_displaying_out_of,
              boardsForSelectionCount,
              totalBoardsCount
            ))
          } else {
            append(appResources.string(
              R.string.add_boards_controller_found_boards_displaying,
              boardsForSelectionCount
            ))
          }
        } else {
          if (totalMatchedBySearchQueryCount > maxDisplayedBoards) {
            append(appResources.string(
              R.string.add_boards_controller_found_boards_displaying_out_of_by_query,
              boardsForSelectionCount,
              totalMatchedBySearchQueryCount
            ))
          } else {
            append(appResources.string(
              R.string.add_boards_controller_found_boards_displaying_by_query,
              boardsForSelectionCount
            ))
          }
        }
      }
    }

    Box(
      modifier = modifier,
      contentAlignment = Alignment.Center
    ) {
      KurobaComposeText(
        modifier = Modifier.padding(vertical = 8.dp),
        text = text,
        fontSize = 14.ktu,
        color = chanTheme.textColorHintCompose
      )
    }
  }

  private fun closeScreen() {
    refreshBoardsFunc()
    requireNavController().popController()
  }

}
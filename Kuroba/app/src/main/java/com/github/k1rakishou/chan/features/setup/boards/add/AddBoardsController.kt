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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.compose.AsyncData
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
import com.github.k1rakishou.chan.ui.compose.scaffold.LazyListScaffoldShared
import com.github.k1rakishou.chan.ui.compose.scaffold.NormalLazyListScaffoldBuilder
import com.github.k1rakishou.chan.ui.compose.snackbar.SnackbarContainer
import com.github.k1rakishou.chan.ui.compose.snackbar.SnackbarScope
import com.github.k1rakishou.chan.ui.controller.base.BaseComposeController
import com.github.k1rakishou.chan.ui.controller.base.DeprecatedNavigationFlags
import com.github.k1rakishou.chan.utils.ComposeAnnotatedStringHelper
import com.github.k1rakishou.chan.utils.ComposeAnnotatedStringHelperImpl
import com.github.k1rakishou.chan.utils.ViewModelScope
import com.github.k1rakishou.core_themes.ThemeEngine
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

  override val snackbarScope: SnackbarScope
    get() = SnackbarScope.Album(mainLayoutAnchor = SnackbarScope.MainLayoutAnchor.Catalog)

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
        title = ToolbarText.String(formatToolbarTitle()),
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

      val uiStateMut by controllerViewModel.uiState
      val uiState = uiStateMut

      val boardsForSelection = controllerViewModel.boardsForSelection
      val checkedBoards = controllerViewModel.checkedBoards
      val currentSearchQuery by controllerViewModel.currentSearchQuery
      val nonActiveBoardsCount by controllerViewModel.nonActiveBoardsCount

      val lazyListState = rememberLazyListState()
      val searchQueryState = rememberTextFieldState()

      val maxDisplayedBoards = AddBoardsControllerViewModel.MAX_DISPLAYED_BOARDS

      LaunchedEffect(key1 = Unit) {
        searchQueryState.forEachTextValue { text ->
          controllerViewModel.onSearchQueryUpdated(text.toString())
        }
      }

      LaunchedEffect(key1 = Unit) {
        controllerViewModel.resetScrollEventFlow
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
            lazyListState = lazyListState,
            controllerKey = controllerKey,
            header = {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .background(chanTheme.backColorSecondaryCompose)
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
                  AsyncData.NotInitialized -> {
                    return@LazyColumnWithFastScroller
                  }
                  AsyncData.Loading -> {
                    item(key = "progress_indicator") {
                      KurobaComposeProgressIndicator(
                        modifier = Modifier.fillParentMaxSize()
                      )
                    }

                    return@LazyColumnWithFastScroller
                  }
                  is AsyncData.Error -> {
                    item(key = "error_message") {
                      KurobaComposeErrorMessage(
                        modifier = Modifier.fillParentMaxSize(),
                        error = uiState.throwable
                      )
                    }

                    return@LazyColumnWithFastScroller
                  }
                  is AsyncData.Data<*> -> {
                    // no-op
                  }
                }

                if (boardsForSelection.isEmpty()) {
                  item(key = "nothing_found") {
                    KurobaComposeMessage(
                      modifier = Modifier.fillParentMaxSize(),
                      message = "Nothing found by query '${currentSearchQuery}'"
                    )
                  }

                  return@LazyColumnWithFastScroller
                }

                items(
                  count = boardsForSelection.size,
                  key = { idx -> boardsForSelection[idx].composeKey() },
                  itemContent = { idx ->
                    val boardForSelection = boardsForSelection[idx]
                    BoardForSelectionElement(
                      index = idx,
                      currentSearchQuery = currentSearchQuery,
                      checked = boardForSelection.boardDescriptor in checkedBoards,
                      boardForSelection = boardForSelection,
                      onCheckChanged = { boardDescriptor, check ->
                        controllerViewModel.onBoardCheckStateChanged(boardDescriptor, check)
                      }
                    )
                  }
                )

                if (boardsForSelection.size >= maxDisplayedBoards && nonActiveBoardsCount > 0) {
                  item(key = "too_many_boards_found") {
                    TooManyBoardsFound(
                      modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                      boardsForSelectionCount = boardsForSelection.size,
                      nonActiveBoardsCount = nonActiveBoardsCount
                    )
                  }
                }
              }
            },
            footer = { bottomPadding ->
              val toggleAllButton = LazyListScaffoldShared.Button(
                // TODO: strings
                text = "Toggle all",
                fontSize = 18.ktu,
                onClick = {
                  controllerViewModel.toggleAll()
                }
              )

              val positiveButton = if (checkedBoards.isEmpty()) {
                null
              } else {
                LazyListScaffoldShared.Button(
                  // TODO: strings
                  text = "Add (${checkedBoards.size}) boards",
                  fontSize = 18.ktu,
                  onClick = {
                    if (checkedBoards.size < maxDisplayedBoards) {
                      controllerViewModel.activateCheckedBoards(
                        onDone = { closeScreen() }
                      )
                      return@Button
                    }

                    dialogFactory.createSimpleConfirmationDialog(
                      context = context,
                      // TODO: Strings
                      titleText = "Adding ${checkedBoards.size} boards",
                      // TODO: Strings
                      descriptionText = "You are about to add ${checkedBoards.size} boards, which might not be a good idea. Are you sure?",
                      negativeButtonText = appResources.string(R.string.no),
                      positiveButtonText = appResources.string(R.string.yes),
                      onPositiveButtonClickListener = {
                        controllerViewModel.activateCheckedBoards(
                          onDone = { closeScreen() }
                        )
                      }
                    )
                  }
                )
              }

              Column(
                modifier = Modifier
                  .background(chanTheme.backColorSecondaryCompose)
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
    checked: Boolean,
    boardForSelection: AddBoardsControllerViewModel.BoardForSelection,
    onCheckChanged: (BoardDescriptor, Boolean) -> Unit
  ) {
    val chanTheme = LocalChanTheme.current

    val index = remember {
      buildAnnotatedString {
        pushStyle(SpanStyle(color = chanTheme.textColorSecondaryCompose))
        append("#${index + 1}")
      }
    }

    val title = remember(key1 = currentSearchQuery, key2 = boardForSelection.boardName) {
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
          onClick = {
            onCheckChanged(boardForSelection.boardDescriptor, !checked)
          }
        )
        .drawBehind {
          if (checked) {
            drawRect(chanTheme.postHighlightedColorCompose)
          }
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
      }

      KurobaComposeCheckbox(
        modifier = Modifier
          .wrapContentSize()
          .padding(horizontal = 16.dp, vertical = 8.dp),
        currentlyChecked = checked,
        onCheckChanged = { nowChecked ->
          onCheckChanged(boardForSelection.boardDescriptor, nowChecked)
        }
      )
    }
  }

  @Composable
  private fun TooManyBoardsFound(
    modifier: Modifier,
    boardsForSelectionCount: Int,
    nonActiveBoardsCount: Int
  ) {
    val chanTheme = LocalChanTheme.current

    Box(
      modifier = modifier,
      contentAlignment = Alignment.Center
    ) {
      KurobaComposeText(
        modifier = Modifier.padding(vertical = 8.dp),
        // TODO: strings
        text = "Only displaying ${boardsForSelectionCount} boards out of ${nonActiveBoardsCount}",
        fontSize = 14.ktu,
        color = chanTheme.textColorHintCompose
      )
    }
  }

  private fun formatToolbarTitle(
    displayed: Int = 0,
    total: Int = 0
  ): String {
    // TODO: strings
    return buildString {
      append("Select boards")

      if (displayed > 0 && total > 0) {
        append("(")
        append(displayed)
        append("/")
        append(total)
        append(")")
      }
    }
  }

  private fun closeScreen() {
    refreshBoardsFunc()
    requireNavController().popController()
  }

}
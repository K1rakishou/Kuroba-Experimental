package com.github.k1rakishou.chan.features.setup.boards.selection

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.controller.ControllerComponent
import com.github.k1rakishou.chan.features.toolbar.BackArrowMenuItem
import com.github.k1rakishou.chan.features.toolbar.ToolbarMiddleContent
import com.github.k1rakishou.chan.features.toolbar.ToolbarText
import com.github.k1rakishou.chan.ui.compose.SiteIconElement
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeDivider
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeMessage
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeTextBarButton
import com.github.k1rakishou.chan.ui.compose.components.KurobaSearchInput
import com.github.k1rakishou.chan.ui.compose.components.kurobaClickable
import com.github.k1rakishou.chan.ui.compose.copy
import com.github.k1rakishou.chan.ui.compose.forEachTextValue
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.chan.ui.compose.lazylist.LazyVerticalGridWithFastScroller
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.providers.LocalWindowSizeClass
import com.github.k1rakishou.chan.ui.compose.scaffold.NormalLazyListScaffoldBuilder
import com.github.k1rakishou.chan.ui.compose.snackbar.SnackbarScope
import com.github.k1rakishou.chan.ui.compose.window.KurobaWindowWidthSizeClass
import com.github.k1rakishou.chan.ui.controller.base.BaseComposeController
import com.github.k1rakishou.chan.ui.controller.base.DeprecatedNavigationFlags
import com.github.k1rakishou.chan.utils.ComposeAnnotatedStringHelper
import com.github.k1rakishou.chan.utils.ComposeAnnotatedStringHelperImpl
import com.github.k1rakishou.chan.utils.ViewModelScope
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.core_themes.resolveTextColor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import kotlin.math.roundToInt

class BoardSelectionController(
  context: Context,
  private val callback: UserSelectionListener
) : BaseComposeController<BoardSelectionControllerViewModel, BoardSelectionControllerParams>(
  context = context,
  viewModelClass = BoardSelectionControllerViewModel::class.java,
  viewModelParams = BoardSelectionControllerParams()
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
        title = ToolbarText.String(appResources.string(R.string.controller_board_select_title))
      )
    )
  }

  @Composable
  override fun ScreenContent() {
    val density = LocalDensity.current
    val windowSizeClass = LocalWindowSizeClass.current
    val layoutDirection = LocalLayoutDirection.current

    val selectableBoardElements = viewModel.selectableElements
    val currentlySelectedCatalogDescriptor by viewModel.currentlySelectedCatalogDescriptor.collectAsState()
    val currentSearchQuery by viewModel.currentSearchQuery

    val searchQueryState = rememberTextFieldState()

    LaunchedEffect(key1 = Unit) {
      searchQueryState.forEachTextValue { text ->
        viewModel.onSearchQueryChanged(text.toString())
      }
    }

    BoxWithConstraints {
      val spanCount = with(density) {
        val cellWidth = when (windowSizeClass.widthSizeClass.asKuroba()) {
          KurobaWindowWidthSizeClass.Compact -> 64.dp
          KurobaWindowWidthSizeClass.Medium -> 72.dp
          KurobaWindowWidthSizeClass.Expanded -> 82.dp
        }

        val availableWidth = this@BoxWithConstraints.maxWidth.toPx()
        (availableWidth / cellWidth.toPx()).roundToInt().coerceIn(MIN_SPAN_COUNT, MAX_SPAN_COUNT)
      }

      with(NormalLazyListScaffoldBuilder()) {
        Content(
          boxScope = this@BoxWithConstraints,
          controllerKey = controllerKey,
          header = {
            SearchInputHeader(searchQueryState)
          },
          body = { paddingValues ->
            val lazyGridPaddings = remember(key1 = paddingValues, key2 = layoutDirection) {
              paddingValues
                .copy(layoutDirection = layoutDirection, start = 8.dp, end = 8.dp)
            }

            LazyVerticalGridWithFastScroller(
              modifier = Modifier.fillMaxSize(),
              columns = GridCells.Fixed(count = spanCount),
              draggableScrollbar = false,
              contentPadding = lazyGridPaddings,
              horizontalArrangement = Arrangement.spacedBy(space = 4.dp),
              verticalArrangement = Arrangement.spacedBy(space = 4.dp),
              content = {
                if (selectableBoardElements.isEmpty()) {
                  item(
                    key = "no_boards",
                    span = { GridItemSpan(maxLineSpan) }
                  ) {
                    KurobaComposeMessage(
                      modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(vertical = 32.dp),
                      message = stringResource(R.string.controller_board_select_no_boards)
                    )
                  }

                  return@LazyVerticalGridWithFastScroller
                }

                selectableBoardElements.forEach { selectableBoardElement ->
                  selectableBoardsSection(
                    spanCount = spanCount,
                    selectableBoardElement = selectableBoardElement,
                    currentlySelectedCatalogDescriptor = currentlySelectedCatalogDescriptor,
                    currentSearchQuery = currentSearchQuery
                  )
                }
              }
            )
          },
          footer = null
        )
      }
    }
  }

  private fun LazyGridScope.selectableBoardsSection(
    spanCount: Int,
    selectableBoardElement: BoardSelectionControllerViewModel.SelectableElement,
    currentlySelectedCatalogDescriptor: ChanDescriptor.ICatalogDescriptor?,
    currentSearchQuery: String
  ) {
    when (selectableBoardElement) {
      is BoardSelectionControllerViewModel.SelectableElement.SiteHeader -> {
        item(
          key = selectableBoardElement.siteDescriptor,
          span = { GridItemSpan(maxLineSpan) }
        ) {
          HeaderElement(selectableBoardElement)
        }
      }
      is BoardSelectionControllerViewModel.SelectableElement.Boards -> {
        val boards = selectableBoardElement.selectableBoards

        items(
          count = selectableBoardElement.selectableBoards.size,
          key = { index ->
            val selectableBoard = boards.getOrNull(index)
              ?: return@items "${selectableBoardElement.siteDescriptor}_null"

            return@items when (val descriptor = selectableBoard.catalogDescriptor) {
              is ChanDescriptor.CatalogDescriptor -> descriptor.boardDescriptor.userReadableString()
              is ChanDescriptor.CompositeCatalogDescriptor -> {
                "CompositeCatalog_${descriptor.userReadableString()}"
              }
            }
          },
          span = { index ->
            val selectableBoard = boards.getOrNull(index)

            val span = if (selectableBoard?.catalogDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
              spanCount / 2
            } else {
              1
            }

            return@items GridItemSpan(span)
          },
          itemContent = { index ->
            val selectableBoard = boards.getOrNull(index)
              ?: return@items

            SelectableBoardElement(
              selectableBoard = selectableBoard,
              isCurrentlySelectedCatalog =
                selectableBoard.catalogDescriptor == currentlySelectedCatalogDescriptor,
              searchQuery = currentSearchQuery
            )
          }
        )
      }
    }
  }

  @Composable
  private fun SearchInputHeader(searchQueryState: TextFieldState) {
    val chanTheme = LocalChanTheme.current

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(chanTheme.backColorCompose),
      verticalAlignment = Alignment.CenterVertically
    ) {
      val kurobaSearchInputColor = if (ThemeEngine.isDarkColor(chanTheme.backColorCompose)) {
        Color.White
      } else {
        Color.Black
      }

      KurobaSearchInput(
        modifier = Modifier
          .weight(1f)
          .wrapContentHeight()
          .padding(vertical = 8.dp),
        displayClearButton = true,
        color = kurobaSearchInputColor,
        searchQueryState = searchQueryState
      )

      Spacer(modifier = Modifier.width(16.dp))

      KurobaComposeTextBarButton(
        text = stringResource(R.string.controller_board_go_to_sites),
        onClick = { callback.onOpenSitesSettingsClicked() }
      )

      Spacer(modifier = Modifier.width(8.dp))
    }
  }

  @Composable
  private fun HeaderElement(
    siteHeader: BoardSelectionControllerViewModel.SelectableElement.SiteHeader
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .kurobaClickable(
          bounded = true,
          onClick = { callback.onSiteSelected(siteHeader.siteDescriptor) }
        )
        .padding(vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Spacer(modifier = Modifier.width(8.dp))
      Box(modifier = Modifier.size(42.dp)) {
        SiteIconElement(siteDescriptor = siteHeader.siteDescriptor)
      }
      Spacer(modifier = Modifier.width(8.dp))
      KurobaComposeText(text = siteHeader.name)
      Spacer(modifier = Modifier.width(8.dp))
      KurobaComposeDivider(modifier = Modifier.weight(1f))
    }
  }

  @Composable
  private fun SelectableBoardElement(
    selectableBoard: BoardSelectionControllerViewModel.SelectableBoard,
    isCurrentlySelectedCatalog: Boolean,
    searchQuery: String
  ) {
    val chanTheme = LocalChanTheme.current

    Box {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .fillMaxHeight()
          .drawBehind {
            if (isCurrentlySelectedCatalog) {
              drawRect(color = chanTheme.postHighlightedColorCompose)
            }
          }
          .kurobaClickable(
            bounded = true,
            onClick = {
              callback.onCatalogSelected(selectableBoard.catalogDescriptor)
              requireNavController().popController()
            }
          )
          .padding(all = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        run {
          val header = remember(
            selectableBoard.header,
            searchQuery,
            chanTheme.textColorPrimaryCompose,
            chanTheme.accentColorCompose
          ) {
            buildAnnotatedString {
              pushStyle(SpanStyle(color = chanTheme.textColorPrimaryCompose))
              append(selectableBoard.header)

              with(ComposeAnnotatedStringHelperImpl()) {
                val textMark = ComposeAnnotatedStringHelper.TextMark(
                  pattern = searchQuery,
                  backgroundColor = chanTheme.accentColorCompose,
                  textColor = ThemeEngine.resolveTextColor(chanTheme.accentColorCompose)
                )

                markText(
                  text = selectableBoard.header,
                  textMarks = listOf(textMark)
                )
              }
            }
          }

          KurobaComposeText(
            text = header,
            fontSize = 14.ktu,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = chanTheme.textColorPrimaryCompose,
            textAlign = TextAlign.Center
          )
        }

        if (selectableBoard.description.isNotNullNorBlank()) {
          val description = remember(
            selectableBoard.description,
            searchQuery,
            chanTheme.textColorSecondaryCompose,
            chanTheme.accentColorCompose
          ) {
            buildAnnotatedString {
              pushStyle(SpanStyle(color = chanTheme.textColorSecondaryCompose))
              pushStyle(ParagraphStyle(lineBreak = LineBreak.Paragraph, hyphens = Hyphens.Auto))
              append(selectableBoard.description)

              with(ComposeAnnotatedStringHelperImpl()) {
                val textMark = ComposeAnnotatedStringHelper.TextMark(
                  pattern = searchQuery,
                  backgroundColor = chanTheme.accentColorCompose,
                  textColor = ThemeEngine.resolveTextColor(chanTheme.accentColorCompose)
                )

                markText(
                  text = selectableBoard.description,
                  textMarks = listOf(textMark)
                )
              }
            }
          }

          KurobaComposeText(
            text = description,
            fontSize = 11.ktu,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
          )
        }
      }

      if (selectableBoard.safeForWork == false) {
        KurobaComposeText(
          modifier = Modifier
            .offset { IntOffset(x = 8.dp.roundToPx(), y = (-4).dp.roundToPx()) }
            .background(color = chanTheme.accentColorCompose)
            .padding(horizontal = 2.dp, vertical = 1.dp)
            .align(Alignment.TopEnd),
          text = stringResource(R.string.controller_board_nsfw_board_tag),
          color = chanTheme.accentColorCompose.resolveTextColor(),
          fontSize = 8.ktu.fixedSize()
        )
      }
    }
  }

  interface UserSelectionListener {
    fun onOpenSitesSettingsClicked()
    fun onSiteSelected(siteDescriptor: SiteDescriptor)
    fun onCatalogSelected(catalogDescriptor: ChanDescriptor.ICatalogDescriptor)
  }

  companion object {
    private const val MIN_SPAN_COUNT = 2
    private const val MAX_SPAN_COUNT = 10
  }

}
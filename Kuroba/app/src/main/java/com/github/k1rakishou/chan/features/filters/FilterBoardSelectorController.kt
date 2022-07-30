package com.github.k1rakishou.chan.features.filters

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.ui.compose.ComposeHelpers.consumeClicks
import com.github.k1rakishou.chan.ui.compose.ComposeHelpers.simpleVerticalScrollbar
import com.github.k1rakishou.chan.ui.compose.ImageLoaderRequest
import com.github.k1rakishou.chan.ui.compose.ImageLoaderRequestData
import com.github.k1rakishou.chan.ui.compose.KurobaComposeCardView
import com.github.k1rakishou.chan.ui.compose.KurobaComposeImage
import com.github.k1rakishou.chan.ui.compose.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.KurobaComposeTextBarButton
import com.github.k1rakishou.chan.ui.compose.KurobaSearchInput
import com.github.k1rakishou.chan.ui.compose.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.kurobaClickable
import com.github.k1rakishou.chan.ui.compose.search.SimpleSearchState
import com.github.k1rakishou.chan.ui.compose.search.rememberSimpleSearchState
import com.github.k1rakishou.chan.ui.controller.BaseFloatingComposeController
import com.github.k1rakishou.chan.utils.InputWithQuerySorter
import com.github.k1rakishou.chan.utils.viewModelByKey
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject

class FilterBoardSelectorController(
  context: Context,
  private val currentlySelectedBoards: Set<BoardDescriptor>?,
  private val onBoardsSelected: (SelectedBoards) -> Unit
) : BaseFloatingComposeController(context) {

  @Inject
  lateinit var imageLoaderV2: ImageLoaderV2

  private val viewModel by lazy {
    requireComponentActivity().viewModelByKey<FilterBoardSelectorControllerViewModel>()
  }

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    viewModel.reload(currentlySelectedBoards)
  }

  @Composable
  override fun BoxScope.BuildContent() {
    val chanTheme = LocalChanTheme.current
    val backgroundColor = chanTheme.backColorCompose
    val searchState = rememberSimpleSearchState<FilterBoardSelectorControllerViewModel.CellData>()

    Column(
      modifier = Modifier
        .widthIn(max = 600.dp)
        .wrapContentHeight()
        .consumeClicks()
        .align(Alignment.Center)
        .background(backgroundColor)
    ) {
      BuildContentInternal(searchState, chanTheme, backgroundColor)

      Row(modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
      ) {
        KurobaComposeTextBarButton(
          modifier = Modifier
            .wrapContentSize()
            .padding(horizontal = 8.dp, vertical = 4.dp),
          onClick = { viewModel.toggleSelectUnselectAll(searchState.results) },
          text = stringResource(id = R.string.filter_toggle_select_unselect_all_boards)
        )

        Spacer(modifier = Modifier.weight(1f))

        KurobaComposeTextBarButton(
          modifier = Modifier
            .wrapContentSize()
            .padding(horizontal = 8.dp, vertical = 4.dp),
          onClick = { pop() },
          text = stringResource(id = R.string.close)
        )

        val currentlySelectedBoards = remember { viewModel.currentlySelectedBoards }

        KurobaComposeTextBarButton(
          modifier = Modifier
            .wrapContentSize()
            .padding(horizontal = 8.dp, vertical = 4.dp),
          enabled = currentlySelectedBoards.isNotEmpty(),
          onClick = {
            if (viewModel.currentlySelectedBoards.isEmpty()) {
              return@KurobaComposeTextBarButton
            }

            val selectedBoards = if (viewModel.currentlySelectedBoards.size == viewModel.cellDataList.size) {
              SelectedBoards.AllBoards
            } else {
              SelectedBoards.Boards(viewModel.currentlySelectedBoards.keys)
            }

            onBoardsSelected(selectedBoards)
            pop()
          },
          text = stringResource(id = R.string.filter_select_n_boards, currentlySelectedBoards.size)
        )
      }
    }
  }

  @Composable
  private fun ColumnScope.BuildContentInternal(
    searchState: SimpleSearchState<FilterBoardSelectorControllerViewModel.CellData>,
    chanTheme: ChanTheme,
    backgroundColor: Color
  ) {
    val cellDataList = remember { viewModel.cellDataList }
    val listState = rememberLazyGridState()

    BuildSearchInput(
      backgroundColor = backgroundColor,
      searchQuery = searchState.queryState,
      onSearchQueryChanged = { newQuery -> searchState.query = newQuery }
    )

    LaunchedEffect(key1 = searchState.query, block = {
      if (searchState.query.isEmpty()) {
        searchState.results = cellDataList
        return@LaunchedEffect
      }

      delay(125L)

      withContext(Dispatchers.Default) {
        searchState.searching = true
        searchState.results = processSearchQuery(searchState.query, cellDataList)
        searchState.searching = false
      }
    })

    val searchQuery = searchState.query
    val searching = searchState.searching
    val searchResults = if (searching) {
      cellDataList
    } else {
      searchState.results
    }

    if (cellDataList.isEmpty()) {
      KurobaComposeText(
        modifier = Modifier
          .height(256.dp)
          .fillMaxWidth()
          .padding(8.dp),
        textAlign = TextAlign.Center,
        text = stringResource(id = R.string.search_nothing_to_display_make_sure_sites_boards_active)
      )
      return
    }

    if (searchResults.isEmpty()) {
      KurobaComposeText(
        modifier = Modifier
          .height(256.dp)
          .fillMaxWidth()
          .padding(8.dp),
        textAlign = TextAlign.Center,
        text = stringResource(id = R.string.search_nothing_found_with_query, searchQuery)
      )
      return
    }

    LazyVerticalGrid(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .defaultMinSize(minHeight = 256.dp)
        .simpleVerticalScrollbar(state = listState, chanTheme = chanTheme),
      state = listState,
      columns = GridCells.Adaptive(CELL_WIDTH),
      content = {
        items(searchResults.size) { index ->
          val cellData = searchResults[index]

          BuildBoardCell(
            chanTheme = chanTheme,
            cellData = cellData,
            onCellClicked = { clickedCellData ->
              viewModel.onBoardSelectionToggled(clickedCellData.catalogCellData.boardDescriptorOrNull!!)
            }
          )
        }
      }
    )
  }

  private fun processSearchQuery(
    inputSearchQuery: String,
    cellDataList: List<FilterBoardSelectorControllerViewModel.CellData>
  ): List<FilterBoardSelectorControllerViewModel.CellData> {
    if (inputSearchQuery.isEmpty() || inputSearchQuery.isBlank()) {
      return cellDataList
    }

    val splitSearchQuery = inputSearchQuery.split(" ")
    if (splitSearchQuery.isEmpty()) {
      return emptyList()
    }

    val filteredCellDataList = cellDataList.filter { entry ->
      return@filter splitSearchQuery.any { searchQuery ->
        return@any entry.catalogCellData.boardCodeFormatted.contains(searchQuery, ignoreCase = true)
          || entry.siteCellData.siteName.contains(searchQuery, ignoreCase = true)
      }
    }

    if (splitSearchQuery.isEmpty() || splitSearchQuery.size > 1) {
      return filteredCellDataList
    }

    return InputWithQuerySorter.sort(
      input = filteredCellDataList,
      query = splitSearchQuery.first(),
      textSelector = { cellData -> cellData.catalogCellData.boardCodeFormatted }
    )
  }

  @Composable
  private fun BuildSearchInput(
    backgroundColor: Color,
    searchQuery: MutableState<String>,
    onSearchQueryChanged: (String) -> Unit
  ) {
    val chanTheme = LocalChanTheme.current
    val onSearchQueryChangedRemembered = rememberUpdatedState(newValue = onSearchQueryChanged)

    KurobaSearchInput(
      modifier = Modifier
        .wrapContentHeight()
        .fillMaxWidth()
        .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
      chanTheme = chanTheme,
      onBackgroundColor = backgroundColor,
      searchQueryState = searchQuery,
      labelText = stringResource(id = R.string.search_hint),
      onSearchQueryChanged = { newQuery -> onSearchQueryChangedRemembered.value.invoke(newQuery) }
    )
  }

  @Composable
  private fun BuildBoardCell(
    chanTheme: ChanTheme,
    cellData: FilterBoardSelectorControllerViewModel.CellData,
    onCellClicked: (FilterBoardSelectorControllerViewModel.CellData) -> Unit
  ) {
    val onCellClickedRemembered = rememberUpdatedState(newValue = onCellClicked)
    val currentlySelectedBoards = remember { viewModel.currentlySelectedBoards }
    val boardDescriptor = cellData.catalogCellData.boardDescriptorOrNull!!
    val currentlyChecked = currentlySelectedBoards.containsKey(boardDescriptor)

    val cardBgColor = remember(key1 = currentlyChecked, key2 = chanTheme) {
      if (!currentlyChecked) {
        return@remember chanTheme.backColorSecondaryCompose
      }

      return@remember chanTheme.postHighlightedColorCompose
    }

    KurobaComposeCardView(
      modifier = Modifier
        .height(CELL_HEIGHT)
        .width(CELL_WIDTH)
        .padding(4.dp)
        .kurobaClickable(
          bounded = true,
          onClick = { onCellClickedRemembered.value.invoke(cellData) }
        ),
      backgroundColor = cardBgColor
    ) {
      Column(
        modifier = Modifier.fillMaxSize()
      ) {
        val siteIcon = cellData.siteCellData.siteIcon
        val siteIconModifier = Modifier
          .fillMaxWidth()
          .height(ICON_SIZE)
          .padding(4.dp)

        if (siteIcon.url != null) {
          val data = ImageLoaderRequestData.Url(
            httpUrl = siteIcon.url!!,
            cacheFileType = CacheFileType.SiteIcon
          )

          val request = ImageLoaderRequest(data)

          KurobaComposeImage(
            modifier = siteIconModifier,
            request = request,
            imageLoaderV2 = imageLoaderV2,
            error = {
              Image(
                modifier = Modifier.fillMaxSize(),
                painter = painterResource(id = R.drawable.error_icon),
                contentDescription = null
              )
            }
          )
        } else if (siteIcon.drawable != null) {
          val bitmapPainter = remember {
            BitmapPainter(siteIcon.drawable!!.bitmap.asImageBitmap())
          }

          Image(
            modifier = siteIconModifier,
            painter = bitmapPainter,
            contentDescription = null
          )
        }

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
        ) {
          Column(
            modifier = Modifier
              .wrapContentSize()
              .align(Alignment.Center)
          ) {
            KurobaComposeText(
              fontSize = 11.sp,
              maxLines = 3,
              overflow = TextOverflow.Ellipsis,
              textAlign = TextAlign.Center,
              modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
              text = cellData.siteCellData.siteName
            )

            KurobaComposeText(
              fontSize = 11.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              textAlign = TextAlign.Center,
              modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
              text = cellData.catalogCellData.boardCodeFormatted
            )
          }
        }
      }
    }
  }

  sealed class SelectedBoards {
    object AllBoards : SelectedBoards()
    data class Boards(val boardDescriptors: Set<BoardDescriptor>) : SelectedBoards()
  }

  companion object {
    private val CELL_HEIGHT = 92.dp
    private val CELL_WIDTH = 72.dp
    private val ICON_SIZE = 24.dp
  }
}
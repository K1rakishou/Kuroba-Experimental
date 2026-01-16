package com.github.k1rakishou.chan.features.filters

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.image.ImageLoaderDeprecated
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeCard
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeTextBarButton
import com.github.k1rakishou.chan.ui.compose.components.KurobaSearchInput
import com.github.k1rakishou.chan.ui.compose.components.kurobaClickable
import com.github.k1rakishou.chan.ui.compose.consumeClicks
import com.github.k1rakishou.chan.ui.compose.image.ImageLoaderRequest
import com.github.k1rakishou.chan.ui.compose.image.ImageLoaderRequestData
import com.github.k1rakishou.chan.ui.compose.image.KurobaComposeImage
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.chan.ui.compose.lazylist.LazyVerticalGridWithFastScroller
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.search.SimpleSearchStateV2
import com.github.k1rakishou.chan.ui.compose.search.rememberSimpleSearchStateV2
import com.github.k1rakishou.chan.ui.compose.textAsFlow
import com.github.k1rakishou.chan.ui.controller.BaseFloatingComposeController
import com.github.k1rakishou.chan.utils.InputWithQuerySorter
import com.github.k1rakishou.chan.utils.viewModelByKey
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import javax.inject.Inject

class FilterBoardSelectorController(
  context: Context,
  private val currentlySelectedBoards: Set<BoardDescriptor>?,
  private val onBoardsSelected: (SelectedBoards) -> Unit
) : BaseFloatingComposeController(context) {

  @Inject
  lateinit var imageLoaderDeprecated: ImageLoaderDeprecated

  private val viewModel by viewModelByKey<FilterBoardSelectorControllerViewModel>()

  override fun injectActivityDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    viewModel.reload(currentlySelectedBoards)
  }

  @Composable
  override fun BoxScope.BuildContent() {
    val chanTheme = LocalChanTheme.current

    val searchState = rememberSimpleSearchStateV2<FilterBoardSelectorControllerViewModel.CellData>(
      textFieldState = viewModel.searchTextFieldState
    )

    Column(
      modifier = Modifier
        .widthIn(max = 600.dp)
        .wrapContentHeight()
        .consumeClicks()
        .align(Alignment.Center)
        .background(chanTheme.backColorCompose)
    ) {
      val cellDataList = viewModel.cellDataList
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

      val chanTheme = LocalChanTheme.current
      val listState = rememberLazyGridState()

      val kurobaSearchInputColor = if (ThemeEngine.isDarkColor(chanTheme.backColor)) {
        Color.LightGray
      } else {
        Color.DarkGray
      }

      KurobaSearchInput(
        modifier = Modifier
          .wrapContentHeight()
          .fillMaxWidth()
          .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        color = kurobaSearchInputColor,
        searchQueryState = searchState.textFieldState
      )

      LaunchedEffect(
        key1 = searchState,
        key2 = cellDataList,
        block = {
          searchState.textFieldState.textAsFlow()
            .onEach { query ->
              delay(125)

              if (query.isEmpty()) {
                searchState.results.value = cellDataList
                return@onEach
              }

              withContext(Dispatchers.Default) {
                searchState.results.value = processSearchQuery(searchState.searchQuery, cellDataList)
              }
            }
            .collect()
        }
      )

      val searchResults by searchState.results
      if (searchResults.isEmpty()) {
        KurobaComposeText(
          modifier = Modifier
            .height(256.dp)
            .fillMaxWidth()
            .padding(8.dp),
          textAlign = TextAlign.Center,
          text = stringResource(id = R.string.search_nothing_found_with_query, searchState.searchQuery)
        )

        return
      }

      Box {
        var lazyGridPaddings by remember { mutableStateOf(PaddingValues.Zero) }

        LazyVerticalGridWithFastScroller(
          modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .defaultMinSize(minHeight = 256.dp),
          state = listState,
          columns = GridCells.Adaptive(CELL_WIDTH),
          draggableScrollbar = false,
          contentPadding = lazyGridPaddings,
          content = {
            items(
              count = searchResults.size,
              key = { index -> searchResults[index].catalogCellData.catalogDescriptor }
            ) { index ->
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

        BottomButtons(
          searchState = searchState,
          onSizeChanged = { heightInDp -> lazyGridPaddings = PaddingValues(bottom = heightInDp) }
        )
      }
    }
  }

  @Composable
  private fun BoxScope.BottomButtons(
    searchState: SimpleSearchStateV2<FilterBoardSelectorControllerViewModel.CellData>,
    onSizeChanged: (Dp) -> Unit
  ) {
    val chanTheme = LocalChanTheme.current
    val density = LocalDensity.current

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .background(chanTheme.backColorCompose)
        .align(Alignment.BottomCenter)
        .onSizeChanged { intSize ->
          val heightInDp = with(density) { intSize.height.toDp() }
          onSizeChanged(heightInDp)
        }
        .padding(vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      KurobaComposeTextBarButton(
        modifier = Modifier
          .wrapContentSize()
          .padding(horizontal = 8.dp, vertical = 4.dp),
        onClick = { viewModel.toggleSelectUnselectAll(searchState.results.value) },
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

  private fun processSearchQuery(
    inputSearchQuery: CharSequence,
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

    KurobaComposeCard(
      modifier = Modifier
        .size(CELL_HEIGHT)
        .width(CELL_WIDTH)
        .padding(4.dp)
        .kurobaClickable(
          bounded = true,
          onClick = { onCellClickedRemembered.value.invoke(cellData) }
        ),
      backgroundColor = cardBgColor
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize(),
        verticalArrangement = Arrangement.Center
      ) {
        val siteIcon = cellData.siteCellData.siteIcon
        val siteIconModifier = Modifier
          .fillMaxWidth()
          .height(ICON_SIZE)

        if (siteIcon.url != null) {
          val request = remember(key1 = siteIcon.url) {
            return@remember ImageLoaderRequest(
              data = ImageLoaderRequestData.Url(
                httpUrl = siteIcon.url!!,
                cacheFileType = CacheFileType.SiteIcon
              )
            )
          }

          KurobaComposeImage(
            modifier = siteIconModifier,
            request = request,
            controllerKey = null,
            contentScale = ContentScale.Fit
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

        Spacer(modifier = Modifier.height(4.dp))

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
        ) {
          Column(
            modifier = Modifier
              .wrapContentSize()
              .align(Alignment.Center)
          ) {
            KurobaComposeText(
              modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
              fontSize = 10.ktu,
              maxLines = 3,
              overflow = TextOverflow.Ellipsis,
              textAlign = TextAlign.Center,
              text = cellData.siteCellData.siteName
            )

            Spacer(modifier = Modifier.height(2.dp))

            KurobaComposeText(
              modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
              fontSize = 10.ktu,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              textAlign = TextAlign.Center,
              text = cellData.catalogCellData.boardCodeFormatted
            )
          }
        }
      }
    }
  }

  sealed class SelectedBoards {
    data object AllBoards : SelectedBoards()
    data class Boards(val boardDescriptors: Set<BoardDescriptor>) : SelectedBoards()
  }

  companion object {
    private val CELL_HEIGHT = 92.dp
    private val CELL_WIDTH = 72.dp
    private val ICON_SIZE = 24.dp
  }
}
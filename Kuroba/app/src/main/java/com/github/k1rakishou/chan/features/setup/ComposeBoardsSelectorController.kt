package com.github.k1rakishou.chan.features.setup

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.ui.compose.ComposeHelpers.consumeClicks
import com.github.k1rakishou.chan.ui.compose.ComposeHelpers.simpleVerticalScrollbar
import com.github.k1rakishou.chan.ui.compose.ImageLoaderRequest
import com.github.k1rakishou.chan.ui.compose.ImageLoaderRequestData
import com.github.k1rakishou.chan.ui.compose.KurobaComposeCardView
import com.github.k1rakishou.chan.ui.compose.KurobaComposeImage
import com.github.k1rakishou.chan.ui.compose.KurobaComposeProgressIndicator
import com.github.k1rakishou.chan.ui.compose.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.KurobaComposeTextBarButton
import com.github.k1rakishou.chan.ui.compose.KurobaSearchInput
import com.github.k1rakishou.chan.ui.compose.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.kurobaClickable
import com.github.k1rakishou.chan.ui.controller.BaseFloatingComposeController
import com.github.k1rakishou.chan.utils.viewModelByKey
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ComposeBoardsSelectorController(
  context: Context,
  private val currentlyComposedBoards: Set<BoardDescriptor>,
  private val onBoardSelected: (BoardDescriptor) -> Unit
) : BaseFloatingComposeController(context) {

  @Inject
  lateinit var imageLoaderV2: ImageLoaderV2

  private val viewModel by lazy {
    requireComponentActivity().viewModelByKey<ComposeBoardsSelectorControllerViewModel>()
  }

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    viewModel.reload(currentlyComposedBoards)
  }

  @Composable
  override fun BoxScope.BuildContent() {
    val chanTheme = LocalChanTheme.current
    val backgroundColor = chanTheme.backColorCompose

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .consumeClicks()
        .align(Alignment.Center)
        .background(backgroundColor)
    ) {
      BuildContentInternal(chanTheme, backgroundColor)

      KurobaComposeTextBarButton(
        modifier = Modifier
          .wrapContentSize()
          .align(Alignment.End)
          .padding(horizontal = 8.dp, vertical = 4.dp),
        onClick = { pop() },
        text = stringResource(id = R.string.close)
      )
    }
  }

  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  private fun ColumnScope.BuildContentInternal(chanTheme: ChanTheme, backgroundColor: Color) {
    val searchState = remember { SearchState() }
    val cellDataList = remember { viewModel.cellDataList }
    val listState = rememberLazyListState()

    BuildSearchInput(
      backgroundColor = backgroundColor,
      initialQuery = searchState.searchQuery,
      onSearchQueryChanged = { newQuery -> searchState.searchQuery = newQuery }
    )

    LaunchedEffect(key1 = searchState.searchQuery, block = {
      delay(125L)

      withContext(Dispatchers.Default) {
        searchState.searching = true
        searchState.searchResults = processSearchQuery(searchState.searchQuery, cellDataList)
        searchState.searching = false
      }
    })

    val searchQuery = searchState.searchQuery
    val searching = searchState.searching
    val searchResults = searchState.searchResults

    if (searching) {
      KurobaComposeProgressIndicator(
        modifier = Modifier
          .height(256.dp)
          .fillMaxWidth()
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
      cells = GridCells.Adaptive(CELL_SIZE),
      content = {
        items(searchResults.size) { index ->
          val cellData = searchResults[index]

          BuildBoardCell(
            chanTheme = chanTheme,
            cellData = cellData,
            onCellClicked = { clickedCellData ->
              onBoardSelected(clickedCellData.catalogCellData.boardDescriptorOrNull!!)
              pop()
            }
          )
        }
      }
    )
  }

  private fun processSearchQuery(
    searchQuery: String,
    cellDataList: List<ComposeBoardsSelectorControllerViewModel.CellData>
  ): List<ComposeBoardsSelectorControllerViewModel.CellData> {
    if (searchQuery.isEmpty()) {
      return cellDataList
    }

    return cellDataList.filter { navigationHistoryEntry ->
      return@filter navigationHistoryEntry.formattedSiteAndBoardFullNameForSearch
        .contains(other = searchQuery, ignoreCase = true)
    }
  }

  @Composable
  private fun BuildSearchInput(
    backgroundColor: Color,
    initialQuery: String,
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
      themeEngine = themeEngine,
      backgroundColor = backgroundColor,
      searchQuery = initialQuery,
      onSearchQueryChanged = { newQuery -> onSearchQueryChangedRemembered.value.invoke(newQuery) }
    )
  }

  @Composable
  private fun BuildBoardCell(
    chanTheme: ChanTheme,
    cellData: ComposeBoardsSelectorControllerViewModel.CellData,
    onCellClicked: (ComposeBoardsSelectorControllerViewModel.CellData) -> Unit
  ) {
    val onCellClickedRemembered = rememberUpdatedState(newValue = onCellClicked)

    KurobaComposeCardView(
      modifier = Modifier
        .size(CELL_SIZE)
        .padding(4.dp)
        .kurobaClickable(bounded = true, onClick = { onCellClickedRemembered.value.invoke(cellData) }),
      backgroundColor = chanTheme.backColorSecondaryCompose
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
          val request = ImageLoaderRequest(ImageLoaderRequestData.Url(siteIcon.url!!))

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
          Image(
            modifier = siteIconModifier,
            painter = BitmapPainter(siteIcon.drawable!!.bitmap.asImageBitmap()),
            contentDescription = null
          )
        }

        Row(modifier = Modifier.fillMaxSize()) {
          KurobaComposeText(
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
              .fillMaxWidth()
              .height(CELL_SIZE - ICON_SIZE)
              .padding(4.dp)
              .align(Alignment.CenterVertically),
            text = cellData.formattedSiteAndBoardFullNameForUi
          )
        }
      }
    }
  }

  class SearchState {
    var searchQuery by mutableStateOf("")
    var searching by mutableStateOf(false)
    var searchResults by mutableStateOf<List<ComposeBoardsSelectorControllerViewModel.CellData>>(emptyList())
  }

  companion object {
    private val CELL_SIZE = 72.dp
    private val ICON_SIZE = 24.dp
  }
}
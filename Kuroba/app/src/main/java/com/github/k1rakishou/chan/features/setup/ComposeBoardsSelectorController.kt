package com.github.k1rakishou.chan.features.setup

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.github.k1rakishou.chan.ui.compose.search.rememberSimpleSearchStateV2
import com.github.k1rakishou.chan.ui.compose.textAsFlow
import com.github.k1rakishou.chan.ui.controller.BaseFloatingComposeController
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

class ComposeBoardsSelectorController(
  context: Context,
  private val currentlyComposedBoards: Set<BoardDescriptor>,
  private val onBoardSelected: (BoardDescriptor) -> Unit
) : BaseFloatingComposeController(context) {

  @Inject
  lateinit var imageLoaderDeprecated: ImageLoaderDeprecated

  private val viewModel by viewModelByKey<ComposeBoardsSelectorControllerViewModel>()

  override fun injectActivityDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    viewModel.reload(currentlyComposedBoards)
  }

  @Composable
  override fun BoxScope.Content() {
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
      val focusManager = LocalFocusManager.current

      BuildContentInternal()

      KurobaComposeTextBarButton(
        modifier = Modifier
          .wrapContentSize()
          .align(Alignment.End)
          .padding(horizontal = 8.dp, vertical = 4.dp),
        onClick = {
          focusManager.clearFocus(force = true)
          pop()
        },
        text = stringResource(id = R.string.close)
      )
    }
  }

  @Composable
  private fun ColumnScope.BuildContentInternal() {
    val chanTheme = LocalChanTheme.current

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

    val searchState = rememberSimpleSearchStateV2<ComposeBoardsSelectorControllerViewModel.CellData>(
      textFieldState = viewModel.searchTextFieldState
    )
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
              searchState.results.value = processSearchQuery(query, cellDataList)
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

    LazyVerticalGridWithFastScroller(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .defaultMinSize(minHeight = 256.dp),
      state = listState,
      columns = GridCells.Adaptive(CELL_WIDTH),
      draggableScrollbar = false,
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
    searchQuery: CharSequence,
    cellDataList: List<ComposeBoardsSelectorControllerViewModel.CellData>
  ): List<ComposeBoardsSelectorControllerViewModel.CellData> {
    if (searchQuery.isEmpty()) {
      return cellDataList
    }

    return cellDataList.filter { navigationHistoryEntry ->
      if (navigationHistoryEntry.siteName.contains(other = searchQuery, ignoreCase = true)) {
        return@filter true
      }

      if (navigationHistoryEntry.boardCode.contains(other = searchQuery, ignoreCase = true)) {
        return@filter true
      }

      return@filter false
    }
  }

  @Composable
  private fun BuildBoardCell(
    chanTheme: ChanTheme,
    cellData: ComposeBoardsSelectorControllerViewModel.CellData,
    onCellClicked: (ComposeBoardsSelectorControllerViewModel.CellData) -> Unit
  ) {
    val onCellClickedRemembered = rememberUpdatedState(newValue = onCellClicked)

    KurobaComposeCard(
      modifier = Modifier
        .wrapContentHeight()
        .size(CELL_HEIGHT)
        .width(CELL_WIDTH)
        .padding(4.dp)
        .kurobaClickable(
          bounded = true,
          onClick = { onCellClickedRemembered.value.invoke(cellData) }
        ),
      backgroundColor = chanTheme.backColorSecondaryCompose
    ) {
      Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center
      ) {
        val siteIcon = cellData.siteCellData.siteIcon
        val siteIconModifier = Modifier
          .fillMaxWidth()
          .height(ICON_SIZE)

        if (siteIcon.url != null) {
          val data = ImageLoaderRequestData.Url(
            httpUrl = siteIcon.url!!,
            cacheFileType = CacheFileType.SiteIcon
          )

          val request = ImageLoaderRequest(data)

          KurobaComposeImage(
            modifier = siteIconModifier,
            controllerKey = null,
            request = request,
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

        Column(
          modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
        ) {
          KurobaComposeText(
            fontSize = 10.ktu,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
              .fillMaxWidth()
              .wrapContentHeight(),
            text = cellData.siteName
          )

          Spacer(modifier = Modifier.height(2.dp))

          KurobaComposeText(
            fontSize = 10.ktu,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
              .fillMaxWidth()
              .wrapContentHeight(),
            text = cellData.boardCode
          )
        }
      }
    }
  }

  companion object {
    private val CELL_HEIGHT = 92.dp
    private val CELL_WIDTH = 72.dp
    private val ICON_SIZE = 24.dp
  }
}
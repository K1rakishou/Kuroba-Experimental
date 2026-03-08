package com.github.k1rakishou.chan.features.search.posts

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.site.sites.search.SearchBoard
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeCard
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.components.KurobaSearchInput
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.chan.ui.compose.lazylist.LazyVerticalGridWithFastScroller
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.textAsFlow
import com.github.k1rakishou.chan.ui.controller.base.BaseFloatingComposeController
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.InputWithQuerySorter
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

class SelectBoardForSearchController(
  context: Context,
  private val siteDescriptor: SiteDescriptor,
  private val supportsAllBoardsSearch: Boolean,
  private val searchBoardProvider: () -> Collection<SearchBoard>,
  private val prevSelectedBoard: SearchBoard?,
  private val onBoardSelected: (SearchBoard) -> Unit
) : BaseFloatingComposeController(context) {

  @Inject
  lateinit var archivesManager: ArchivesManager

  private val oneCellSpan = GridItemSpan(1)
  private val twoCellsSpan = GridItemSpan(2)

  private val searchTextFieldState = TextFieldState()

  override fun injectActivityDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  @Composable
  override fun BoxScope.BuildContent() {
    val chanTheme = LocalChanTheme.current
    val isTablet = remember { AppModuleAndroidUtils.isTablet }
    val fraction = if (isTablet) {
      0.8f
    } else {
      0.9f
    }

    KurobaComposeCard(
      modifier = Modifier
        .wrapContentHeight()
        .fillMaxWidth(fraction = fraction)
        .align(Alignment.Center)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
      ) {
        val boardsSupportingSearch = remember { mutableStateListOf<SearchBoard>() }

        LaunchedEffect(key1 = searchTextFieldState) {
          searchTextFieldState.textAsFlow()
            .onEach { query ->
              val boards = collectBoardsSupportingSearch(query)

              boardsSupportingSearch.clear()
              boardsSupportingSearch.addAll(boards)
            }
            .collect()
        }

        val minSize = if (isTablet) {
          120.dp
        } else {
          86.dp
        }

        val listState = rememberLazyGridState()

        val kurobaSearchInputColor = if (ThemeEngine.isDarkColor(chanTheme.backColor)) {
          Color.LightGray
        } else {
          Color.DarkGray
        }

        LazyVerticalGridWithFastScroller(
          state = listState,
          columns = GridCells.Adaptive(minSize = minSize),
          draggableScrollbar = false,
          content = {
            item(
              span = { GridItemSpan(this.maxCurrentLineSpan) },
              content = {
                KurobaSearchInput(
                  modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                  color = kurobaSearchInputColor,
                  searchQueryState = searchTextFieldState
                )
              }
            )

            if (boardsSupportingSearch.isEmpty()) {
              item(
                span = { GridItemSpan(this.maxCurrentLineSpan) },
                content = {
                  val text = if (searchTextFieldState.text.isEmpty()) {
                    stringResource(
                      R.string.select_board_for_search_controller_no_boards_found,
                      siteDescriptor.siteName
                    )
                  } else {
                    stringResource(
                      R.string.select_board_for_search_controller_no_boards_found_with_query,
                      siteDescriptor.siteName,
                      searchTextFieldState.text
                    )
                  }

                  KurobaComposeText(
                    modifier = Modifier
                      .fillMaxWidth()
                      .wrapContentHeight()
                      .padding(horizontal = 4.dp, vertical = 8.dp),
                    text = text,
                    textAlign = TextAlign.Center
                  )
                }
              )

              return@LazyVerticalGridWithFastScroller
            }

            items(
              count = boardsSupportingSearch.size,
              span = { index ->
                if (boardsSupportingSearch.getOrNull(index) is SearchBoard.AllBoards) {
                  twoCellsSpan
                } else {
                  oneCellSpan
                }
              },
              itemContent = { index ->
                val searchBoard = boardsSupportingSearch[index]

                BuildSearchBoardCell(
                  isTablet = isTablet,
                  searchBoard = searchBoard,
                  onSearchBoardClicked = { clickedSearchBoard ->
                    onBoardSelected(clickedSearchBoard)
                    pop()
                  }
                )
              }
            )
          }
        )
      }
    }
  }

  @Composable
  private fun BuildSearchBoardCell(
    isTablet: Boolean,
    searchBoard: SearchBoard,
    onSearchBoardClicked: (SearchBoard) -> Unit
  ) {
    val chanTheme = LocalChanTheme.current

    val background = remember(key1 = chanTheme, key2 = searchBoard) {
      if (searchBoard != prevSelectedBoard) {
        return@remember Color.Unspecified
      }

      return@remember chanTheme.postHighlightedColorCompose
    }

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 42.dp)
        .background(background)
        .clickable { onSearchBoardClicked(searchBoard) }
    ) {
      val fontSize = if (isTablet) {
        18.ktu
      } else {
        16.ktu
      }

      KurobaComposeText(
        modifier = Modifier
          .fillMaxSize()
          .align(Alignment.Center),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        text = searchBoard.boardCodeFormatted(),
        fontSize = fontSize,
        textAlign = TextAlign.Center
      )
    }
  }

  private fun collectBoardsSupportingSearch(query: CharSequence): List<SearchBoard> {
    val boardsSupportingSearch = mutableListOf<SearchBoard>()

    if (supportsAllBoardsSearch) {
      val canAdd = query.isEmpty() || SearchBoard.AllBoards.boardCode().contains(query, ignoreCase = true)

      if (canAdd) {
        boardsSupportingSearch += SearchBoard.AllBoards
      }
    }

    boardsSupportingSearch.addAll(applySearchQueryAndSortBoards(query))

    return boardsSupportingSearch
  }

  private fun applySearchQueryAndSortBoards(query: CharSequence): List<SearchBoard> {
    val boards = searchBoardProvider().filter { searchBoard ->
      if (query.isEmpty()) {
        return@filter true
      }

      return@filter searchBoard.boardCode()
        .contains(query, ignoreCase = true)
    }

    if (query.isNotEmpty()) {
      return InputWithQuerySorter.sort(
        input = boards,
        query = query,
        textSelector = { searchBoard -> searchBoard.boardCode() }
      )
    }

    return boards
  }

}
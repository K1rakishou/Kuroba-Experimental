package com.github.k1rakishou.chan.features.site_archive

import android.content.Context
import android.os.Parcelable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.compose.AsyncUiData
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.features.toolbar.BackArrowMenuItem
import com.github.k1rakishou.chan.features.toolbar.ToolbarMiddleContent
import com.github.k1rakishou.chan.features.toolbar.ToolbarText
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeErrorMessage
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeMessage
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeProgressIndicator
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.chan.ui.compose.lazylist.LazyColumnWithFastScroller
import com.github.k1rakishou.chan.ui.compose.providers.ComposeEntrypoint
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.providers.LocalContentPaddings
import com.github.k1rakishou.chan.ui.compose.search.rememberSimpleSearchStateV2
import com.github.k1rakishou.chan.ui.compose.textAsFlow
import com.github.k1rakishou.chan.ui.controller.base.Controller
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.viewModelByKey
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import javax.inject.Inject

class BoardArchiveController(
  context: Context,
  private val catalogDescriptor: ChanDescriptor.CatalogDescriptor,
  private val onThreadClicked: (ChanDescriptor.ThreadDescriptor) -> Unit
) : Controller(context) {

  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  private val viewModel by viewModelByKey<BoardArchiveViewModel>(
    key = catalogDescriptor.serializeToString(),
    params = { Params(catalogDescriptor) }
  )

  override fun injectActivityDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    toolbarState.enterDefaultMode(
      leftItem = BackArrowMenuItem(
        onClick = { requireNavController().popController() }
      ),
      middleContent = ToolbarMiddleContent.Title(
        title = ToolbarText.String(getString(R.string.controller_board_archive_title, catalogDescriptor.boardCode()))
      ),
      menuBuilder = {
        withMenuItem(
          id = ACTION_SEARCH,
          drawableId = R.drawable.ic_search_white_24dp,
          onClick = { toolbarState.enterSearchMode() }
        )
      }
    )

    view = ComposeView(context).apply {
      setContent {
        ComposeEntrypoint {
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

  @Composable
  private fun BuildContent() {
    val archiveThreads = viewModel.archiveThreads

    BuildListOfArchiveThreads(
      archiveThreads = archiveThreads,
      viewModel = viewModel
    ) { threadNo ->
      viewModel.currentlySelectedThreadNo.value = threadNo

      val threadDescriptor = ChanDescriptor.ThreadDescriptor.create(catalogDescriptor, threadNo)
      onThreadClicked(threadDescriptor)

      withLayoutMode(
        phone = { requireNavController().popController(false) }
      )
    }
  }

  @Composable
  private fun BuildListOfArchiveThreads(
    viewModel: BoardArchiveViewModel,
    archiveThreads: List<BoardArchiveViewModel.ArchiveThread>,
    onThreadClicked: (Long) -> Unit
  ) {
    val chanTheme = LocalChanTheme.current
    val contentPaddings = LocalContentPaddings.current

    val boardArchiveControllerStateMut by viewModel.state
    val boardArchiveControllerState = boardArchiveControllerStateMut

    val page by viewModel.page
    val endReached by viewModel.endReached

    val searchState = rememberSimpleSearchStateV2<BoardArchiveViewModel.ArchiveThread>(
      textFieldState = toolbarState.search.searchQueryState
    )
    val searchQuery = searchState.textFieldState.text

    val searchResultsPair by produceState(
      initialValue = searchState.usingSearch to archiveThreads,
      key1 = searchQuery,
      key2 = page,
      key3 = archiveThreads,
      producer = {
        withContext(Dispatchers.Default) {
          value = processSearchQuery(searchQuery, archiveThreads)
        }
      }
    )

    val resultsFromSearch = searchResultsPair.first
    val searchResults = searchResultsPair.second

    val state = rememberLazyListState(
      initialFirstVisibleItemIndex = viewModel.rememberedFirstVisibleItemIndex,
      initialFirstVisibleItemScrollOffset = viewModel.rememberedFirstVisibleItemScrollOffset
    )

    if (!searchState.usingSearch) {
      DisposableEffect(
        key1 = Unit,
        effect = {
          onDispose {
            viewModel.updatePrevLazyListState(
              firstVisibleItemIndex = state.firstVisibleItemIndex,
              firstVisibleItemScrollOffset = state.firstVisibleItemScrollOffset
            )
          }
        }
      )
    } else {
      LaunchedEffect(key1 = Unit) {
        searchState.textFieldState.textAsFlow()
          .onEach {
            try {
              state.scrollToItem(0)
            } catch (_: Throwable) {

            }
          }
          .collect()
      }
    }

    val paddingValues = remember(contentPaddings) {
      contentPaddings
        .asPaddingValues(controllerKey)
    }

    LazyColumnWithFastScroller(
      state = state,
      modifier = Modifier
        .fillMaxSize(),
      contentPadding = paddingValues,
      draggableScrollbar = true
    ) {
      if (searchResults.isEmpty()) {
        if (boardArchiveControllerState is AsyncUiData.Error) {
          item {
            KurobaComposeErrorMessage(
              modifier = Modifier.fillParentMaxSize(),
              error = boardArchiveControllerState.throwable
            )
          }

          return@LazyColumnWithFastScroller
        }

        if (boardArchiveControllerState is AsyncUiData.Loading) {
          item {
            KurobaComposeProgressIndicator(modifier = Modifier.fillParentMaxSize())
          }

          return@LazyColumnWithFastScroller
        }
      }

      items(
        count = searchResults.size + 1,
        key = { index -> searchResults.getOrNull(index)?.threadDescriptor ?: "<null_${index}>" },
        contentType = { "archive_thread_item" }
      ) { index ->
        val archiveThreadItem = searchResults.getOrNull(index)
        if (archiveThreadItem != null) {
          ArchiveThreadItem(
            position = index,
            archiveThread = searchResults[index],
            onThreadClicked = onThreadClicked
          )

          if (index >= 0 && index < searchResults.size) {
            Divider(
              modifier = Modifier.padding(horizontal = 2.dp),
              color = chanTheme.dividerColorCompose,
              thickness = 1.dp
            )
          }
        }
      }

      item(
        key = "list_footer",
        contentType = "list_footer"
      ) {
        ListFooter(
          resultsFromSearch = resultsFromSearch,
          searchQuery = searchQuery,
          hasResults = searchResults.isNotEmpty(),
          endReached = endReached,
          page = page,
          boardArchiveControllerState = boardArchiveControllerState,
          viewModel = viewModel
        )
      }
    }
  }

  @Composable
  private fun ListFooter(
    resultsFromSearch: Boolean,
    searchQuery: CharSequence?,
    hasResults: Boolean,
    endReached: Boolean,
    page: Int?,
    boardArchiveControllerState: AsyncUiData<Unit>,
    viewModel: BoardArchiveViewModel
  ) {
    if (boardArchiveControllerState is AsyncUiData.NotInitialized) {
      return
    }

    val modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight()
      .padding(horizontal = 8.dp, vertical = 12.dp)

    if (boardArchiveControllerState is AsyncUiData.Error) {
      KurobaComposeErrorMessage(
        modifier = modifier,
        error = boardArchiveControllerState.throwable
      )

      return
    }

    if (boardArchiveControllerState is AsyncUiData.Loading) {
      KurobaComposeProgressIndicator(modifier = modifier)
      return
    }

    boardArchiveControllerState as AsyncUiData.UiData

    if (!hasResults) {
      if (!resultsFromSearch || searchQuery == null) {
        KurobaComposeMessage(
          modifier = modifier,
          message = stringResource(id = R.string.search_nothing_found)
        )
      } else {
        KurobaComposeMessage(
          modifier = modifier,
          message = stringResource(id = R.string.search_nothing_found_with_query, searchQuery)
        )
      }

      return
    }

    if (endReached) {
      KurobaComposeText(
        modifier = modifier,
        text = stringResource(id = R.string.archives_end_reached),
        textAlign = TextAlign.Center
      )

      return
    }

    // Do not trigger the next page load when we are searching for something
    if (page != null && !resultsFromSearch) {
      LaunchedEffect(
        key1 = page,
        block = { viewModel.loadNextPageOfArchiveThreads() }
      )
    }
  }

  private fun processSearchQuery(
    searchQuery: CharSequence?,
    archiveThreads: List<BoardArchiveViewModel.ArchiveThread>
  ): Pair<Boolean, List<BoardArchiveViewModel.ArchiveThread>> {
    if (searchQuery == null || searchQuery.isEmpty()) {
      return false to archiveThreads
    }

    val results = archiveThreads
      .filter { archiveThread -> archiveThread.comment.contains(other = searchQuery, ignoreCase = true) }

    return true to results
  }

  @Composable
  private fun ArchiveThreadItem(
    position: Int,
    archiveThread: BoardArchiveViewModel.ArchiveThread,
    onThreadClicked: (Long) -> Unit
  ) {
    val chanTheme = LocalChanTheme.current
    val currentlySelectedThreadNo by remember { viewModel.currentlySelectedThreadNo }

    val backgroundColor = remember(key1 = archiveThread.threadNo) {
      if (currentlySelectedThreadNo == archiveThread.threadNo) {
        chanTheme.postHighlightedColorCompose
      } else {
        Color.Unspecified
      }
    }

    Column(modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight()
      .background(color = backgroundColor)
      .clickable { onThreadClicked(archiveThread.threadNo) }
      .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
      val threadNo = remember(key1 = archiveThread.threadNo) {
        "#${position + 1} No. ${archiveThread.threadNo}"
      }

      val alreadyVisited = viewModel.alreadyVisitedThreads.containsKey(archiveThread.threadDescriptor)

      val alpha = if (alreadyVisited) {
        0.7f
      } else {
        1f
      }

      KurobaComposeText(
        modifier = Modifier.alpha(alpha),
        text = threadNo,
        color = chanTheme.textColorHintCompose,
        fontSize = 12.ktu
      )

      KurobaComposeText(
        modifier = Modifier.alpha(alpha),
        text = archiveThread.comment,
        fontSize = 14.ktu
      )
    }
  }

  @Parcelize
  data class Params(
    val catalogDescriptor: ChanDescriptor.CatalogDescriptor
  ) : Parcelable

  companion object {
    private const val ACTION_SEARCH = 0
  }

}
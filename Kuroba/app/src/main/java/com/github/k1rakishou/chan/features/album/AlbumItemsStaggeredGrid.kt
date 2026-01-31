package com.github.k1rakishou.chan.features.album

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.ui.compose.data.ChanDescriptorUi
import com.github.k1rakishou.chan.ui.compose.lazylist.LazyVerticalStaggeredGridWithFastScroller
import com.github.k1rakishou.chan.ui.compose.providers.LocalContentPaddings
import com.github.k1rakishou.chan.ui.controller.base.ControllerKey
import com.github.k1rakishou.chan.ui.helper.awaitWhile
import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter

private const val TAG = "AlbumItemsStaggeredGrid"

@Composable
fun AlbumItemsStaggeredGrid(
  controllerKey: ControllerKey,
  controllerViewModel: AlbumViewControllerViewModel,
  albumSpanCount: Int,
  onClick: (AlbumItemData) -> Unit,
  onLongClick: (AlbumItemData) -> Unit,
  clearDownloadingAlbumItemState: (DownloadingAlbumItem) -> Unit
) {
  val contentPaddings = LocalContentPaddings.current
  val albumSelection by controllerViewModel.albumSelection.collectAsState()
  val showAlbumViewsImageDetails by controllerViewModel.showAlbumViewsImageDetails.collectAsState()
  val globalNsfwMode by controllerViewModel.globalNsfwMode.collectAsState()
  val currentDescriptor by controllerViewModel.currentDescriptor.collectAsState()

  val albumItems = controllerViewModel.albumItems
  val downloadingAlbumItems = controllerViewModel.downloadingAlbumItems

  val state = rememberLazyStaggeredGridState(
    initialFirstVisibleItemIndex = controllerViewModel.lastScrollPosition.intValue
  )
  val currentLazyStaggeredGridState = rememberUpdatedState(newValue = state)

  LaunchedEffect(key1 = Unit) {
    controllerViewModel.scrollToPositionRequests
      .filter { scrollToPosition -> scrollToPosition >= 0 }
      .collectLatest { scrollToPosition ->
        try {
          val currentState = currentLazyStaggeredGridState.value

          Logger.debug(TAG) {
            "scrollToPosition() Got scroll request to position ${scrollToPosition}, " +
              "current totalItemsCount: ${currentState.layoutInfo.totalItemsCount}, " +
              "waiting for LazyList to fully load..."
          }

          val success = awaitWhile(
            maxWaitTimeMs = 1_000L,
            waitWhile = { currentState.layoutInfo.totalItemsCount < scrollToPosition }
          )

          Logger.debug(TAG) {
            "scrollToPosition() " +
              "totalItemsCount: ${currentState.layoutInfo.totalItemsCount}, " +
              "scrollToPosition: ${scrollToPosition}, " +
              "success: ${success}"
          }

          currentState.scrollToItem(scrollToPosition)
        } catch (_: Throwable) {
          // no-op
        } finally {
          controllerViewModel.resetScrollToPositionRequests()
        }
      }
  }

  LaunchedEffect(key1 = state) {
    // Give some time for scroll position restoration routine to do it's job
    delay(2000)

    snapshotFlow { state.firstVisibleItemIndex }
      .debounce(100)
      .filter { state.layoutInfo.totalItemsCount > 0 }
      .collectLatest { firstVisibleItemIndex -> controllerViewModel.updateLastScrollPosition(firstVisibleItemIndex) }
  }

  LazyVerticalStaggeredGridWithFastScroller(
    modifier = Modifier.fillMaxSize(),
    columns = StaggeredGridCells.Fixed(albumSpanCount),
    state = state,
    verticalItemSpacing = 2.dp,
    horizontalArrangement = Arrangement.spacedBy(2.dp),
    contentPadding = remember(contentPaddings, controllerKey) { contentPaddings.asPaddingValues(controllerKey) }
  ) {
    items(
      count = albumItems.size,
      key = { index -> albumItems.get(index).composeKey },
      contentType = { "album_item" },
      itemContent = { index ->
        val albumItemData = albumItems[index]

        AlbumItem(
          modifier = Modifier
            .fillMaxSize()
            .let { modifier ->
              val aspectRatio = albumItemData.albumItemPostData
                ?.aspectRatio
                ?: 1f

              return@let modifier.aspectRatio(aspectRatio)
            },
          isInSelectionMode = albumSelection.isInSelectionMode,
          isSelected = albumItemData.id in albumSelection.selectedItems,
          isNsfwModeEnabled = globalNsfwMode ?: false,
          showAlbumViewsImageDetails = showAlbumViewsImageDetails ?: false,
          albumSpanCount = albumSpanCount,
          controllerKey = controllerKey,
          chanDescriptorUi = remember { currentDescriptor?.let { descriptor -> ChanDescriptorUi(descriptor) } },
          albumItemData = albumItemData,
          downloadingAlbumItem = downloadingAlbumItems[albumItemData.id],
          onClick = onClick,
          onLongClick = onLongClick,
          clearDownloadingAlbumItemState = clearDownloadingAlbumItemState
        )
      }
    )
  }
}
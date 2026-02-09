package com.github.k1rakishou.chan.ui.compose.lazylist

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.ui.compose.awaitPointerSlopOrCancellationWithPass
import com.github.k1rakishou.chan.ui.compose.lazylist.wrapper.LazyGridStateWrapper
import com.github.k1rakishou.chan.ui.compose.lazylist.wrapper.LazyItemInfoWrapper
import com.github.k1rakishou.chan.ui.compose.lazylist.wrapper.LazyLayoutInfoWrapper
import com.github.k1rakishou.chan.ui.compose.lazylist.wrapper.LazyListStateWrapper
import com.github.k1rakishou.chan.ui.compose.lazylist.wrapper.LazyStaggeredGridStateWrapper
import com.github.k1rakishou.chan.ui.compose.lazylist.wrapper.LazyStateWrapper
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.ui.globalstate.GlobalUiStateHolder
import com.github.k1rakishou.chan.utils.appDependencies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt


val DEFAULT_UNDRAGGABLE_SCROLLBAR_WIDTH = 6.dp
val DEFAULT_DRAGGABLE_SCROLLBAR_WIDTH = 10.dp
val DEFAULT_SCROLLBAR_HEIGHT = 64.dp

@Composable
private fun defaultScrollbarWidth(draggableScrollbar: Boolean): Int {
  if (draggableScrollbar) {
    return with(LocalDensity.current) { remember { DEFAULT_DRAGGABLE_SCROLLBAR_WIDTH.toPx().toInt() } }
  }

  return with(LocalDensity.current) { remember { DEFAULT_UNDRAGGABLE_SCROLLBAR_WIDTH.toPx().toInt() } }
}

@Composable
private fun defaultScrollbarHeight(): Int {
  return with(LocalDensity.current) { remember { DEFAULT_SCROLLBAR_HEIGHT.toPx().toInt() } }
}

@Composable
fun LazyColumnWithFastScroller(
  modifier: Modifier = Modifier,
  state: LazyListState,
  contentPadding: PaddingValues = remember { PaddingValues() },
  draggableScrollbar: Boolean = true,
  userScrollEnabled: Boolean = true,
  reverseLayout: Boolean = false,
  scrollbarWidth: Int = defaultScrollbarWidth(draggableScrollbar),
  scrollbarHeight: Int = defaultScrollbarHeight(),
  verticalArrangement: Arrangement.Vertical =
    if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
  horizontalAlignment: Alignment.Horizontal = Alignment.Start,
  onFastScrollerDragStateChanged: ((Boolean) -> Unit)? = null,
  content: LazyListScope.() -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val density = LocalDensity.current

  val globalUiStateHolder = appDependencies().globalUiStateHolder

  val paddingTop = remember(contentPadding) { contentPadding.calculateTopPadding() }
  val paddingBottom = remember(contentPadding) { contentPadding.calculateBottomPadding() }

  val lazyListStateWrapper = remember { LazyListStateWrapper(state) }
  val coroutineScope = rememberCoroutineScope()

  var scrollbarDragProgress by remember(draggableScrollbar) { mutableStateOf<Float?>(null) }

  BoxWithConstraints {
    val maxWidthPx = with(density) { maxWidth.toPx().toInt() }
    val maxHeightPx = with(density) { maxHeight.toPx().toInt() }

    Box(
      modifier = Modifier
        .pointerInput(
          contentPadding,
          maxWidthPx,
          maxHeightPx,
          draggableScrollbar,
          block = {
            if (draggableScrollbar) {
              processFastScrollerInputs(
                coroutineScope = coroutineScope,
                globalUiStateHolder = globalUiStateHolder,
                lazyStateWrapper = lazyListStateWrapper,
                width = maxWidthPx,
                paddingTop = with(density) { paddingTop.roundToPx() },
                paddingBottom = with(density) { paddingBottom.roundToPx() },
                scrollbarWidth = scrollbarWidth,
                onScrollbarDragStateUpdated = { dragProgress ->
                  scrollbarDragProgress = dragProgress
                  onFastScrollerDragStateChanged?.invoke(dragProgress != null)
                }
              )
            }
          }
        )
    ) {
      LazyColumn(
        modifier = modifier.then(
          Modifier
            .scrollbar(
              lazyStateWrapper = lazyListStateWrapper,
              scrollbarDimens = ScrollbarDimens.Vertical.Static(
                width = scrollbarWidth,
                height = scrollbarHeight
              ),
              scrollbarTrackColor = chanTheme.scrollbarTrackColorCompose,
              scrollbarThumbColorNormal = chanTheme.scrollbarThumbColorNormalCompose,
              scrollbarThumbColorDragged = chanTheme.scrollbarThumbColorDraggedCompose,
              contentPadding = contentPadding,
              scrollbarManualDragProgress = scrollbarDragProgress
            )
        ),
        reverseLayout = reverseLayout,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        userScrollEnabled = userScrollEnabled,
        state = state,
        contentPadding = contentPadding,
        content = content
      )
    }
  }

}

@Composable
fun LazyVerticalGridWithFastScroller(
  columns: GridCells,
  modifier: Modifier = Modifier,
  state: LazyGridState = rememberLazyGridState(),
  draggableScrollbar: Boolean = true,
  scrollbarWidth: Int = defaultScrollbarWidth(draggableScrollbar),
  scrollbarHeight: Int = defaultScrollbarHeight(),
  contentPadding: PaddingValues = PaddingValues(0.dp),
  reverseLayout: Boolean = false,
  verticalArrangement: Arrangement.Vertical =
    if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
  horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
  userScrollEnabled: Boolean = true,
  onFastScrollerDragStateChanged: ((Boolean) -> Unit)? = null,
  content: LazyGridScope.() -> Unit,
) {
  val chanTheme = LocalChanTheme.current
  val density = LocalDensity.current

  val globalUiStateHolder = appDependencies().globalUiStateHolder

  val paddingTop = remember(contentPadding) { contentPadding.calculateTopPadding() }
  val paddingBottom = remember(contentPadding) { contentPadding.calculateBottomPadding() }

  val lazyGridStateWrapper = remember { LazyGridStateWrapper(state) }
  val coroutineScope = rememberCoroutineScope()

  var scrollbarDragProgress by remember(draggableScrollbar) { mutableStateOf<Float?>(null) }

  BoxWithConstraints {
    val maxWidthPx = with(density) { maxWidth.toPx().toInt() }
    val maxHeightPx = with(density) { maxHeight.toPx().toInt() }

    Box(
      modifier = Modifier
        .pointerInput(
          contentPadding,
          maxWidthPx,
          maxHeightPx,
          draggableScrollbar,
          block = {
            if (draggableScrollbar) {
              processFastScrollerInputs(
                coroutineScope = coroutineScope,
                globalUiStateHolder = globalUiStateHolder,
                lazyStateWrapper = lazyGridStateWrapper,
                width = maxWidthPx,
                paddingTop = with(density) { paddingTop.roundToPx() },
                paddingBottom = with(density) { paddingBottom.roundToPx() },
                scrollbarWidth = scrollbarWidth,
                onScrollbarDragStateUpdated = { dragProgress ->
                  scrollbarDragProgress = dragProgress
                  onFastScrollerDragStateChanged?.invoke(dragProgress != null)
                }
              )
            }
          }
        )
    ) {
      LazyVerticalGrid(
        modifier = modifier.then(
          Modifier
            .scrollbar(
              lazyStateWrapper = lazyGridStateWrapper,
              scrollbarDimens = ScrollbarDimens.Vertical.Static(
                width = scrollbarWidth,
                height = scrollbarHeight
              ),
              scrollbarTrackColor = chanTheme.scrollbarTrackColorCompose,
              scrollbarThumbColorNormal = chanTheme.scrollbarThumbColorNormalCompose,
              scrollbarThumbColorDragged = chanTheme.scrollbarThumbColorDraggedCompose,
              contentPadding = contentPadding,
              scrollbarManualDragProgress = scrollbarDragProgress
            )
        ),
        columns = columns,
        reverseLayout = reverseLayout,
        verticalArrangement = verticalArrangement,
        horizontalArrangement = horizontalArrangement,
        userScrollEnabled = userScrollEnabled,
        state = state,
        contentPadding = contentPadding,
        content = content
      )
    }
  }
}

@Composable
fun LazyVerticalStaggeredGridWithFastScroller(
  modifier: Modifier = Modifier,
  columns: StaggeredGridCells,
  state: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
  contentPadding: PaddingValues = PaddingValues(0.dp),
  draggableScrollbar: Boolean = true,
  scrollbarWidth: Int = defaultScrollbarWidth(draggableScrollbar),
  scrollbarHeight: Int = defaultScrollbarHeight(),
  verticalItemSpacing: Dp = 0.dp,
  horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(0.dp),
  userScrollEnabled: Boolean = true,
  onFastScrollerDragStateChanged: ((Boolean) -> Unit)? = null,
  content: LazyStaggeredGridScope.() -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val density = LocalDensity.current

  val globalUiStateHolder = appDependencies().globalUiStateHolder

  val paddingTop = remember(contentPadding) { contentPadding.calculateTopPadding() }
  val paddingBottom = remember(contentPadding) { contentPadding.calculateBottomPadding() }

  val lazyStaggeredGridStateWrapper = remember { LazyStaggeredGridStateWrapper(state) }
  val coroutineScope = rememberCoroutineScope()

  var scrollbarDragProgress by remember(draggableScrollbar) { mutableStateOf<Float?>(null) }

  BoxWithConstraints {
    val maxWidthPx = with(density) { maxWidth.toPx().toInt() }
    val maxHeightPx = with(density) { maxHeight.toPx().toInt() }

    Box(
      modifier = Modifier
        .pointerInput(
          contentPadding,
          maxWidthPx,
          maxHeightPx,
          draggableScrollbar,
          block = {
            if (draggableScrollbar) {
              processFastScrollerInputs(
                coroutineScope = coroutineScope,
                globalUiStateHolder = globalUiStateHolder,
                lazyStateWrapper = lazyStaggeredGridStateWrapper,
                width = maxWidthPx,
                paddingTop = with(density) { paddingTop.roundToPx() },
                paddingBottom = with(density) { paddingBottom.roundToPx() },
                scrollbarWidth = scrollbarWidth,
                onScrollbarDragStateUpdated = { dragProgress ->
                  scrollbarDragProgress = dragProgress
                  onFastScrollerDragStateChanged?.invoke(dragProgress != null)
                }
              )
            }
          }
        )
    ) {
      LazyVerticalStaggeredGrid(
        modifier = modifier.then(
          Modifier
            .scrollbar(
              lazyStateWrapper = lazyStaggeredGridStateWrapper,
              scrollbarDimens = ScrollbarDimens.Vertical.Static(
                width = scrollbarWidth,
                height = scrollbarHeight
              ),
              scrollbarTrackColor = chanTheme.scrollbarTrackColorCompose,
              scrollbarThumbColorNormal = chanTheme.scrollbarThumbColorNormalCompose,
              scrollbarThumbColorDragged = chanTheme.scrollbarThumbColorDraggedCompose,
              contentPadding = contentPadding,
              scrollbarManualDragProgress = scrollbarDragProgress
            )
        ),
        columns = columns,
        userScrollEnabled = userScrollEnabled,
        state = lazyStaggeredGridStateWrapper.lazyStaggeredGridState,
        contentPadding = contentPadding,
        verticalItemSpacing = verticalItemSpacing,
        horizontalArrangement = horizontalArrangement,
        content = content
      )
    }
  }
}

suspend fun <
  ItemInfo : LazyItemInfoWrapper,
  LayoutInfo : LazyLayoutInfoWrapper<ItemInfo>
> PointerInputScope.processFastScrollerInputs(
  coroutineScope: CoroutineScope,
  globalUiStateHolder: GlobalUiStateHolder,
  lazyStateWrapper: LazyStateWrapper<ItemInfo, LayoutInfo>,
  width: Int,
  paddingTop: Int,
  paddingBottom: Int,
  scrollbarWidth: Int,
  onScrollbarDragStateUpdated: (Float?) -> Unit
) {
  awaitEachGesture {
    val downEvent = awaitPointerEvent(pass = PointerEventPass.Initial)
    if (downEvent.type != PointerEventType.Press) {
      return@awaitEachGesture
    }

    val down = downEvent.changes.firstOrNull()
      ?: return@awaitEachGesture

    if (down.position.x < (width - scrollbarWidth)) {
      return@awaitEachGesture
    }

    val touchSlopDetected = awaitPointerSlopOrCancellationWithPass(
      pointerId = down.id,
      pointerEventPass = PointerEventPass.Initial,
      onPointerSlopReached = { change, _ ->
        val distance = change.position - down.position

        // In order to avoid triggering fast scroller accidentally when touching the right edge of the screen (some
        // phones have curved screen, god forbid them) we want to check that the finger actually moved vertically more
        // than horizontally by 1.333x distance.
        if (distance.y.absoluteValue > (distance.x.absoluteValue * 1.333f)) {
          down.consume()
          change.consume()
          return@awaitPointerSlopOrCancellationWithPass true
        }

        return@awaitPointerSlopOrCancellationWithPass false
      }
    ) != null

    if (!touchSlopDetected) {
      return@awaitEachGesture
    }

    var job: Job? = null

    try {
      globalUiStateHolder.updateFastScrollerState {
        updateIsDraggingFastScroller(true)
      }

      while (true) {
        val nextEvent = awaitPointerEvent(pass = PointerEventPass.Initial)
        if (nextEvent.type != PointerEventType.Move) {
          break
        }

        for (change in nextEvent.changes) {
          change.consume()
        }

        nextEvent.changes.lastOrNull()?.let { lastChange ->
          job = coroutineScope.launch {
            val touchY = lastChange.position.y - paddingTop
            val scrollbarTrackHeight = lazyStateWrapper.viewportHeight - paddingBottom - paddingTop

            val touchFraction = (touchY / scrollbarTrackHeight).coerceIn(0f, 1f)
            val itemsCount = (lazyStateWrapper.totalItemsCount - lazyStateWrapper.fullyVisibleItemsCount)

            var scrollToIndex = (itemsCount.toFloat() * touchFraction).roundToInt()
            if (touchFraction == 0f) {
              scrollToIndex = 0
            } else if (touchFraction == 1f) {
              // We want to use the actual last item index for scrolling when touchFraction == 1f
              // because otherwise we may end up not at the very bottom of the list but slightly
              // above it (like 1 element's height)
              scrollToIndex = lazyStateWrapper.totalItemsCount
            }

            lazyStateWrapper.scrollToItem(scrollToIndex)

            if (isActive) {
              onScrollbarDragStateUpdated(touchFraction)
            }
          }
        }
      }
    } finally {
      // Make sure the coroutine doesn't overwrite the onScrollbarDragStateUpdated() with non-null
      // value because otherwise the scrollbar will stuck in "dragging" state.
      job?.cancel()
      job = null

      onScrollbarDragStateUpdated(null)

      globalUiStateHolder.updateFastScrollerState {
        updateIsDraggingFastScroller(false)
      }
    }
  }
}

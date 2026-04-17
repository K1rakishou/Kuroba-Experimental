package com.github.k1rakishou.chan.ui.compose.lazylist

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.ui.compose.lazylist.wrapper.LazyGridItemInfoWrapper
import com.github.k1rakishou.chan.ui.compose.lazylist.wrapper.LazyGridLayoutInfoWrapper
import com.github.k1rakishou.chan.ui.compose.lazylist.wrapper.LazyGridStateWrapper
import com.github.k1rakishou.chan.ui.compose.lazylist.wrapper.LazyItemInfoWrapper
import com.github.k1rakishou.chan.ui.compose.lazylist.wrapper.LazyLayoutInfoWrapper
import com.github.k1rakishou.chan.ui.compose.lazylist.wrapper.LazyListStateWrapper
import com.github.k1rakishou.chan.ui.compose.lazylist.wrapper.LazyStateWrapper
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme

private val DefaultPaddingValues = PaddingValues(0.dp)

@Immutable
sealed class ScrollbarDimens {

  @Immutable
  sealed class Vertical : ScrollbarDimens() {
    abstract val width: Int

    data class Dynamic(override val width: Int, val minHeight: Int) : Vertical()
    data class Static(override val width: Int, val height: Int) : Vertical()
  }

  @Immutable
  sealed class Horizontal : ScrollbarDimens() {
    abstract val height: Int

    data class Dynamic(override val height: Int, val minWidth: Int) : Horizontal()
    data class Static(override val height: Int, val width: Int) : Horizontal()
  }
}

fun Modifier.scrollbar(
  state: LazyListState,
  scrollbarDimens: ScrollbarDimens,
  scrollbarTrackColor: Color? = null,
  scrollbarThumbColorNormal: Color? = null,
  scrollbarThumbColorDragged: Color? = null,
  contentPadding: PaddingValues = DefaultPaddingValues,
  scrollbarManualDragProgress: Float? = null,
): Modifier {
  return composed {
    val chanTheme = LocalChanTheme.current
    val lazyListStateWrapper = remember { LazyListStateWrapper(state) }

    return@composed scrollbar(
      lazyStateWrapper = lazyListStateWrapper,
      scrollbarDimens = scrollbarDimens,
      scrollbarTrackColor = scrollbarTrackColor ?: chanTheme.scrollbarTrackColorCompose,
      scrollbarThumbColorNormal = scrollbarThumbColorNormal ?: chanTheme.scrollbarThumbColorNormalCompose,
      scrollbarThumbColorDragged = scrollbarThumbColorDragged ?: chanTheme.scrollbarThumbColorDraggedCompose,
      contentPadding = contentPadding,
      scrollbarManualDragProgress = scrollbarManualDragProgress,
      isScrollInProgress = { listStateWrapper -> listStateWrapper.isScrollInProgress }
    )
  }
}

fun Modifier.scrollbar(
  state: LazyGridState,
  scrollbarDimens: ScrollbarDimens,
  scrollbarTrackColor: Color? = null,
  scrollbarThumbColorNormal: Color? = null,
  scrollbarThumbColorDragged: Color? = null,
  contentPadding: PaddingValues = DefaultPaddingValues,
  scrollbarManualDragProgress: Float? = null,
): Modifier {
  return composed {
    val chanTheme = LocalChanTheme.current
    val lazyListStateWrapper = remember { LazyGridStateWrapper(state) }

    return@composed scrollbar<LazyGridItemInfoWrapper, LazyGridLayoutInfoWrapper>(
      lazyStateWrapper = lazyListStateWrapper,
      scrollbarDimens = scrollbarDimens,
      scrollbarTrackColor = scrollbarTrackColor ?: chanTheme.scrollbarTrackColorCompose,
      scrollbarThumbColorNormal = scrollbarThumbColorNormal ?: chanTheme.scrollbarThumbColorNormalCompose,
      scrollbarThumbColorDragged = scrollbarThumbColorDragged ?: chanTheme.scrollbarThumbColorDraggedCompose,
      contentPadding = contentPadding,
      scrollbarManualDragProgress = scrollbarManualDragProgress,
      isScrollInProgress = { listStateWrapper -> listStateWrapper.isScrollInProgress }
    )
  }
}

/**
 * scrollbar for LazyLists
 * */
fun <ItemInfo : LazyItemInfoWrapper, LayoutInfo : LazyLayoutInfoWrapper<ItemInfo>> Modifier.scrollbar(
  lazyStateWrapper: LazyStateWrapper<ItemInfo, LayoutInfo>,
  scrollbarDimens: ScrollbarDimens,
  scrollbarTrackColor: Color,
  scrollbarThumbColorNormal: Color,
  scrollbarThumbColorDragged: Color,
  contentPadding: PaddingValues,
  scrollbarManualDragProgress: Float? = null,
  isScrollInProgress: (LazyStateWrapper<ItemInfo, LayoutInfo>) -> Boolean = { lazyListState ->
    lazyListState.isScrollInProgress
  }
): Modifier {
  return composed(
    inspectorInfo = debugInspectorInfo {
      name = "scrollbar"
      properties["lazyStateWrapper"] = lazyStateWrapper
      properties["scrollbarDimens"] = scrollbarDimens
      properties["scrollbarTrackColor"] = scrollbarTrackColor
      properties["scrollbarThumbColorNormal"] = scrollbarThumbColorNormal
      properties["scrollbarThumbColorDragged"] = scrollbarThumbColorDragged
      properties["contentPadding"] = contentPadding
      properties["scrollbarManualDragProgress"] = scrollbarManualDragProgress
    },
    factory = {
      val density = LocalDensity.current
      val layoutDirection = LocalLayoutDirection.current

      var topPaddingPx = 0f
      var bottomPaddingPx = 0f

      var leftPaddingPx = 0f
      var rightPaddingPx = 0f

      when (scrollbarDimens) {
        is ScrollbarDimens.Horizontal -> {
          leftPaddingPx = with(density) {
            remember(key1 = contentPadding) { contentPadding.calculateLeftPadding(layoutDirection).toPx() }
          }

          rightPaddingPx = with(density) {
            remember(key1 = contentPadding) { contentPadding.calculateRightPadding(layoutDirection).toPx() }
          }
        }
        is ScrollbarDimens.Vertical -> {
          topPaddingPx = with(density) {
            remember(key1 = contentPadding) { contentPadding.calculateTopPadding().toPx() }
          }

          bottomPaddingPx = with(density) {
            remember(key1 = contentPadding) { contentPadding.calculateBottomPadding().toPx() }
          }
        }
      }

      val isScrollbarDragged = scrollbarManualDragProgress != null
      val targetThumbAlpha = when {
        isScrollbarDragged -> 1f
        isScrollInProgress(lazyStateWrapper) -> 0.8f
        else -> 0f
      }

      val targetTrackAlpha = when {
        isScrollbarDragged -> 0.7f
        isScrollInProgress(lazyStateWrapper) -> 0.5f
        else -> 0f
      }

      val duration = if (isScrollInProgress(lazyStateWrapper) || isScrollbarDragged) 150 else 500
      val delay = if (isScrollInProgress(lazyStateWrapper) || isScrollbarDragged) 0 else 1500

      val thumbAlphaAnimated = animateFloatAsState(
        targetValue = targetThumbAlpha,
        animationSpec = tween(
          durationMillis = duration,
          delayMillis = delay
        )
      )

      val trackAlphaAnimated = animateFloatAsState(
        targetValue = targetTrackAlpha,
        animationSpec = tween(
          durationMillis = duration,
          delayMillis = delay
        )
      )

      val thumbColorAnimated = animateColorAsState(
        targetValue = if (isScrollbarDragged) scrollbarThumbColorDragged else scrollbarThumbColorNormal,
        animationSpec = tween(durationMillis = 200)
      )

      val scrollbarSizeAnimatedState = when (scrollbarDimens) {
        is ScrollbarDimens.Horizontal -> {
          val scrollbarHeightPx =
            if (isScrollInProgress(lazyStateWrapper) || isScrollbarDragged) scrollbarDimens.height else 0

          animateIntAsState(
            targetValue = scrollbarHeightPx,
            animationSpec = tween(
              durationMillis = duration,
              delayMillis = delay
            )
          )
        }
        is ScrollbarDimens.Vertical -> {
          val scrollbarWidthPx =
            if (isScrollInProgress(lazyStateWrapper) || isScrollbarDragged) scrollbarDimens.width else 0

          animateIntAsState(
            targetValue = scrollbarWidthPx,
            animationSpec = tween(
              durationMillis = duration,
              delayMillis = delay
            )
          )
        }
      }

      this.then(
        Modifier.drawWithContent {
          drawContent()

          val firstVisibleElementIndex = lazyStateWrapper.layoutInfo.visibleItemsInfo.firstOrNull()?.index
          val needDrawScrollbar = lazyStateWrapper.totalItemsCount > lazyStateWrapper.visibleItemsCount
            && (isScrollInProgress(lazyStateWrapper) || thumbAlphaAnimated.value > 0f || trackAlphaAnimated.value > 0f)

          // Draw scrollbar if total item count is greater than visible item count and either
          // currently scrolling or if any of the animations is still running and lazy column has content
          if (!needDrawScrollbar || firstVisibleElementIndex == null) {
            return@drawWithContent
          }

          when (scrollbarDimens) {
            is ScrollbarDimens.Horizontal -> {
              val (scrollbarOffsetX, scrollbarWidthAdjusted) = when (scrollbarDimens) {
                is ScrollbarDimens.Horizontal.Dynamic -> {
                  calculateDynamicScrollbarWidth(
                    leftPaddingPx = leftPaddingPx,
                    rightPaddingPx = rightPaddingPx,
                    lazyStateWrapper = lazyStateWrapper,
                    firstVisibleElementIndex = firstVisibleElementIndex,
                    scrollbarMinWidth = scrollbarDimens.minWidth.toFloat(),
                    realScrollbarWidthDiff = null
                  )
                }
                is ScrollbarDimens.Horizontal.Static -> {
                  calculateStaticScrollbarWidth(
                    leftPaddingPx = leftPaddingPx,
                    rightPaddingPx = rightPaddingPx,
                    scrollbarManualDragProgress = scrollbarManualDragProgress,
                    lazyStateWrapper = lazyStateWrapper,
                    firstVisibleElementIndex = firstVisibleElementIndex,
                    scrollbarWidth = scrollbarDimens.width.toFloat()
                  )
                }
              }

              val offsetX = leftPaddingPx + scrollbarOffsetX
              val offsetY = this.size.height - scrollbarSizeAnimatedState.value

              drawRect(
                color = scrollbarTrackColor,
                topLeft = Offset(leftPaddingPx, offsetY),
                size = Size(
                  width = this.size.width - (leftPaddingPx + rightPaddingPx),
                  height = scrollbarSizeAnimatedState.value.toFloat()
                ),
                alpha = trackAlphaAnimated.value
              )

              drawRect(
                color = thumbColorAnimated.value,
                topLeft = Offset(offsetX, offsetY),
                size = Size(scrollbarWidthAdjusted, scrollbarSizeAnimatedState.value.toFloat()),
                alpha = thumbAlphaAnimated.value
              )
            }
            is ScrollbarDimens.Vertical -> {
              val (scrollbarOffsetY, scrollbarHeightAdjusted) = when (scrollbarDimens) {
                is ScrollbarDimens.Vertical.Dynamic -> {
                  calculateDynamicScrollbarHeight(
                    topPaddingPx = topPaddingPx,
                    bottomPaddingPx = bottomPaddingPx,
                    lazyStateWrapper = lazyStateWrapper,
                    firstVisibleElementIndex = firstVisibleElementIndex,
                    scrollbarMinHeight = scrollbarDimens.minHeight.toFloat(),
                    realScrollbarHeightDiff = null
                  )
                }
                is ScrollbarDimens.Vertical.Static -> {
                  calculateStaticScrollbarHeight(
                    topPaddingPx = topPaddingPx,
                    bottomPaddingPx = bottomPaddingPx,
                    scrollbarManualDragProgress = scrollbarManualDragProgress,
                    lazyStateWrapper = lazyStateWrapper,
                    firstVisibleElementIndex = firstVisibleElementIndex,
                    scrollbarHeight = scrollbarDimens.height.toFloat()
                  )
                }
              }

              val offsetY = topPaddingPx + scrollbarOffsetY
              val offsetX = this.size.width - scrollbarSizeAnimatedState.value

              val trackWidth = scrollbarSizeAnimatedState.value.toFloat()
              val trackHeight = this.size.height - (topPaddingPx + bottomPaddingPx)

              val topLeft = Offset(offsetX, topPaddingPx)
              val size = Size(trackWidth, trackHeight)

              drawRect(
                color = scrollbarTrackColor,
                topLeft = topLeft,
                size = size,
                alpha = trackAlphaAnimated.value
              )

              kotlin.run {
                drawRect(
                  color = thumbColorAnimated.value,
                  topLeft = Offset(offsetX, offsetY),
                  size = Size(scrollbarSizeAnimatedState.value.toFloat(), scrollbarHeightAdjusted),
                  alpha = thumbAlphaAnimated.value
                )
              }
            }
          }
        }
      )
    }
  )
}

/**
 * Vertical scrollbar for Composables that use ScrollState (like verticalScroll())
 * */
fun Modifier.scrollbar(
  paddings: PaddingValues,
  scrollState: ScrollState,
  enabled: Boolean = true
): Modifier {
  if (!enabled) {
    return this
  }

  return composed {
    val density = LocalDensity.current
    val chanTheme = LocalChanTheme.current

    val scrollbarWidth = with(density) { 4.dp.toPx() }
    val scrollbarHeight = with(density) { 16.dp.toPx() }
    val thumbColor = chanTheme.scrollbarThumbColorDraggedCompose

    val scrollStateUpdated by rememberUpdatedState(newValue = scrollState)
    val currentPositionPx by remember { derivedStateOf { scrollStateUpdated.value } }
    val maxScrollPositionPx by remember { derivedStateOf { scrollStateUpdated.maxValue } }

    val topPaddingPx = with(density) {
      remember(key1 = paddings) { paddings.calculateTopPadding().toPx() }
    }
    val bottomPaddingPx = with(density) {
      remember(key1 = paddings) { paddings.calculateBottomPadding().toPx() }
    }

    val duration = if (scrollStateUpdated.isScrollInProgress) 150 else 1000
    val delay = if (scrollStateUpdated.isScrollInProgress) 0 else 1000
    val targetThumbAlpha = if (scrollStateUpdated.isScrollInProgress) 0.8f else 0f

    val thumbAlphaAnimated by animateFloatAsState(
      targetValue = targetThumbAlpha,
      animationSpec = tween(
        durationMillis = duration,
        delayMillis = delay
      )
    )

    return@composed Modifier.drawWithContent {
      drawContent()

      if (maxScrollPositionPx == Int.MAX_VALUE || maxScrollPositionPx == 0) {
        return@drawWithContent
      }

      val availableHeight = this.size.height - scrollbarHeight - topPaddingPx - bottomPaddingPx
      val unit = availableHeight / maxScrollPositionPx.toFloat()
      val scrollPosition = currentPositionPx * unit

      val offsetX = this.size.width - scrollbarWidth
      val offsetY = topPaddingPx + scrollPosition

      drawRect(
        color = thumbColor,
        topLeft = Offset(offsetX, offsetY),
        size = Size(scrollbarWidth, scrollbarHeight),
        alpha = thumbAlphaAnimated
      )
    }
  }
}

private fun <
  ItemInfo : LazyItemInfoWrapper,
  LayoutInfo : LazyLayoutInfoWrapper<ItemInfo>
> ContentDrawScope.calculateDynamicScrollbarWidth(
  leftPaddingPx: Float,
  rightPaddingPx: Float,
  lazyStateWrapper: LazyStateWrapper<ItemInfo, LayoutInfo>,
  firstVisibleElementIndex: Int,
  scrollbarMinWidth: Float,
  realScrollbarWidthDiff: Float?
): Pair<Float, Float> {
  val totalWidthWithoutPaddings = this.size.width - (realScrollbarWidthDiff ?: 0f) - leftPaddingPx - rightPaddingPx
  val elementWidth = totalWidthWithoutPaddings / lazyStateWrapper.totalItemsCount
  val scrollbarOffsetX = firstVisibleElementIndex * elementWidth
  val scrollbarWidthReal = (lazyStateWrapper.visibleItemsCount * elementWidth)
  val scrollbarWidthAdjusted = scrollbarWidthReal.coerceAtLeast(scrollbarMinWidth)

  if (scrollbarWidthAdjusted > scrollbarWidthReal && realScrollbarWidthDiff == null) {
    return calculateDynamicScrollbarWidth(
      leftPaddingPx = leftPaddingPx,
      rightPaddingPx = rightPaddingPx,
      lazyStateWrapper = lazyStateWrapper,
      firstVisibleElementIndex = firstVisibleElementIndex,
      scrollbarMinWidth = scrollbarMinWidth,
      realScrollbarWidthDiff = (scrollbarWidthAdjusted - scrollbarWidthReal)
    )
  }

  return Pair(scrollbarOffsetX, scrollbarWidthAdjusted)
}

private fun <
  ItemInfo : LazyItemInfoWrapper,
  LayoutInfo : LazyLayoutInfoWrapper<ItemInfo>
> ContentDrawScope.calculateDynamicScrollbarHeight(
  topPaddingPx: Float,
  bottomPaddingPx: Float,
  lazyStateWrapper: LazyStateWrapper<ItemInfo, LayoutInfo>,
  firstVisibleElementIndex: Int,
  scrollbarMinHeight: Float,
  realScrollbarHeightDiff: Float?
): Pair<Float, Float> {
  val totalHeightWithoutPaddings = this.size.height - (realScrollbarHeightDiff ?: 0f) - topPaddingPx - bottomPaddingPx
  val elementHeight = totalHeightWithoutPaddings / lazyStateWrapper.totalItemsCount
  val scrollbarOffsetY = firstVisibleElementIndex * elementHeight
  val scrollbarHeightReal = (lazyStateWrapper.visibleItemsCount * elementHeight)
  val scrollbarHeightAdjusted = scrollbarHeightReal.coerceAtLeast(scrollbarMinHeight)

  if (scrollbarHeightAdjusted > scrollbarHeightReal && realScrollbarHeightDiff == null) {
    return calculateDynamicScrollbarHeight(
      topPaddingPx = topPaddingPx,
      bottomPaddingPx = bottomPaddingPx,
      lazyStateWrapper = lazyStateWrapper,
      firstVisibleElementIndex = firstVisibleElementIndex,
      scrollbarMinHeight = scrollbarMinHeight,
      realScrollbarHeightDiff = (scrollbarHeightAdjusted - scrollbarHeightReal)
    )
  }

  return Pair(scrollbarOffsetY, scrollbarHeightAdjusted)
}

@Suppress("IfThenToElvis")
private fun <
  ItemInfo : LazyItemInfoWrapper,
  LayoutInfo : LazyLayoutInfoWrapper<ItemInfo>
> ContentDrawScope.calculateStaticScrollbarWidth(
  leftPaddingPx: Float,
  rightPaddingPx: Float,
  scrollbarManualDragProgress: Float?,
  lazyStateWrapper: LazyStateWrapper<ItemInfo, LayoutInfo>,
  firstVisibleElementIndex: Int,
  scrollbarWidth: Float
): Pair<Float, Float> {
  val scrollProgress = if (scrollbarManualDragProgress == null) {
    firstVisibleElementIndex.toFloat() /
      (lazyStateWrapper.totalItemsCount.toFloat() - lazyStateWrapper.visibleItemsCount.toFloat())
  } else {
    scrollbarManualDragProgress
  }

  val totalWidth = this.size.width - scrollbarWidth - leftPaddingPx - rightPaddingPx
  val scrollbarOffsetX = (scrollProgress * totalWidth)

  return Pair(scrollbarOffsetX, scrollbarWidth)
}

@Suppress("IfThenToElvis")
private fun <
  ItemInfo : LazyItemInfoWrapper,
  LayoutInfo : LazyLayoutInfoWrapper<ItemInfo>
> ContentDrawScope.calculateStaticScrollbarHeight(
  topPaddingPx: Float,
  bottomPaddingPx: Float,
  scrollbarManualDragProgress: Float?,
  lazyStateWrapper: LazyStateWrapper<ItemInfo, LayoutInfo>,
  firstVisibleElementIndex: Int,
  scrollbarHeight: Float
): Pair<Float, Float> {
  val scrollProgress = if (scrollbarManualDragProgress == null) {
    firstVisibleElementIndex.toFloat() /
      (lazyStateWrapper.totalItemsCount.toFloat() - lazyStateWrapper.visibleItemsCount.toFloat())
  } else {
    scrollbarManualDragProgress
  }

  val totalHeight = this.size.height - scrollbarHeight - topPaddingPx - bottomPaddingPx
  val scrollbarOffsetY = (scrollProgress * totalHeight)

  return Pair(scrollbarOffsetY, scrollbarHeight)
}
package com.github.k1rakishou.chan.ui.compose.lazylist

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.IntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.LayoutManager
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.github.k1rakishou.chan.core.usecase.PostMapInfoHolder
import com.github.k1rakishou.chan.ui.compose.awaitPointerSlopOrCancellationWithPass
import com.github.k1rakishou.chan.ui.compose.providers.ComposeEntrypoint
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.ui.globalstate.GlobalUiStateHolder
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.chan.ui.view.PostInfoMapItemDecoration
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.common.quantize
import com.github.k1rakishou.core_themes.ChanTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

class ScrollbarView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

  @Inject
  lateinit var appResources: AppResources
  @Inject
  lateinit var globalUiStateHolder: GlobalUiStateHolder

  private val _postInfoMapItemDecoration by lazy(LazyThreadSafetyMode.NONE) { PostInfoMapItemDecoration(context) }
  private val _attachedRecyclerView = mutableStateOf<RecyclerView?>(null, referentialEqualityPolicy())
  private val _isScrollbarDraggable = mutableStateOf(false)
  private var _thumbDragListener: ThumbDragListener? = null

  val scrollbarWidth: Dp
    get() {
      return if (_isScrollbarDraggable.value) {
        DEFAULT_DRAGGABLE_SCROLLBAR_WIDTH
      } else {
        DEFAULT_UNDRAGGABLE_SCROLLBAR_WIDTH
      }
    }

  val scrollbarWidthPx: Int
    get() {
      return with(appResources.composeDensity) {
        scrollbarWidth.roundToPx()
      }
    }

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    addView(
      ComposeView(context).apply {
        setContent {
          ComposeEntrypoint {
            Scrollbar()
          }
        }
      }
    )
  }

  val isDragging: Boolean
    get() = false

  fun attachRecyclerView(recyclerView: RecyclerView) {
    _attachedRecyclerView.value = recyclerView
    recyclerView.isVerticalScrollBarEnabled = false
  }

  fun detachRecyclerView() {
    _attachedRecyclerView.value = null
  }

  fun thumbDragListener(listener: ThumbDragListener) {
    check(_thumbDragListener == null) { "Attempt to attach multiple listeners" }
    _thumbDragListener = listener
  }

  fun isScrollbarDraggable(draggable: Boolean) {
    _isScrollbarDraggable.value = draggable
  }

  fun updateScrollbarMarks(postMapInfoHolder: PostMapInfoHolder) {
    _postInfoMapItemDecoration.setItems(postMapInfoHolder)
  }

  fun hideScrollbarMarks() {
    _postInfoMapItemDecoration.hide()
  }

  fun cleanup() {
    _attachedRecyclerView.value = null
    _thumbDragListener = null
  }

  @Composable
  private fun Scrollbar() {
    val attachedRecyclerViewMut by _attachedRecyclerView
    val attachedRecyclerView = attachedRecyclerViewMut

    if (attachedRecyclerView == null) {
      return
    }

    val isScrollbarDraggable by _isScrollbarDraggable

    val desiredScrollbarHeight = DEFAULT_SCROLLBAR_HEIGHT
    val coroutineScope = rememberCoroutineScope()

    val recyclerViewPaddingsState = remember { mutableStateOf<PaddingValues>(PaddingValues()) }
    val recyclerViewPaddings by recyclerViewPaddingsState

    val scrollbarManualDragProgressState = remember(isScrollbarDraggable) { mutableStateOf<Float?>(null) }

    val layoutManager = attachedRecyclerView.layoutManager

    Box(
      modifier = Modifier
        .fillMaxHeight()
        .width(scrollbarWidth)
        .scrollbar(
          recyclerView = attachedRecyclerView,
          scrollbarWidth = scrollbarWidth,
          desiredScrollbarHeight = desiredScrollbarHeight,
          scrollbarManualDragProgressState = scrollbarManualDragProgressState,
          recyclerViewPaddingsState = recyclerViewPaddingsState
        )
        .pointerInput(
          recyclerViewPaddings,
          scrollbarWidth,
          isScrollbarDraggable,
          attachedRecyclerView,
          layoutManager,
          block = {
            if (!isScrollbarDraggable) {
              return@pointerInput
            }

            val localLayoutManager = layoutManager
              ?: return@pointerInput

            val paddingTop = recyclerViewPaddings
              .calculateTopPadding()
              .roundToPx()
            val paddingBottom = recyclerViewPaddings
              .calculateBottomPadding()
              .roundToPx()

            processFastScrollerInputs(
              coroutineScope = coroutineScope,
              recyclerViewHeight = attachedRecyclerView.height,
              recyclerViewLayoutManager = localLayoutManager,
              width = scrollbarWidth.roundToPx(),
              paddingTop = paddingTop,
              paddingBottom = paddingBottom,
              scrollbarWidth = scrollbarWidth.roundToPx(),
              onScrollbarDragStateStarted = {
                scrollbarManualDragProgressState.value = null
                _thumbDragListener?.onDragStarted()
              },
              onScrollbarDragStateUpdated = { dragProgress ->
                scrollbarManualDragProgressState.value = dragProgress
              },
              onScrollbarDragStateEnded = {
                scrollbarManualDragProgressState.value = null
                _thumbDragListener?.onDragEnded()
              }
            )
          }
        )
    )
  }

  private fun Modifier.scrollbar(
    recyclerView: RecyclerView,
    scrollbarWidth: Dp,
    desiredScrollbarHeight: Dp,
    scrollbarManualDragProgressState: State<Float?>,
    recyclerViewPaddingsState: MutableState<PaddingValues>
  ): Modifier {
    return composed(
      inspectorInfo = debugInspectorInfo {
        name = "scrollbar"
        properties["recyclerView"] = recyclerView
        properties["scrollbarWidth"] = scrollbarWidth
        properties["desiredScrollbarHeight"] = desiredScrollbarHeight
        properties["scrollbarManualDragProgress"] = scrollbarManualDragProgressState.value
        properties["recyclerViewPaddings"] = recyclerViewPaddingsState.value
      },
      factory = {
        val density = LocalDensity.current
        val layoutDirection = LocalLayoutDirection.current
        val chanTheme = LocalChanTheme.current

        val layoutManager = recyclerView.layoutManager

        val recyclerViewScrollExtent = remember { mutableIntStateOf(0) }
        val recyclerViewScrollRange = remember { mutableIntStateOf(0) }
        val recyclerViewScrollOffset = remember { mutableIntStateOf(0) }
        var recyclerViewScrollState by remember { mutableStateOf<RecyclerViewScrollState>(RecyclerViewScrollState.NotScrolling) }
        var needToDrawScrollbar by remember { mutableStateOf(false) }
        val scrollbarManualDragProgress by scrollbarManualDragProgressState

        val isScrollbarDragged = scrollbarManualDragProgress != null
        val targetStaticThumbAlpha = when {
          isScrollbarDragged -> 1f
          recyclerViewScrollState == RecyclerViewScrollState.Scrolling -> 0.8f
          else -> 0f
        }

        val targetRealThumbAlpha = when {
          isScrollbarDragged -> 0.8f
          recyclerViewScrollState == RecyclerViewScrollState.Scrolling -> 0.8f
          else -> 0f
        }

        val targetMarksAlpha = when {
          isScrollbarDragged -> 1f
          recyclerViewScrollState == RecyclerViewScrollState.Scrolling -> 1f
          else -> 0f
        }

        val targetTrackAlpha = when {
          isScrollbarDragged -> 0.7f
          recyclerViewScrollState == RecyclerViewScrollState.Scrolling -> 0.5f
          else -> 0f
        }

        val isBeingScrolledOrDragged = recyclerViewScrollState == RecyclerViewScrollState.Scrolling || isScrollbarDragged
        val duration = if (isBeingScrolledOrDragged) 150 else 500
        val delay = if (isBeingScrolledOrDragged) 0 else 1500
        val scrollbarWidthPx = with(density) { if (isBeingScrolledOrDragged) scrollbarWidth.roundToPx() else 0 }
        val tempArray = remember(key1 = layoutManager) { IntArray(32) }

        val staticThumbAlphaAnimatedState = animateFloatAsState(
          targetValue = targetStaticThumbAlpha,
          animationSpec = tween(
            durationMillis = duration,
            delayMillis = delay
          )
        )

        val realThumbAlphaAnimatedState = animateFloatAsState(
          targetValue = targetRealThumbAlpha,
          animationSpec = tween(
            durationMillis = duration,
            delayMillis = delay
          )
        )

        val marksAlphaAnimatedState = animateFloatAsState(
          targetValue = targetMarksAlpha,
          animationSpec = tween(
            durationMillis = duration,
            delayMillis = delay
          )
        )

        val trackAlphaAnimatedState = animateFloatAsState(
          targetValue = targetTrackAlpha,
          animationSpec = tween(
            durationMillis = duration,
            delayMillis = delay
          )
        )

        val thumbColorAnimatedState = animateColorAsState(
          targetValue = if (isScrollbarDragged) {
            chanTheme.scrollbarThumbColorDraggedCompose
          } else {
            chanTheme.scrollbarThumbColorNormalCompose
          },
          animationSpec = tween(durationMillis = 200)
        )

        val scrollbarWidthAnimatedState = animateIntAsState(
          targetValue = scrollbarWidthPx,
          animationSpec = tween(
            durationMillis = duration,
            delayMillis = delay
          )
        )

        val onLayoutListener = remember(key1 = recyclerView, key2 = density, key3 = layoutDirection) {
          OnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val newPaddingValues = with(density) {
              PaddingValues(
                start = recyclerView.paddingLeft.toDp(),
                end = recyclerView.paddingRight.toDp(),
                top = recyclerView.paddingTop.toDp(),
                bottom = recyclerView.paddingBottom.toDp()
              )
            }

            if (recyclerViewPaddingsState.value != newPaddingValues) {
              recyclerViewPaddingsState.value = newPaddingValues
            }
          }
        }

        val onScrollListener = remember(key1 = recyclerView) {
          object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
              if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                if (recyclerViewScrollState != RecyclerViewScrollState.Scrolling) {
                  val range = recyclerView.computeVerticalScrollRange()
                  needToDrawScrollbar = range > (recyclerView.height * 1.33f)

                  recyclerViewScrollState = RecyclerViewScrollState.Scrolling
                }
              } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                if (recyclerViewScrollState != RecyclerViewScrollState.NotScrolling) {
                  recyclerViewScrollState = RecyclerViewScrollState.NotScrolling
                }
              }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
              val extent = recyclerView.computeVerticalScrollExtent()
              val range = recyclerView.computeVerticalScrollRange()
              val offset = recyclerView.computeVerticalScrollOffset()

              recyclerViewScrollExtent.intValue = extent
              recyclerViewScrollRange.intValue = range
              recyclerViewScrollOffset.intValue = offset
              needToDrawScrollbar = range > (recyclerView.height * 1.33f)
            }
          }
        }

        DisposableEffect(key1 = onScrollListener) {
          recyclerView.addOnScrollListener(onScrollListener)
          recyclerView.addOnLayoutChangeListener(onLayoutListener)

          onDispose {
            recyclerView.removeOnScrollListener(onScrollListener)
            recyclerView.removeOnLayoutChangeListener(onLayoutListener)
          }
        }

        return@composed this.then(
          Modifier.drawWithContent {
            drawContent()

            if (needToDrawScrollbar) {
              val desiredScrollbarHeightPx = with(density) { desiredScrollbarHeight.toPx() }

              drawStaticScrollbar(
                density = density,
                chanTheme = chanTheme,
                scrollbarWidthAnimatedState = scrollbarWidthAnimatedState,
                desiredScrollbarHeightPx = desiredScrollbarHeightPx,
                scrollbarManualDragProgress = scrollbarManualDragProgress,
                recyclerViewPaddingsState = recyclerViewPaddingsState,
                recyclerViewScrollExtent = recyclerViewScrollExtent,
                recyclerViewScrollRange = recyclerViewScrollRange,
                recyclerViewScrollOffset = recyclerViewScrollOffset,
                thumbAlphaAnimatedState = staticThumbAlphaAnimatedState,
                trackAlphaAnimatedState = trackAlphaAnimatedState,
                thumbColorAnimatedState = thumbColorAnimatedState,
              )

              val recyclerViewPaddings = recyclerViewPaddingsState.value

              _postInfoMapItemDecoration.draw(
                contentDrawScope = this,
                scrollbarMarkWidth = scrollbarWidth.toPx(),
                recyclerTopPadding = recyclerViewPaddings
                  .calculateTopPadding()
                  .toPx(),
                recyclerBottomPadding = recyclerViewPaddings
                  .calculateBottomPadding()
                  .toPx(),
                recyclerView = recyclerView,
                alpha = marksAlphaAnimatedState.value.quantize(precision = 0.33f)
              )

              if (layoutManager != null) {
                drawRealDynamicScrollbar(
                  tempArray = tempArray,
                  recyclerViewLayoutManager = layoutManager,
                  scrollbarWidthAnimatedState = scrollbarWidthAnimatedState,
                  recyclerViewPaddingsState = recyclerViewPaddingsState,
                  color = chanTheme.scrollbarTrackColorCompose,
                  alpha = realThumbAlphaAnimatedState.value.quantize(precision = 0.33f)
                )
              }
            }
          }
        )
      }
    )
  }

  private fun ContentDrawScope.drawRealDynamicScrollbar(
    tempArray: IntArray,
    recyclerViewLayoutManager: LayoutManager,
    scrollbarWidthAnimatedState: State<Int>,
    recyclerViewPaddingsState: State<PaddingValues>,
    color: Color,
    alpha: Float
  ) {
    val scrollbarWidthAnimated by scrollbarWidthAnimatedState
    val recyclerViewPaddings by recyclerViewPaddingsState

    val topPaddingPx = recyclerViewPaddings.calculateTopPadding().toPx()
    val bottomPaddingPx = recyclerViewPaddings.calculateBottomPadding().toPx()

    val totalItemsCount = recyclerViewLayoutManager.itemCount
    val minPostsInThread = 100

    if (totalItemsCount < minPostsInThread) {
      // To avoid real scrollbar flickering constantly in short threads
      return
    }

    val visibleItemsCount = recyclerViewLayoutManager.fullyVisibleItemsCount(tempArray)
    val firstVisibleElementIndex = recyclerViewLayoutManager.firstVisibleElementIndex(tempArray)

    val (realScrollbarOffsetY, realScrollbarHeight) = with(density) {
      calculateRealScrollbarHeight(
        topPaddingPx = topPaddingPx,
        bottomPaddingPx = bottomPaddingPx,
        visibleItemsCount = visibleItemsCount,
        totalItemsCount = totalItemsCount,
        firstVisibleElementIndex = firstVisibleElementIndex,
        scrollbarMinHeight = 1.dp.toPx(),
        realScrollbarHeightDiff = null
      )
    }

    val desiredHeight = 2.dp.toPx()
    val offsetY = topPaddingPx + realScrollbarOffsetY + ((realScrollbarHeight - desiredHeight) / 2f)
    val offsetX = this.size.width - scrollbarWidthAnimated

    drawRect(
      color = color,
      topLeft = Offset(offsetX, offsetY),
      size = Size(scrollbarWidthAnimated.toFloat(), desiredHeight),
      alpha = alpha
    )
  }

  private fun ContentDrawScope.drawStaticScrollbar(
    density: Density,
    chanTheme: ChanTheme,
    scrollbarWidthAnimatedState: State<Int>,
    desiredScrollbarHeightPx: Float,
    scrollbarManualDragProgress: Float?,
    recyclerViewPaddingsState: State<PaddingValues>,
    recyclerViewScrollExtent: IntState,
    recyclerViewScrollRange: IntState,
    recyclerViewScrollOffset: IntState,
    thumbAlphaAnimatedState: State<Float>,
    trackAlphaAnimatedState: State<Float>,
    thumbColorAnimatedState: State<Color>
  ) {
    val recyclerViewPaddings by recyclerViewPaddingsState
    val thumbAlphaAnimated by thumbAlphaAnimatedState
    val trackAlphaAnimated by trackAlphaAnimatedState
    val thumbColorAnimated by thumbColorAnimatedState
    val scrollbarWidthAnimated by scrollbarWidthAnimatedState

    val topPaddingPx = recyclerViewPaddings.calculateTopPadding().toPx()
    val bottomPaddingPx = recyclerViewPaddings.calculateBottomPadding().toPx()

    val recyclerViewScrollExtent = recyclerViewScrollExtent.intValue.toFloat()
    val recyclerViewScrollRange = recyclerViewScrollRange.intValue.toFloat()
    val recyclerViewScrollOffset = recyclerViewScrollOffset.intValue.toFloat()

    val (scrollbarOffsetY, scrollbarHeightAdjusted) = with(density) {
      calculateStaticScrollbarHeight(
        topPaddingPx = topPaddingPx,
        bottomPaddingPx = bottomPaddingPx,
        scrollbarManualDragProgress = scrollbarManualDragProgress,
        recyclerViewScrollExtent = recyclerViewScrollExtent,
        recyclerViewScrollRange = recyclerViewScrollRange,
        recyclerViewScrollOffset = recyclerViewScrollOffset,
        desiredScrollbarHeightPx = desiredScrollbarHeightPx
      )
    }

    val offsetY = topPaddingPx + scrollbarOffsetY
    val offsetX = this.size.width - scrollbarWidthAnimated

    val trackWidth = scrollbarWidthAnimated.toFloat()
    val trackHeight = this.size.height - (topPaddingPx + bottomPaddingPx)

    val topLeft = Offset(offsetX, topPaddingPx)
    val size = Size(trackWidth, trackHeight)

    drawRect(
      color = chanTheme.scrollbarTrackColorCompose,
      topLeft = topLeft,
      size = size,
      alpha = trackAlphaAnimated
    )

    drawRect(
      color = thumbColorAnimated,
      topLeft = Offset(offsetX, offsetY),
      size = Size(scrollbarWidthAnimated.toFloat(), scrollbarHeightAdjusted),
      alpha = thumbAlphaAnimated
    )
  }

  private fun ContentDrawScope.calculateStaticScrollbarHeight(
    topPaddingPx: Float,
    bottomPaddingPx: Float,
    scrollbarManualDragProgress: Float?,
    recyclerViewScrollExtent: Float,
    recyclerViewScrollRange: Float,
    recyclerViewScrollOffset: Float,
    desiredScrollbarHeightPx: Float
  ): Pair<Float, Float> {
    val scrollProgress = if (scrollbarManualDragProgress == null) {
      val adjustedScrollRange = recyclerViewScrollRange - recyclerViewScrollExtent
      if (adjustedScrollRange > 0) {
        recyclerViewScrollOffset / adjustedScrollRange
      } else {
        0f
      }
    } else {
      scrollbarManualDragProgress
    }

    val totalHeight = this.size.height - desiredScrollbarHeightPx - topPaddingPx - bottomPaddingPx
    val scrollbarOffsetY = (scrollProgress * totalHeight)

    return Pair(scrollbarOffsetY, desiredScrollbarHeightPx)
  }

  private fun ContentDrawScope.calculateRealScrollbarHeight(
    topPaddingPx: Float,
    bottomPaddingPx: Float,
    visibleItemsCount: Int,
    totalItemsCount: Int,
    firstVisibleElementIndex: Int,
    scrollbarMinHeight: Float,
    realScrollbarHeightDiff: Float?
  ): Pair<Float, Float> {
    val totalHeightWithoutPaddings = this.size.height - (realScrollbarHeightDiff ?: 0f) - topPaddingPx - bottomPaddingPx
    val elementHeight = totalHeightWithoutPaddings / totalItemsCount
    val scrollbarOffsetY = firstVisibleElementIndex * elementHeight
    val scrollbarHeightReal = (visibleItemsCount * elementHeight)
    val scrollbarHeightAdjusted = scrollbarHeightReal.coerceAtLeast(scrollbarMinHeight)

    if (scrollbarHeightAdjusted > scrollbarHeightReal && realScrollbarHeightDiff == null) {
      return calculateRealScrollbarHeight(
        topPaddingPx = topPaddingPx,
        bottomPaddingPx = bottomPaddingPx,
        visibleItemsCount = visibleItemsCount,
        totalItemsCount = totalItemsCount,
        firstVisibleElementIndex = firstVisibleElementIndex,
        scrollbarMinHeight = scrollbarMinHeight,
        realScrollbarHeightDiff = (scrollbarHeightAdjusted - scrollbarHeightReal)
      )
    }

    return Pair(scrollbarOffsetY, scrollbarHeightAdjusted)
  }

  private suspend fun PointerInputScope.processFastScrollerInputs(
    coroutineScope: CoroutineScope,
    recyclerViewHeight: Int,
    recyclerViewLayoutManager: LayoutManager,
    width: Int,
    paddingTop: Int,
    paddingBottom: Int,
    scrollbarWidth: Int,
    onScrollbarDragStateStarted: () -> Unit,
    onScrollbarDragStateUpdated: (Float) -> Unit,
    onScrollbarDragStateEnded: () -> Unit,
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

          // In order to avoid triggering fast scroller accidentally when touch the right edge of the screen (some
          // phones have curved screen, god forbid them) we want to check that the finger actually moved vertically more
          // than horizontally by 1/3.
          if (distance.y.absoluteValue < (distance.x.absoluteValue * 1.333f)) {
            return@awaitPointerSlopOrCancellationWithPass false
          }

          down.consume()
          change.consume()
          return@awaitPointerSlopOrCancellationWithPass true
        }
      ) != null

      if (!touchSlopDetected) {
        return@awaitEachGesture
      }

      var job: Job? = null
      onScrollbarDragStateStarted()

      globalUiStateHolder.updateFastScrollerState {
        updateIsDraggingFastScroller(true)
      }

      val tempArray = if (recyclerViewLayoutManager is StaggeredGridLayoutManager) {
        IntArray(recyclerViewLayoutManager.spanCount)
      } else {
        IntArray(0)
      }

      try {
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
              val scrollbarTrackHeight = recyclerViewHeight - paddingBottom - paddingTop
              val touchFraction = (touchY / scrollbarTrackHeight).coerceIn(0f, 1f)
              val itemCount = recyclerViewLayoutManager.itemCount - recyclerViewLayoutManager.fullyVisibleItemsCount(tempArray)

              var scrollToIndex = (touchFraction * itemCount).roundToInt()
              if (touchFraction <= 0f) {
                scrollToIndex = 0
              } else if (touchFraction >= 1f) {
                // We want to use the actual last item index for scrolling when touchFraction == 1f
                // because otherwise we may end up not at the very bottom of the list but slightly
                // above it (like 1 element's height)
                scrollToIndex = itemCount
              }

              when (recyclerViewLayoutManager) {
                is GridLayoutManager -> recyclerViewLayoutManager.scrollToPositionWithOffset(scrollToIndex, 0)
                is LinearLayoutManager -> recyclerViewLayoutManager.scrollToPositionWithOffset(scrollToIndex, 0)
                is StaggeredGridLayoutManager -> recyclerViewLayoutManager.scrollToPositionWithOffset(scrollToIndex, 0)
                else -> error("Unexpected layout manager: ${recyclerViewLayoutManager::class.java.name}")
              }

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

        globalUiStateHolder.updateFastScrollerState {
          updateIsDraggingFastScroller(false)
        }

        onScrollbarDragStateEnded()
      }
    }
  }

  private fun LayoutManager.fullyVisibleItemsCount(tempArray: IntArray): Int {
    return when (this) {
      is GridLayoutManager -> {
        findLastCompletelyVisibleItemPosition() - findFirstCompletelyVisibleItemPosition()
      }
      is LinearLayoutManager -> {
        findLastCompletelyVisibleItemPosition() - findFirstCompletelyVisibleItemPosition()
      }
      is StaggeredGridLayoutManager -> {
        findLastCompletelyVisibleItemPositions(tempArray).max() - findFirstCompletelyVisibleItemPositions(tempArray).min()
      }
      else -> error("Unexpected layout manager: ${this::class.java.name}")
    }
  }

  private fun LayoutManager.firstVisibleElementIndex(tempArray: IntArray): Int {
    return when (this) {
      is GridLayoutManager -> findFirstCompletelyVisibleItemPosition()
      is LinearLayoutManager -> findFirstCompletelyVisibleItemPosition()
      is StaggeredGridLayoutManager -> findFirstCompletelyVisibleItemPositions(tempArray).min()
      else -> error("Unexpected layout manager: ${this::class.java.name}")
    }
  }

  private enum class RecyclerViewScrollState {
    Scrolling,
    NotScrolling
  }

  interface ThumbDragListener {
    fun onDragStarted()
    fun onDragEnded()
  }

}
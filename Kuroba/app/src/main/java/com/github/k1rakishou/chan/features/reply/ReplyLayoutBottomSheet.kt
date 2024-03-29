package com.github.k1rakishou.chan.features.reply

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FixedThreshold
import androidx.compose.material.SwipeableDefaults
import androidx.compose.material.ThresholdConfig
import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutAnimationState
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutState
import com.github.k1rakishou.chan.ui.compose.consumeClicks
import com.github.k1rakishou.chan.ui.compose.providers.LocalWindowInsets
import com.github.k1rakishou.common.quantize
import com.github.k1rakishou.core_themes.ChanTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlin.math.roundToInt


@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ReplyLayoutBottomSheet(
  modifier: Modifier = Modifier,
  replyLayoutState: ReplyLayoutState,
  replyLayoutAnimationState: ReplyLayoutAnimationState,
  chanTheme: ChanTheme,
  onHeightSettled: (Int) -> Unit,
  content: @Composable ColumnScope.(
    targetHeight: Dp,
    draggableState: DraggableState,
    onDragStarted: suspend () -> Unit,
    onDragStopped: suspend (Float) -> Unit
  ) -> Unit
) {
  val density = LocalDensity.current
  val windowInsets = LocalWindowInsets.current
  val threshold = remember { FixedThreshold(52.dp) }

  val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)
  val replyAttachables by replyLayoutState.attachables
  val syntheticAttachables = replyLayoutState.syntheticAttachables

  val defaultOpenedHeightDp = if (replyAttachables.attachables.isEmpty() && syntheticAttachables.isEmpty()) {
    180.dp
  } else {
    260.dp
  }
  val defaultOpenedHeightPx = with(density) { defaultOpenedHeightDp.roundToPx() }

  BoxWithConstraints(
    modifier = modifier,
    contentAlignment = Alignment.BottomCenter
  ) {
    val bottomInsetPx = with(density) { windowInsets.bottom.roundToPx() }
    val minPositionY = with(density) { (toolbarHeight + windowInsets.top).roundToPx() }
    val maxPositionY = constraints.maxHeight

    val anchors = remember(minPositionY, maxPositionY, bottomInsetPx, defaultOpenedHeightPx) {
      mapOf(
        ReplyLayoutAnimationState.Collapsed to maxPositionY,
        ReplyLayoutAnimationState.Opened to (maxPositionY - defaultOpenedHeightPx - bottomInsetPx),
        ReplyLayoutAnimationState.Expanded to minPositionY
      )
    }
    val anchorsUpdated = rememberUpdatedState(newValue = anchors)

    var currentReplyLayoutAnimationState by remember { mutableStateOf<ReplyLayoutAnimationState>(replyLayoutAnimationState) }
    val dragStartPositionY = remember { mutableIntStateOf(0) }
    val lastDragPosition = remember { mutableIntStateOf(0) }
    val isCurrentlyDragging = remember { mutableStateOf(false) }
    val performingFlingAnimation = remember { mutableStateOf(false) }

    val dragOffsetAnimatable = remember {
      Animatable(
        initialValue = maxPositionY,
        typeConverter = Int.VectorConverter,
        visibilityThreshold = 1
      )
    }

    val dragRequests = remember {
      MutableSharedFlow<DragRequest>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
      )
    }

    val draggableState = rememberDraggableState(
      onDelta = { dragged ->
        val prevDragOffset = dragOffsetAnimatable.value
        val newDragOffset = (prevDragOffset + dragged).quantize(1f)
        val target = newDragOffset.toInt().coerceIn(minPositionY, maxPositionY)

        lastDragPosition.intValue = target
        dragRequests.tryEmit(DragRequest.Snap(target))
      }
    )

    LaunchedEffect(
      key1 = replyLayoutState,
      block = {
        val prevAnchors = mutableMapOf<ReplyLayoutAnimationState, Int>()

        combine(
          flow = snapshotFlow { replyLayoutState.replyLayoutAnimationState.value },
          flow2 = snapshotFlow { anchorsUpdated.value },
          transform = { t1, t2 -> Pair(t1, t2) }
        ).collect { (replyLayoutAnimationState, anchors) ->
          if (replyLayoutAnimationState == currentReplyLayoutAnimationState && anchors == prevAnchors) {
            return@collect
          }

          if (isCurrentlyDragging.value || performingFlingAnimation.value) {
            return@collect
          }

          val newReplyLayoutAnimationState = when (replyLayoutAnimationState) {
            ReplyLayoutAnimationState.Collapsing,
            ReplyLayoutAnimationState.Collapsed -> ReplyLayoutAnimationState.Collapsed
            ReplyLayoutAnimationState.Opening,
            ReplyLayoutAnimationState.Opened -> ReplyLayoutAnimationState.Opened
            ReplyLayoutAnimationState.Expanding,
            ReplyLayoutAnimationState.Expanded -> ReplyLayoutAnimationState.Expanded
          }

          val target = anchors[newReplyLayoutAnimationState]
            ?: return@collect

          dragRequests.emit(DragRequest.Animate(newReplyLayoutAnimationState, target))
          currentReplyLayoutAnimationState = newReplyLayoutAnimationState

          if (anchors != prevAnchors) {
            prevAnchors.clear()
            prevAnchors.putAll(anchors)
          }
        }
      }
    )

    LaunchedEffect(
      key1 = minPositionY,
      key2 = maxPositionY,
      block = {
        dragRequests.collectLatest { dragRequest ->
          try {
            when (dragRequest) {
              is DragRequest.Animate -> {
                val targetClamped = dragRequest.target.coerceIn(minPositionY, maxPositionY)
                dragOffsetAnimatable.animateTo(targetClamped)
                onHeightSettled(maxPositionY - targetClamped)
              }

              is DragRequest.Snap -> {
                val targetClamped = dragRequest.target.coerceIn(minPositionY, maxPositionY)
                dragOffsetAnimatable.snapTo(targetClamped)
              }
            }
          } catch (ignored: CancellationException) {
            // no-op
          } finally {
            when (dragRequest) {
              is DragRequest.Animate -> {
                replyLayoutState.onAnimationFinished(dragRequest.replyLayoutAnimationState)
              }
              is DragRequest.Snap -> {
                // no-op
              }
            }
          }
        }
      }
    )

    Column(
      modifier = Modifier
        .offset { IntOffset(x = 0, y = dragOffsetAnimatable.value) }
        .fillMaxSize()
        .consumeClicks(enabled = replyLayoutAnimationState > ReplyLayoutAnimationState.Collapsed)
        .background(chanTheme.backColorCompose)
    ) {
      val targetReplyLayoutHeight = remember(
        density,
        maxPositionY,
        dragOffsetAnimatable.value,
        windowInsets.bottom,
        defaultOpenedHeightPx
      ) {
        return@remember with(density) {
          (maxPositionY - dragOffsetAnimatable.value - windowInsets.bottom.roundToPx())
            .coerceAtLeast(defaultOpenedHeightPx)
            .toFloat()
            // Quantize to avoid unnecessary re-measures/re-layouts/re-compositions when
            // the amount of pixels the reply layout moved is less than 1dp
            .quantize(1.dp.toPx())
            .roundToInt()
            .toDp()
        }
      }

      AnimatedVisibility(
        visible = currentReplyLayoutAnimationState > ReplyLayoutAnimationState.Collapsed,
        enter = fadeIn(),
        exit = fadeOut()
      ) {
        content(
          targetReplyLayoutHeight,
          draggableState,
          {
            dragStartPositionY.intValue = dragOffsetAnimatable.value
            isCurrentlyDragging.value = true
          },
          { velocity ->
            performFling(
              density = density,
              anchors = anchors,
              anchorsUpdated = anchorsUpdated,
              currentPositionY = dragOffsetAnimatable.value,
              dragStartPositionY = dragStartPositionY,
              lastDragPosition = lastDragPosition,
              threshold = threshold,
              replyLayoutState = replyLayoutState,
              draggableState = draggableState,
              velocity = velocity,
              updateDragStartPositionY = { dragStartPositionY.intValue = 0 },
              updateReplyLayoutAnimationState = { newState -> currentReplyLayoutAnimationState = newState },
              onAnimationStarted = {
                performingFlingAnimation.value = true
              },
              onAnimationFinished = { target ->
                onHeightSettled(maxPositionY - target)
                isCurrentlyDragging.value = false
                performingFlingAnimation.value = false
              }
            )
          }
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterialApi::class)
private suspend fun performFling(
  density: Density,
  anchors: Map<ReplyLayoutAnimationState, Int>,
  anchorsUpdated: State<Map<ReplyLayoutAnimationState, Int>>,
  currentPositionY: Int,
  dragStartPositionY: IntState,
  lastDragPosition: IntState,
  threshold: FixedThreshold,
  replyLayoutState: ReplyLayoutState,
  draggableState: DraggableState,
  velocity: Float,
  updateDragStartPositionY: () -> Unit,
  updateReplyLayoutAnimationState: (ReplyLayoutAnimationState) -> Unit,
  onAnimationStarted: () -> Unit,
  onAnimationFinished: (Int) -> Unit
) {
  val newReplyLayoutAnimationState = keyByPosition(
    density = density,
    anchors = anchorsUpdated.value,
    lastPosition = dragStartPositionY.intValue,
    position = currentPositionY,
    threshold = threshold,
    velocity = velocity
  ) ?: ReplyLayoutAnimationState.Collapsed

  updateDragStartPositionY()

  // Set the currentReplyLayoutAnimationState to avoid playing the default collapse/expand
  // animations since we will be playing a custom velocity-based one.
  updateReplyLayoutAnimationState(newReplyLayoutAnimationState)

  val target = anchors[newReplyLayoutAnimationState]!!

  try {
    var prevValue = lastDragPosition.intValue

    replyLayoutState.onAnimationStarted(newReplyLayoutAnimationState)
    onAnimationStarted()

    draggableState.drag {
      Animatable<Int, AnimationVector1D>(
        initialValue = lastDragPosition.intValue,
        typeConverter = Int.VectorConverter,
        visibilityThreshold = 1
      ).animateTo(targetValue = target) {
        dragBy(this.value.toFloat() - prevValue)
        prevValue = this.value
      }
    }
  } catch (ignored: CancellationException) {
    // no-op
  } finally {
    replyLayoutState.onAnimationFinished(newReplyLayoutAnimationState)
    onAnimationFinished(target)
  }
}

@OptIn(ExperimentalMaterialApi::class)
private fun <T> keyByPosition(
  density: Density,
  anchors: Map<T, Int>,
  lastPosition: Int,
  position: Int,
  threshold: ThresholdConfig,
  velocity: Float
): T? {
  val targetValue = computeTarget(
    offset = position,
    lastValue = lastPosition,
    anchors = anchors.values,
    thresholds = { a, b -> with(threshold) { density.computeThreshold(a.toFloat(), b.toFloat()) } },
    velocity = velocity,
    velocityThreshold = with(density) { SwipeableDefaults.VelocityThreshold.toPx() }
  )

  return anchors.entries
    .firstOrNull { (_, value) -> value == targetValue }
    ?.key
}

// Taken from Jetpack Compose Swipeable.kt
private fun computeTarget(
  offset: Int,
  lastValue: Int,
  anchors: Collection<Int>,
  thresholds: (Int, Int) -> Float,
  velocity: Float,
  velocityThreshold: Float
): Int {
  val bounds = findBounds(offset, anchors)
  return when (bounds.size) {
    0 -> lastValue
    1 -> bounds[0]
    else -> {
      val lower = bounds[0]
      val upper = bounds[1]
      if (lastValue <= offset) {
        // Swiping from lower to upper (positive).
        if (velocity >= velocityThreshold) {
          return upper
        } else {
          val threshold = thresholds(lower, upper)
          if (offset < threshold) lower else upper
        }
      } else {
        // Swiping from upper to lower (negative).
        if (velocity <= -velocityThreshold) {
          return lower
        } else {
          val threshold = thresholds(upper, lower)
          if (offset > threshold) upper else lower
        }
      }
    }
  }
}

private fun findBounds(
  offset: Int,
  anchors: Collection<Int>
): List<Int> {
  val a = anchors.filter { it <= offset + 0.001 }.maxOrNull()
  val b = anchors.filter { it >= offset - 0.001 }.minOrNull()

  return when {
    a == null -> listOfNotNull(b)
    b == null -> listOf(a)
    a == b -> listOf(a)
    else -> listOf(a, b)
  }
}

private sealed class DragRequest {
  class Animate(
    val replyLayoutAnimationState: ReplyLayoutAnimationState,
    val target: Int
  ) : DragRequest()

  class Snap(
    val target: Int
  ) : DragRequest()
}
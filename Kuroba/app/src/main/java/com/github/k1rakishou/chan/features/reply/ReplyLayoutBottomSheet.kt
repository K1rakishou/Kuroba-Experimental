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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
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
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutState
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutVisibility
import com.github.k1rakishou.chan.ui.compose.LocalWindowInsets
import com.github.k1rakishou.chan.ui.compose.consumeClicks
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
  replyLayoutState: ReplyLayoutState,
  chanTheme: ChanTheme,
  content: @Composable ColumnScope.(Dp, DraggableState, suspend () -> Unit, suspend (Float) -> Unit) -> Unit
) {
  val density = LocalDensity.current
  val windowInsets = LocalWindowInsets.current
  val threshold = remember { FixedThreshold(1.dp) }

  val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)
  val attachedMediaList = replyLayoutState.attachables

  val defaultOpenedHeightDp = if (attachedMediaList.isEmpty()) {
    dimensionResource(id = R.dimen.reply_layout_container_opened_height_no_attachments)
  } else {
    dimensionResource(id = R.dimen.reply_layout_container_opened_height_with_attachments)
  }
  val defaultOpenedHeightPx = with(density) { defaultOpenedHeightDp.roundToPx() }

  BoxWithConstraints(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.BottomCenter
  ) {
    val replyLayoutVisibility by replyLayoutState.replyLayoutVisibility

    val bottomInsetPx = with(density) { windowInsets.bottom.roundToPx() }
    val minPositionY = with(density) { (toolbarHeight + windowInsets.top).roundToPx() }
    val maxPositionY = constraints.maxHeight

    val anchors = remember(minPositionY, maxPositionY, bottomInsetPx, defaultOpenedHeightPx) {
      mapOf(
        ReplyLayoutVisibility.Collapsed to maxPositionY,
        ReplyLayoutVisibility.Opened to (maxPositionY - defaultOpenedHeightPx - bottomInsetPx),
        ReplyLayoutVisibility.Expanded to minPositionY
      )
    }
    val anchorsUpdated = rememberUpdatedState(newValue = anchors)

    var currentReplyLayoutVisibility by remember { mutableStateOf<ReplyLayoutVisibility>(replyLayoutVisibility) }
    var dragStartPositionY by remember { mutableStateOf(0) }
    var lastDragPosition by remember { mutableStateOf(0) }

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

        lastDragPosition = target
        dragRequests.tryEmit(DragRequest.Snap(target))
      }
    )

    LaunchedEffect(
      key1 = replyLayoutState,
      block = {
        val prevAnchors = mutableMapOf<ReplyLayoutVisibility, Int>()

        combine(
          flow = snapshotFlow { replyLayoutState.replyLayoutVisibility.value },
          flow2 = snapshotFlow { anchorsUpdated.value },
          transform = { t1, t2 -> Pair(t1, t2) }
        ).collect { (replyLayoutVisibility, anchors) ->
          if (replyLayoutVisibility == currentReplyLayoutVisibility && anchors == prevAnchors) {
            return@collect
          }

          val target = anchors[replyLayoutVisibility]
            ?: return@collect

          dragRequests.emit(DragRequest.Animate(target))
          currentReplyLayoutVisibility = replyLayoutVisibility

          if (anchors != prevAnchors) {
            prevAnchors.clear()
            prevAnchors.putAll(anchors)
          }
        }
      }
    )

    LaunchedEffect(
      key1 = Unit,
      block = {
        dragRequests.collectLatest { dragRequest ->
          try {
            when (dragRequest) {
              is DragRequest.Animate -> {
                val targetClamped = dragRequest.target.coerceIn(minPositionY, maxPositionY)
                dragOffsetAnimatable.animateTo(targetClamped)
              }
              is DragRequest.Snap -> {
                val targetClamped = dragRequest.target.coerceIn(minPositionY, maxPositionY)
                dragOffsetAnimatable.snapTo(targetClamped)
              }
            }
          } catch (ignored: CancellationException) {
            // no-op
          }
        }
      }
    )

    Column(
      modifier = Modifier
        .offset { IntOffset(x = 0, y = dragOffsetAnimatable.value) }
        .fillMaxSize()
        .consumeClicks(enabled = replyLayoutVisibility.order > ReplyLayoutVisibility.Collapsed.order)
        .background(chanTheme.backColorCompose)
    ) {
      val targetReplyLayoutHeight = with(density) {
        (maxPositionY - dragOffsetAnimatable.value - windowInsets.bottom.roundToPx())
          .coerceAtLeast(defaultOpenedHeightPx)
          .toFloat()
          // Quantize to avoid unnecessary re-measures/re-layouts/re-compositions when
          // the amount of pixels the reply layout moved is less than 1dp
          .quantize(1.dp.toPx())
          .roundToInt()
          .toDp()
      }

      AnimatedVisibility(
        visible = currentReplyLayoutVisibility != ReplyLayoutVisibility.Collapsed,
        enter = fadeIn(),
        exit = fadeOut()
      ) {
        content(
          targetReplyLayoutHeight,
          draggableState,
          { dragStartPositionY = dragOffsetAnimatable.value },
          { velocity ->
            performFling(
              density = density,
              anchorsUpdated = anchorsUpdated,
              dragStartPositionY = dragStartPositionY,
              currentPositionY = dragOffsetAnimatable.value,
              threshold = threshold,
              replyLayoutState = replyLayoutState,
              lastDragPosition = lastDragPosition,
              anchors = anchors,
              draggableState = draggableState,
              velocity = velocity,
              updateDragStartPositionY = { dragStartPositionY = 0 },
              updateReplyLayoutVisibility = { newReplyLayoutVisibility ->
                currentReplyLayoutVisibility = newReplyLayoutVisibility
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
  anchorsUpdated: State<Map<ReplyLayoutVisibility, Int>>,
  dragStartPositionY: Int,
  currentPositionY: Int,
  threshold: FixedThreshold,
  replyLayoutState: ReplyLayoutState,
  lastDragPosition: Int,
  anchors: Map<ReplyLayoutVisibility, Int>,
  draggableState: DraggableState,
  velocity: Float,
  updateDragStartPositionY: () -> Unit,
  updateReplyLayoutVisibility: (ReplyLayoutVisibility) -> Unit
) {
  val newReplyLayoutVisibility = keyByPosition(
    density = density,
    anchors = anchorsUpdated.value,
    lastPosition = dragStartPositionY,
    position = currentPositionY,
    threshold = threshold,
    velocity = velocity
  )

  updateDragStartPositionY()

  // Set the currentReplyLayoutVisibility to avoid playing the default collapse/expand
  // animations since we will be playing a custom velocity-based one.
  updateReplyLayoutVisibility(newReplyLayoutVisibility)

  when (newReplyLayoutVisibility) {
    ReplyLayoutVisibility.Collapsed -> replyLayoutState.collapseReplyLayout()
    ReplyLayoutVisibility.Opened -> replyLayoutState.openReplyLayout()
    ReplyLayoutVisibility.Expanded -> replyLayoutState.expandReplyLayout()
  }

  try {
    val target = anchors[newReplyLayoutVisibility]!!
    var prevValue = lastDragPosition

    draggableState.drag {
      Animatable<Int, AnimationVector1D>(
        initialValue = lastDragPosition,
        typeConverter = Int.VectorConverter,
        visibilityThreshold = 1
      ).animateTo(targetValue = target) {
        dragBy(this.value.toFloat() - prevValue)
        prevValue = this.value
      }
    }
  } catch (ignored: CancellationException) {
    // no-op
  }
}

@OptIn(ExperimentalMaterialApi::class)
private fun keyByPosition(
  density: Density,
  anchors: Map<ReplyLayoutVisibility, Int>,
  lastPosition: Int,
  position: Int,
  threshold: ThresholdConfig,
  velocity: Float
): ReplyLayoutVisibility {
  val targetValue = computeTarget(
    offset = position,
    lastValue = lastPosition,
    anchors = anchors.values,
    thresholds = { a, b -> with(threshold) { density.computeThreshold(a.toFloat(), b.toFloat()) } },
    velocity = velocity,
    velocityThreshold = with(density) { SwipeableDefaults.VelocityThreshold.toPx() }
  )

  val targetReplyLayoutVisibility = anchors.entries
    .firstOrNull { (_, value) -> value == targetValue }
    ?.key

  return targetReplyLayoutVisibility ?: ReplyLayoutVisibility.Collapsed
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
  class Animate(val target: Int) : DragRequest()
  class Snap(val target: Int) : DragRequest()
}
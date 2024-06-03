package com.github.k1rakishou.chan.ui.compose.post.ui.body

import android.os.SystemClock
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.currentRecomposeScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextLayoutResult
import com.github.k1rakishou.chan.ui.compose.post.state.PostCellClickableTextEvent
import com.github.k1rakishou.chan.ui.compose.post.state.PostCellTextState
import com.github.k1rakishou.chan.ui.compose.post.state.PostCommentClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.onEach
import kotlin.math.hypot

private data class SpoilersData(
  val start: Int,
  val end: Int,
  val spoilerPath: Path,
  val circlePath: Path,
  val annotationColor: Color,
  val touchPosition: Offset?,
  val startTime: Long?
)

@Composable
internal fun PostCellSpoilersOverlay(
  modifier: Modifier,
  animationDuration: Int,
  postCellTextState: PostCellTextState,
  textLayoutResultState: State<TextLayoutResult?>,
  onSpoilerClicked: (PostCommentClickable.Spoiler) -> Unit,
  onSpoilersOverlayInitialized: () -> Unit
) {
  val textLayoutResult by textLayoutResultState

  val spoilers = remember(key1 = textLayoutResult) {
    val localTextLayoutResult = textLayoutResult
    if (localTextLayoutResult == null) {
      return@remember mutableStateListOf<SpoilersData>()
    }

    val postSpoilers = postCellTextState.postSpoilers.value
    if (postSpoilers == null) {
      return@remember mutableStateListOf<SpoilersData>()
    }

    val spoilersDataList = postSpoilers.mapNotNull { spoiler ->
      val spoilerPath = localTextLayoutResult.getPathForRange(spoiler.start, spoiler.end)
      if (spoilerPath.isEmpty) {
        return@mapNotNull null
      }

      return@mapNotNull SpoilersData(
        start = spoiler.start,
        end = spoiler.end,
        spoilerPath = spoilerPath,
        circlePath = Path(),
        annotationColor = Color.Black,
        touchPosition = null,
        startTime = null
      )
    }

    val spoilersDataMutableList = mutableStateListOf<SpoilersData>()
    spoilersDataMutableList.addAll(spoilersDataList)

    onSpoilersOverlayInitialized()

    return@remember spoilersDataMutableList
  }

  LaunchedEffect(key1 = postCellTextState, key2 = spoilers) {
    postCellTextState.clickableTextEventsFlow
      .filterIsInstance<PostCellClickableTextEvent.Spoiler>()
      .onEach { spoiler ->
        val currentTextLayoutResult = textLayoutResultState.value
        if (currentTextLayoutResult == null) {
          return@onEach
        }

        val currentSpoilers = postCellTextState.postSpoilers.value
        if (currentSpoilers.isNullOrEmpty()) {
          return@onEach
        }

        val clickedSpoilerInfo = currentSpoilers.firstOrNull { clickableSpoiler ->
          clickableSpoiler.start == spoiler.start && clickableSpoiler.end == spoiler.end
        }

        if (clickedSpoilerInfo == null) {
          return@onEach
        }

        fun getAnimationIndex(): Int {
          return spoilers.indexOfFirst { animationData ->
            animationData.start == spoiler.start && animationData.end == spoiler.end
          }
        }

        when (spoiler.state) {
          PostCellClickableTextEvent.State.Pressed -> {
            val animationIndex = getAnimationIndex()
            if (animationIndex >= 0) {
              spoilers[animationIndex] = spoilers[animationIndex].copy(
                annotationColor = Color.Black,
                touchPosition = spoiler.position
              )
            }
          }
          PostCellClickableTextEvent.State.Canceled -> {
            val animationIndex = getAnimationIndex()
            if (animationIndex >= 0) {
              spoilers.removeAt(animationIndex)
            }

            postCellTextState.onCanceledConfirmed()
          }
          PostCellClickableTextEvent.State.Clicked -> {
            onSpoilerClicked(clickedSpoilerInfo)

            var animationIndex = getAnimationIndex()

            if (animationIndex >= 0) {
              spoilers[animationIndex] = spoilers[animationIndex]
                .copy(startTime = SystemClock.elapsedRealtime())
            }

            delay(animationDuration.toLong())

            animationIndex = getAnimationIndex()
            if (animationIndex >= 0) {
              spoilers.removeAt(animationIndex)
            }

            postCellTextState.onClickedConfirmed()
          }
        }
      }
      .collect()
  }

  val recomposeScope = currentRecomposeScope

  Canvas(
    modifier = modifier,
    onDraw = {
      var needInvalidation = false

      for (animation in spoilers) {
        val animating = drawSpoiler(animation, animationDuration)
        if (animating) {
          needInvalidation = true
        }
      }

      if (needInvalidation) {
        // TODO: compose post cells. Find a better solution.
        recomposeScope.invalidate()
      }
    }
  )
}

private fun DrawScope.drawSpoiler(
  animation: SpoilersData,
  animationDuration: Int
): Boolean {
  val startTime = animation.startTime
  val annotationPath = animation.spoilerPath
  val circlePath = animation.circlePath
  val annotationColor = animation.annotationColor
  val touchPosition = animation.touchPosition

  if (startTime != null && touchPosition != null) {
    return drawSpoilerRevealAnimation(
      annotationPath = annotationPath,
      startTime = startTime,
      animationDuration = animationDuration,
      touchPosition = touchPosition,
      circlePath = circlePath,
      annotationColor = annotationColor
    )
  }

  drawPath(
    path = annotationPath,
    style = Fill,
    brush = SolidColor(value = annotationColor)
  )

  return false
}

private fun DrawScope.drawSpoilerRevealAnimation(
  annotationPath: Path,
  startTime: Long,
  animationDuration: Int,
  touchPosition: Offset,
  circlePath: Path,
  annotationColor: Color
): Boolean {
  val pathBounds = annotationPath.getBounds()
  val pathWidth = pathBounds.width
  val pathHeight = pathBounds.height

  val animationProgressPreTransformed = (SystemClock.elapsedRealtime() - startTime).toFloat() / (animationDuration).toFloat()
  val animationProgress = FastOutLinearInEasing.transform(animationProgressPreTransformed)

  val circleRadius = hypot(pathWidth, pathHeight) * animationProgress
  if (circleRadius > 0f) {
    circlePath.rewind()
    circlePath.addOval(Rect(center = touchPosition, radius = circleRadius * 2f))
    circlePath.close()

    clipPath(path = circlePath, clipOp = ClipOp.Difference) {
      drawPath(
        path = annotationPath,
        style = Fill,
        brush = SolidColor(value = annotationColor)
      )
    }
  }

  // We are animating stuff so return true here
  return true
}
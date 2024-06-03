package com.github.k1rakishou.chan.ui.compose.post.ui.body

import android.os.SystemClock
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.currentRecomposeScope
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.clipPath
import com.github.k1rakishou.chan.core.parser.usecase.PostCommentApplier
import com.github.k1rakishou.chan.ui.compose.post.state.PostCellClickableTextEvent
import com.github.k1rakishou.chan.ui.compose.post.state.PostCellTextState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.onEach
import kotlin.math.hypot

private data class LinksAnimationData(
  val start: Int,
  val end: Int,
  val annotationPath: Path,
  val circlePath: Path,
  val annotationColor: Color,
  val touchPosition: Offset,
  val startTime: Long?
)

@Composable
internal fun PostCellLinksOverlay(
  modifier: Modifier,
  animationDuration: Int,
  postCellTextState: PostCellTextState
) {
  val animations = remember { mutableStateListOf<LinksAnimationData>() }

  LaunchedEffect(key1 = postCellTextState) {
    postCellTextState.clickableTextEventsFlow
      .filterIsInstance<PostCellClickableTextEvent.PostLinkable>()
      .onEach { postLinkable ->
        val clickedLinkInfo = postCellTextState.postClickableLinks.value.firstOrNull { clickableLink ->
          clickableLink.start == postLinkable.start && clickableLink.end == postLinkable.end
        }

        if (clickedLinkInfo == null) {
          return@onEach
        }

        val color = postCellTextState.clickedTextBackgroundColorMap.value[PostCommentApplier.ANNOTATION_POST_LINKABLE]
          ?: Color.Magenta

        fun getAnimationIndex(): Int {
          return animations.indexOfFirst { animationData ->
            animationData.start == postLinkable.start && animationData.end == postLinkable.end
          }
        }

        when (postLinkable.state) {
          PostCellClickableTextEvent.State.Pressed -> {
            val animationIndex = getAnimationIndex()
            if (animationIndex >= 0) {
              animations[animationIndex] = animations[animationIndex].copy(
                annotationPath = clickedLinkInfo.path,
                annotationColor = color,
                touchPosition = postLinkable.position
              )
            } else {
              animations += LinksAnimationData(
                start = postLinkable.start,
                end = postLinkable.end,
                annotationPath = clickedLinkInfo.path,
                circlePath = Path(),
                annotationColor = color,
                touchPosition = postLinkable.position,
                startTime = SystemClock.elapsedRealtime()
              )
            }
          }
          PostCellClickableTextEvent.State.Canceled -> {
            val animationIndex = getAnimationIndex()
            if (animationIndex >= 0) {
              animations.removeAt(animationIndex)
            }

            postCellTextState.onCanceledConfirmed()
          }
          PostCellClickableTextEvent.State.Clicked -> {
            delay(animationDuration.toLong())

            val animationIndex = getAnimationIndex()
            if (animationIndex >= 0) {
              animations.removeAt(animationIndex)
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
      for (animation in animations) {
        val startTime = animation.startTime
        if (startTime == null) {
          // Animation hasn't started yet
          continue
        }

        val annotationPath = animation.annotationPath
        val circlePath = animation.circlePath
        val annotationColor = animation.annotationColor
        val touchPosition = animation.touchPosition

        val pathBounds = annotationPath.getBounds()
        val pathWidth = pathBounds.width
        val pathHeight = pathBounds.height

        val animationProgressPreTransformed = (SystemClock.elapsedRealtime() - startTime).toFloat() / animationDuration.toFloat()
        val animationProgress = FastOutLinearInEasing.transform(animationProgressPreTransformed)

        val circleRadius = hypot(pathWidth, pathHeight) * animationProgress

        if (circleRadius > 0f) {
          circlePath.rewind()
          circlePath.addOval(Rect(center = touchPosition, radius = circleRadius * 2f))
          circlePath.close()

          clipPath(circlePath) {
            drawPath(
              path = annotationPath,
              style = Fill,
              brush = SolidColor(value = annotationColor)
            )
          }
        }

        // TODO: compose post cells. Find a better solution.
        recomposeScope.invalidate()
      }
    }
  )
}
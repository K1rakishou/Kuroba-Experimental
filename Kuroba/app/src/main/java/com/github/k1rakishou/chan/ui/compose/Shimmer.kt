package com.github.k1rakishou.chan.ui.compose

import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Taken and adopted from https://github.com/valentinilk/compose-shimmer
 * */

@Composable
fun rememberShimmerState(
  rotation: Float = 15f,
  cornerRadius: Dp = 0.dp,
  bgColor: Color = LocalChanTheme.current.backColorCompose
): ShimmerState {
  val cornerRadiusPx = with(LocalDensity.current) {
    remember(key1 = cornerRadius) { cornerRadius.toPx()  }
  }

  return remember {
    ShimmerState(
      rotation = rotation,
      bgColor = bgColor,
      cornerRadiusPx = cornerRadiusPx
    )
  }
}

@Composable
fun Shimmer(
  modifier: Modifier = Modifier,
  selectedOnBackColor: Color = LocalChanTheme.current.selectedOnBackColor,
  shimmerState: ShimmerState = rememberShimmerState()
) {
  val density = LocalDensity.current

  BoxWithConstraints(modifier = modifier) {
    val maxWidthDp = remember(key1 = maxWidth) { maxWidth }
    val maxHeightDp = remember(key1 = maxHeight) { maxHeight }

    val maxWidth = with(density) { remember(key1 = maxWidthDp) { maxWidthDp.toPx().toInt() } }
    val maxHeight = with(density) { remember(key1 = maxHeightDp) { maxHeightDp.toPx().toInt() } }

    LaunchedEffect(
      maxWidth,
      maxHeight,
      selectedOnBackColor,
      block = {
        shimmerState.start(
          maxWidth = maxWidth.toFloat(),
          maxHeight = maxHeight.toFloat(),
          selectedOnBackColor = selectedOnBackColor
        )
      }
    )

    val progress by shimmerState.progress

    Canvas(
      modifier = Modifier.size(maxWidthDp, maxHeightDp),
      onDraw = { shimmerState.draw(this, progress) }
    )
  }
}

@Stable
class ShimmerState(
  val rotation: Float,
  val bgColor: Color,
  val cornerRadiusPx: Float
) {
  private val animatedState = Animatable(0f)
  private val transformationMatrix = android.graphics.Matrix()

  private val reducedRotation = rotation
    .reduceRotation()
    .toRadian()

  private val animationSpec = infiniteRepeatable<Float>(
    animation = tween(
      1000,
      easing = LinearEasing,
    ),
    repeatMode = RepeatMode.Restart,
  )

  private val rectF = RectF()
  private val bgPaint by lazy {
    android.graphics.Paint().apply {
      isAntiAlias = true
      style = android.graphics.Paint.Style.FILL
      color = bgColor.toArgb()
    }
  }

  private var _paint: android.graphics.Paint? = null
  private var translationDistance = 0f
  private var pivotPoint = Offset.Unspecified

  val progress: State<Float>
    get() = animatedState.asState()

  private fun Float.reduceRotation(): Float {
    if (this < 0f) {
      throw IllegalArgumentException("The shimmer's rotation must be a positive number")
    }

    var rotation = this % 180   // 0..179, 0
    rotation -= 90              // -90..0..89, -90
    rotation = -abs(rotation)   // -90..0..-90
    return rotation + 90        // 0..90..0
  }

  private fun Float.toRadian(): Float = (this.toDouble() / 180.0 * Math.PI).toFloat()

  suspend fun start(maxWidth: Float, maxHeight: Float, selectedOnBackColor: Color) {
    animatedState.snapTo(0f)

    pivotPoint = -Offset(0f, 0f) + Rect(0f, 0f, maxWidth, maxHeight).center

    val colors = listOf(
      selectedOnBackColor.copy(alpha = 0.25f),
      selectedOnBackColor.copy(alpha = 1.00f),
      selectedOnBackColor.copy(alpha = 0.25f),
    )
    val colorStops = listOf(0.0f, 0.5f, 1.0f)

    val gradient = LinearGradientShader(
      from = Offset(-maxWidth / 2f, 0f),
      to = Offset(maxWidth / 2f, 0f),
      colors = colors,
      colorStops = colorStops,
    )

    _paint = android.graphics.Paint().apply {
      isAntiAlias = true
      style = android.graphics.Paint.Style.FILL
      xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
      shader = gradient
    }

    val distanceCornerToCenter = sqrt(maxWidth.pow(2) + maxHeight.pow(2))
    val beta = acos(maxWidth / distanceCornerToCenter)
    val alpha = beta - reducedRotation
    val widthOfShimmer = maxWidth / 2

    val distanceCornerToRotatedCenterLine = cos(alpha) * distanceCornerToCenter
    translationDistance = distanceCornerToRotatedCenterLine * 2 + widthOfShimmer

    animatedState.animateTo(
      targetValue = 1f,
      animationSpec = animationSpec,
    )
  }

  fun draw(drawScope: DrawScope, progress: Float) {
    with(drawScope) {
      if (this.size.isEmpty()) {
        return@with
      }

      val paint = _paint ?: return@with
      val shared = paint.shader ?: return@with

      rectF.set(0f, 0f, size.width, size.height)
      drawContext.canvas.nativeCanvas.drawRoundRect(rectF, cornerRadiusPx, cornerRadiusPx, bgPaint)

      val traversal = -translationDistance / 2 + translationDistance * progress + pivotPoint.x

      transformationMatrix.apply {
        reset()
        postTranslate(traversal, 0f)
        postRotate(rotation, pivotPoint.x, pivotPoint.y)
      }

      shared.setLocalMatrix(transformationMatrix)
      drawContext.canvas.nativeCanvas.drawRoundRect(rectF, cornerRadiusPx, cornerRadiusPx, paint)
    }
  }

}
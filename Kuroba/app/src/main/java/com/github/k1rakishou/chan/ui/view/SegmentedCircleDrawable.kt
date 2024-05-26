package com.github.k1rakishou.chan.ui.view

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

class SegmentedCircleDrawable : Drawable() {
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  private var currentPercent: Float = .1f

  fun percentage(percent: Float) {
    currentPercent = percent

    if (currentPercent < .1f) {
      currentPercent = .1f
    }

    if (currentPercent > 1f) {
      currentPercent =  1f
    }
  }

  fun setColor(color: Int) {
    paint.color = color
  }

  override fun setAlpha(alpha: Int) {
    paint.alpha = alpha
  }

  @Deprecated("Deprecated in Java")
  override fun getOpacity(): Int {
    return PixelFormat.TRANSLUCENT
  }

  override fun setColorFilter(colorFilter: ColorFilter?) {
    paint.colorFilter = colorFilter
  }

  override fun draw(canvas: Canvas) {
    val currentFilledSegmentsCount = (currentPercent * MAX_SEGMENTS_COUNT).toInt()
    val anglesPerSegment = 360f / MAX_SEGMENTS_COUNT

    repeat(currentFilledSegmentsCount) { index ->
      canvas.drawArc(
        bounds.left.toFloat(),
        bounds.top.toFloat(),
        bounds.right.toFloat(),
        bounds.bottom.toFloat(),
        index * anglesPerSegment,
        anglesPerSegment,
        true,
        paint
      )
    }
  }

  companion object {
    private const val MAX_SEGMENTS_COUNT = 32
  }
}
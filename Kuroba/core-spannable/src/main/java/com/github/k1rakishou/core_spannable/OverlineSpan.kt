package com.github.k1rakishou.core_spannable

import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.LineBackgroundSpan

class OverlineSpan : LineBackgroundSpan {
  private val themeEngine by lazy { SpannableModuleInjector.themeEngine }

  override fun drawBackground(
    canvas: Canvas,
    paint: Paint,
    left: Int,
    right: Int,
    top: Int,
    baseline: Int,
    bottom: Int,
    text: CharSequence,
    start: Int,
    end: Int,
    lineNumber: Int
  ) {
    val oldStyle = paint.style
    val oldStrokeWidth = paint.strokeWidth
    val strokeWidth = themeEngine.density * 1f

    paint.style = Paint.Style.STROKE
    paint.strokeWidth = strokeWidth

    val lineEnd = paint.measureText(text, start, end)
    canvas.drawLine(left.toFloat(), top.toFloat(), lineEnd, top.toFloat(), paint)

    paint.style = oldStyle
    paint.strokeWidth = oldStrokeWidth
  }

}
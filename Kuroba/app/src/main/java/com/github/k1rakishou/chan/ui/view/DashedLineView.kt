package com.github.k1rakishou.chan.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class DashedLineView @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defAttrStyle: Int = 0
) : View(context, attributeSet, defAttrStyle) {
  private var drawNormalLine: Boolean = true
  private var horizontal: Boolean = false
  private val path = Path()

  private val normalPaint = Paint().apply {
    setColor(Color.RED)
    flags = Paint.ANTI_ALIAS_FLAG
    style = Paint.Style.FILL
  }

  private val dottedPaint = Paint().apply {
    setColor(Color.GREEN)
    flags = Paint.ANTI_ALIAS_FLAG
    style = Paint.Style.STROKE
    pathEffect = DashPathEffect(floatArrayOf(15f, 10f), 0f)
  }

  init {
    setWillNotDraw(false)
  }

  fun updateColor(newColor: Int) {
    var updated = false

    if (dottedPaint.color != newColor) {
      dottedPaint.color = newColor
      updated = true
    }

    if (normalPaint.color != newColor) {
      normalPaint.color = newColor
      updated = true
    }

    if (!updated) {
      return
    }

    invalidate()
  }

  fun drawNormalLine(normalLine: Boolean) {
    if (this.drawNormalLine == normalLine) {
      return
    }

    this.drawNormalLine = normalLine
    invalidate()
  }

  fun horizontal(horiz: Boolean) {
    if (this.horizontal == horiz) {
      return
    }

    this.horizontal = horiz
    invalidate()
  }

  override fun setAlpha(alpha: Float) {
    dottedPaint.alpha = (alpha * 255f).toInt()
    normalPaint.alpha = (alpha * 255f).toInt()

    super.setAlpha(alpha)
  }

  override fun onDraw(canvas: Canvas) {
    if (drawNormalLine) {
      if (normalPaint.color == 0 || normalPaint.alpha <= 0) {
        return
      }

      canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), normalPaint)
    } else {
      if (dottedPaint.color == 0 || dottedPaint.alpha <= 0) {
        return
      }

      if (dottedPaint.strokeWidth.toInt() != width) {
        dottedPaint.strokeWidth = width.toFloat()
      }

      path.rewind()

      if (horizontal) {
        path.moveTo(0f, height.toFloat() / 2f)
        path.lineTo(width.toFloat(), height.toFloat() / 2f)
      } else {
        path.moveTo(width.toFloat() / 2f, 0f)
        path.lineTo(width.toFloat() / 2f, height.toFloat())
      }

      canvas.drawPath(path, dottedPaint)
    }
  }

}
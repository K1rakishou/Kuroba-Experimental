package com.github.k1rakishou.chan.ui.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.painter.Painter

class SimpleSquarePainter(
  color: Color
) : Painter() {
  private val paint = Paint()

  init {
    paint.isAntiAlias = true
    paint.color = color
  }

  override val intrinsicSize: Size
    get() = Size.Unspecified

  override fun DrawScope.onDraw() {
    val width = drawContext.size.width
    val height = drawContext.size.height

    this.drawIntoCanvas { canvas ->
      canvas.drawRect(0f, 0f, width, height, paint)
    }
  }
}
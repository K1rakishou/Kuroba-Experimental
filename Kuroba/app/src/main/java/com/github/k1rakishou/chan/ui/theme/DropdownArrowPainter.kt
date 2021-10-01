package com.github.k1rakishou.chan.ui.theme

import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.rotate
import androidx.compose.ui.unit.LayoutDirection

class DropdownArrowPainter(
  private val color: Color,
  down: Boolean
) : Painter() {
  private val paint = Paint()
  private var rotation = 0f

  init {
    rotation = if (down) 1f else 0f
    paint.isAntiAlias = true
    paint.color = color
  }

  override val intrinsicSize: Size
    get() = Size.Unspecified

  private val triangleShape = GenericShape { size, _ ->
    val height = size.height / 2f

    moveTo(size.width / 2f, 0f)
    lineTo(size.width, height)
    lineTo(0f, height)
  }

  override fun DrawScope.onDraw() {
    val width = drawContext.size.width
    val height = drawContext.size.height

    this.drawIntoCanvas { canvas ->
      canvas.save()
      canvas.rotate(rotation * 180f, (width / 2f), (height / 2f))

      val outline = triangleShape.createOutline(
        size = drawContext.size,
        layoutDirection = LayoutDirection.Ltr,
        density = this
      )

      canvas.drawOutline(outline, paint)
      canvas.restore()
    }
  }
}
package com.github.k1rakishou.chan.ui.view.attach

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.core_themes.ThemeEngine
import javax.inject.Inject


class AttachNewFileButton @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  attrDefStyle: Int = 0
) : View(context, attributeSet, attrDefStyle), ThemeEngine.ThemeChangesListener {

  @Inject
  lateinit var themeEngine: ThemeEngine

  private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  private var addIconDrawable: Drawable

  init {
    setWillNotDraw(false)

    addIconDrawable = ContextCompat.getDrawable(context, R.drawable.ic_add_white_24dp)!!

    if (!isInEditMode) {
      AppModuleAndroidUtils.extractActivityComponent(context)
        .inject(this)

      init(Color.RED, dp(4f).toFloat())
    } else {
      init(Color.RED, 8f)
    }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()

    themeEngine.addListener(this)
    onThemeChanged()
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    themeEngine.removeListener(this)
  }

  override fun onThemeChanged() {
    val tintColor = ThemeEngine.resolveDrawableTintColor(themeEngine.chanTheme.isBackColorDark)

    paint.color = tintColor
    paint.alpha = 160
    addIconDrawable = themeEngine.tintDrawable(addIconDrawable, tintColor)

    invalidate()
  }

  private fun init(color: Int, width: Float) {
    paint.style = Paint.Style.STROKE
    paint.color = color
    paint.strokeWidth = width
    paint.pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
  }

  override fun onDraw(canvas: Canvas) {
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

    val centerX = width / 2
    val centerY = height / 2

    addIconDrawable.setBounds(
      centerX - (ADD_ICON_SIZE / 2),
      centerY - (ADD_ICON_SIZE / 2),
      centerX + (ADD_ICON_SIZE / 2),
      centerY + (ADD_ICON_SIZE / 2)
    )

    addIconDrawable.draw(canvas)
  }

  companion object {
    private var ADD_ICON_SIZE = dp(32f)
  }
}
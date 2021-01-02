package com.github.k1rakishou.chan.ui.theme.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.constraintlayout.widget.ConstraintLayout
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.core_themes.IColorizableWidget
import com.github.k1rakishou.core_themes.ThemeEngine
import javax.inject.Inject

class TouchBlockingConstraintLayout @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defStyle: Int = 0
) : ConstraintLayout(context, attributeSet, defStyle), IColorizableWidget {

  @Inject
  lateinit var themeEngine: ThemeEngine

  init {
    if (!isInEditMode) {
      AppModuleAndroidUtils.extractActivityComponent(context)
        .inject(this)
    }
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    return true
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()

    applyColors()
  }

  override fun applyColors() {
    if (isInEditMode) {
      return
    }

    setBackgroundColor(themeEngine.chanTheme.backColor)
  }

}
package com.github.k1rakishou.chan.ui.theme.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.ListView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.ViewUtils.changeEdgeEffect
import com.github.k1rakishou.core_themes.IColorizableWidget
import com.github.k1rakishou.core_themes.ThemeEngine
import javax.inject.Inject

open class ColorizableListView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  val defStyleAttr: Int = android.R.attr.listViewStyle
) : ListView(context, attrs, defStyleAttr), IColorizableWidget {

  @Inject
  lateinit var themeEngine: ThemeEngine

  init {
    if (!isInEditMode) {
      AppModuleAndroidUtils.extractActivityComponent(context)
        .inject(this)
    }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()

    applyColors()
  }

  override fun applyColors() {
    if (isInEditMode) {
      return
    }

    changeEdgeEffect(themeEngine.chanTheme)
  }

}
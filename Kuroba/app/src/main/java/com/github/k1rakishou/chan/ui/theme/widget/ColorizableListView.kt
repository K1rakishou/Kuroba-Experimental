package com.github.k1rakishou.chan.ui.theme.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.ListView
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import com.github.k1rakishou.chan.utils.ViewUtils.changeEdgeEffect
import javax.inject.Inject

class ColorizableListView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  val defStyleAttr: Int = android.R.attr.listViewStyle
) : ListView(context, attrs, defStyleAttr), IColorizableWidget {

  @Inject
  protected lateinit var themeEngine: ThemeEngine

  init {
    if (!isInEditMode) {
      Chan.inject(this)
    }
  }

  override fun onFinishInflate() {
    super.onFinishInflate()

    applyColors()
  }

  override fun applyColors() {
    changeEdgeEffect(themeEngine)
  }

}
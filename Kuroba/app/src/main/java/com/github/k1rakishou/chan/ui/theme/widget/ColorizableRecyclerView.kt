package com.github.k1rakishou.chan.ui.theme.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.EdgeEffect
import androidx.recyclerview.widget.RecyclerView
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import javax.inject.Inject

open class ColorizableRecyclerView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  val defStyleAttr: Int = R.attr.recyclerViewStyle
) : RecyclerView(context, attrs, defStyleAttr), IColorizableWidget {

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
    edgeEffectFactory = object : RecyclerView.EdgeEffectFactory() {
      override fun createEdgeEffect(view: RecyclerView, direction: Int): EdgeEffect {
        return EdgeEffect(view.context).apply { color = themeEngine.chanTheme.accentColor }
      }
    }
  }

}
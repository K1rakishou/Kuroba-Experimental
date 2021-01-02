package com.github.k1rakishou.chan.features.search.epoxy

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.airbnb.epoxy.ModelView
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.core_themes.ThemeEngine.Companion.manipulateColor
import javax.inject.Inject

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
class EpoxySearchPostGapView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), ThemeEngine.ThemeChangesListener {

  @Inject
  lateinit var themeEngine: ThemeEngine

  init {
    inflate(context, R.layout.epoxy_search_post_gap_view, this)

    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    onThemeChanged()
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    themeEngine.addListener(this)
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    themeEngine.removeListener(this)
  }

  override fun onThemeChanged() {
    setBackgroundColor(manipulateColor(themeEngine.chanTheme.backColor, .8f))
  }

}
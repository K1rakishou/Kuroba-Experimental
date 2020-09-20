package com.github.adamantcheese.chan.features.search.epoxy

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.airbnb.epoxy.ModelView
import com.github.adamantcheese.chan.Chan
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.ui.theme.ThemeHelper
import javax.inject.Inject

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
class EpoxySearchPostGapView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

  @Inject
  lateinit var themeHelper: ThemeHelper

  init {
    Chan.inject(this)
    inflate(context, R.layout.epoxy_search_post_gap_view, this)
    setBackgroundColor(themeHelper.theme.primaryColor.color)
  }

}
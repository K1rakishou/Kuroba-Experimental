package com.github.adamantcheese.chan.ui.epoxy

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.github.adamantcheese.chan.Chan
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.ui.theme.ThemeHelper
import com.github.adamantcheese.common.updatePaddings
import javax.inject.Inject

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
class EpoxyDividerView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

  @Inject
  lateinit var themeHelper: ThemeHelper

  private val divider: View

  init {
    Chan.inject(this)
    View.inflate(context, R.layout.epoxy_divider_view, this)

    divider = findViewById<View>(R.id.divider)
    divider.setBackgroundColor(themeHelper.theme.dividerColor)
  }

  @ModelProp
  fun setTopPadding(topPaddingPx: Int) {
    divider.updatePaddings(top = topPaddingPx)
  }

  @ModelProp
  fun setBottomPadding(bottomPaddingPx: Int) {
    divider.updatePaddings(bottom = bottomPaddingPx)
  }

}
package com.github.k1rakishou.chan.ui.epoxy

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.common.updateMargins
import com.github.k1rakishou.core_themes.ThemeEngine
import javax.inject.Inject

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
class EpoxyDividerView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

  @Inject
  lateinit var themeEngine: ThemeEngine

  private val divider: View

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    View.inflate(context, R.layout.epoxy_divider_view, this)

    divider = findViewById(R.id.divider)
  }

  @ModelProp
  fun updateMargins(margins: Margins?) {
    if (margins == null) {
      divider.updateMargins(left = 0, right = 0)
      return
    }

    divider.updateMargins(
      left = margins.left,
      right = margins.right
    )
  }

  data class Margins(
    val left: Int,
    val right: Int
  )

}
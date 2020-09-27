package com.github.k1rakishou.chan.features.settings.epoxy

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import javax.inject.Inject

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
class EpoxySettingsGroupTitle  @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

  @Inject
  lateinit var themeEngine: ThemeEngine

  private val groupTitle: TextView

  init {
    Chan.inject(this)
    View.inflate(context, R.layout.epoxy_settings_group_title, this)

    groupTitle = findViewById(R.id.group_title)
    groupTitle.setTextColor(themeEngine.chanTheme.textSecondaryColor)
  }

  @ModelProp
  fun setGroupTitle(title: String?) {
    if (title != null) {
      groupTitle.visibility = View.VISIBLE
      groupTitle.text = title
    } else {
      groupTitle.visibility = View.GONE
    }
  }

}
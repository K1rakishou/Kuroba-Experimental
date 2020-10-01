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
class EpoxyNoSettingsFoundView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), ThemeEngine.ThemeChangesListener {

  @Inject
  lateinit var themeEngine: ThemeEngine

  private val messageView: TextView

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    themeEngine.addListener(this)
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    themeEngine.removeListener(this)
  }

  override fun onThemeChanged() {
    updateMessageTextColor()
  }

  init {
    Chan.inject(this)
    View.inflate(context, R.layout.epoxy_no_settings_found, this)

    messageView = findViewById(R.id.message_view)
  }

  @ModelProp
  fun setQuery(query: String) {
    messageView.text = context.getString(R.string.epoxy_no_settings_found_by_query, query)
    updateMessageTextColor()
  }

  private fun updateMessageTextColor() {
    messageView.setTextColor(themeEngine.chanTheme.textColorSecondary)
  }

}
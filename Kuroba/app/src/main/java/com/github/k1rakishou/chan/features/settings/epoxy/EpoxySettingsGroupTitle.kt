package com.github.k1rakishou.chan.features.settings.epoxy

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.core_themes.ThemeEngine
import javax.inject.Inject

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
class EpoxySettingsGroupTitle  @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), ThemeEngine.ThemeChangesListener {

  @Inject
  lateinit var themeEngine: ThemeEngine

  private val groupTitle: TextView

  init {
    View.inflate(context, R.layout.epoxy_settings_group_title, this)

    AppModuleAndroidUtils.extractStartActivityComponent(context)
      .inject(this)

    groupTitle = findViewById(R.id.group_title)
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
    updateGroupTitleTextColor()
  }

  @ModelProp
  fun setGroupTitle(title: String?) {
    groupTitle.text = title

    if (title == null) {
      groupTitle.visibility = View.GONE
      return
    }

    groupTitle.visibility = View.VISIBLE
    updateGroupTitleTextColor()
  }

  private fun updateGroupTitleTextColor() {
    groupTitle.setTextColor(themeEngine.chanTheme.textColorSecondary)
  }

}
package com.github.k1rakishou.chan.ui.view.floating_menu.epoxy

import android.content.Context
import android.content.res.ColorStateList
import android.text.method.ScrollingMovementMethod
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
class EpoxyHeaderListMenuRow @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), ThemeEngine.ThemeChangesListener {

  @Inject
  lateinit var themeEngine: ThemeEngine

  private val title: TextView

  init {
    View.inflate(context, R.layout.epoxy_header_list_menu_row, this)

    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    title = findViewById(R.id.title)
    title.movementMethod = ScrollingMovementMethod()
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
    updateTitleColor()
  }

  @ModelProp
  fun setTitle(text: String) {
    title.text = text
    updateTitleColor()
  }

  private fun updateTitleColor() {
    val colorStateList = ColorStateList(
      arrayOf(intArrayOf(android.R.attr.state_enabled), intArrayOf(-android.R.attr.state_enabled)),
      intArrayOf(themeEngine.chanTheme.textColorPrimary, themeEngine.chanTheme.textColorSecondary)
    )

    title.setTextColor(colorStateList)
  }

}
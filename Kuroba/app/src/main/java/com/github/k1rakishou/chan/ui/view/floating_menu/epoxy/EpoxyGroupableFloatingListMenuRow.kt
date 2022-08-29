package com.github.k1rakishou.chan.ui.view.floating_menu.epoxy

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.airbnb.epoxy.CallbackProp
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableRadioButton
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.core_themes.ThemeEngine
import javax.inject.Inject

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
class EpoxyGroupableFloatingListMenuRow @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), ThemeEngine.ThemeChangesListener {

  @Inject
  lateinit var themeEngine: ThemeEngine

  private val holder: LinearLayout
  private val title: TextView
  private val checkbox: ColorizableRadioButton

  init {
    View.inflate(context, R.layout.epoxy_groupable_floating_list_menu_row, this)

    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    holder = findViewById(R.id.holder)
    title = findViewById(R.id.title)
    checkbox = findViewById(R.id.checkbox)
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

  @ModelProp
  fun setSettingEnabled(isEnabled: Boolean) {
    if (isEnabled) {
      holder.alpha = 1f
      title.alpha = 1f

      holder.isClickable = true
      holder.isFocusable = true
    } else {
      holder.alpha = .5f
      title.alpha = .5f

      holder.isClickable = false
      holder.isFocusable = false
    }
  }

  @ModelProp
  fun setChecked(isChecked: Boolean) {
    checkbox.isChecked = isChecked
  }

  @CallbackProp
  fun setCallback(callback: ((checked: Boolean) -> Unit)?) {
    if (callback == null) {
      holder.setOnClickListener(null)
      return
    }

    holder.setOnClickListener {
      checkbox.isChecked = !checkbox.isChecked
      callback.invoke(checkbox.isChecked)
    }
  }

  private fun updateTitleColor() {
    val colorStateList = ColorStateList(
      arrayOf(intArrayOf(android.R.attr.state_enabled), intArrayOf(-android.R.attr.state_enabled)),
      intArrayOf(themeEngine.chanTheme.textColorPrimary, themeEngine.chanTheme.textColorSecondary)
    )

    title.setTextColor(colorStateList)
  }

}
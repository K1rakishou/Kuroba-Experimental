package com.github.k1rakishou.chan.features.drawer.epoxy

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout
import com.airbnb.epoxy.CallbackProp
import com.airbnb.epoxy.ModelView
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.core_themes.ThemeEngine
import javax.inject.Inject

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
class EpoxyHistoryHeaderView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr),
  ThemeEngine.ThemeChangesListener {
  private val themeSwitchButton: AppCompatImageView
  private val drawerSettingsButton: AppCompatImageView

  @Inject
  lateinit var themeEngine: ThemeEngine

  init {
    inflate(context, R.layout.epoxy_history_header_view, this)

    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    themeSwitchButton = findViewById(R.id.drawer_header_theme_switch)
    drawerSettingsButton = findViewById(R.id.drawer_header_settings)

    AndroidUtils.setBoundlessRoundRippleBackground(themeSwitchButton)
    AndroidUtils.setBoundlessRoundRippleBackground(drawerSettingsButton)

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
    if (themeEngine.chanTheme.isDarkTheme) {
      themeSwitchButton.setImageDrawable(
        themeEngine.tintDrawable(context, R.drawable.ic_baseline_wb_sunny_24)
      )
    } else {
      themeSwitchButton.setImageDrawable(
        themeEngine.tintDrawable(context, R.drawable.ic_baseline_nights_stay_24)
      )
    }

    drawerSettingsButton.setImageDrawable(
      themeEngine.tintDrawable(context, R.drawable.ic_more_vert_white_24dp)
    )
  }

  @CallbackProp
  fun onThemeSwitcherClicked(func: (() -> Unit)?) {
    if (func == null) {
      themeSwitchButton.setOnClickListener(null)
      return
    }

    themeSwitchButton.setOnClickListener { func.invoke() }
  }

  @CallbackProp
  fun onDrawerSettingsClicked(func: (() -> Unit)?) {
    if (func == null) {
      drawerSettingsButton.setOnClickListener(null)
      return
    }

    drawerSettingsButton.setOnClickListener { func.invoke() }
  }

}
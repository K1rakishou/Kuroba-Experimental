package com.github.k1rakishou.chan.features.settings.epoxy

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import com.airbnb.epoxy.CallbackProp
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.settings.SettingNotificationType
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.core_themes.ThemeEngine
import javax.inject.Inject

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
class EpoxyLinkSetting @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), ThemeEngine.ThemeChangesListener {

  @Inject
  lateinit var themeEngine: ThemeEngine

  private val topDescriptor: TextView
  private val bottomDescription: TextView
  private val settingViewHolder: LinearLayout
  private val notificationIcon: AppCompatImageView

  init {
    inflate(context, R.layout.epoxy_setting_link, this)

    AppModuleAndroidUtils.extractStartActivityComponent(context)
      .inject(this)

    topDescriptor = findViewById(R.id.top)
    bottomDescription = findViewById(R.id.bottom)
    settingViewHolder = findViewById(R.id.preference_item)
    notificationIcon = findViewById(R.id.setting_notification_icon)
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
    updateTopDescriptionTextColor()
    updateBottomDescriptionTextColor()
  }

  @ModelProp
  fun setTopDescription(description: String) {
    topDescriptor.text = description
    updateTopDescriptionTextColor()
  }

  @ModelProp
  fun setBottomDescription(description: String?) {
    bottomDescription.text = description

    if (description == null) {
      bottomDescription.visibility = View.GONE
      return
    }

    bottomDescription.visibility = View.VISIBLE
    updateBottomDescriptionTextColor()
  }

  @ModelProp
  fun setSettingEnabled(isEnabled: Boolean) {
    if (isEnabled) {
      topDescriptor.alpha = 1f
      bottomDescription.alpha = 1f
      notificationIcon.alpha = 1f

      settingViewHolder.isClickable = true
      settingViewHolder.isFocusable = true
    } else {
      topDescriptor.alpha = .5f
      bottomDescription.alpha = .5f
      notificationIcon.alpha = .5f

      settingViewHolder.isClickable = false
      settingViewHolder.isFocusable = false
    }
  }

  @ModelProp
  fun bindNotificationIcon(settingNotificationType: SettingNotificationType) {
    if (settingNotificationType !== SettingNotificationType.Default) {
      val tintColor = context.resources.getColor(settingNotificationType.notificationIconTintColor)

      AndroidUtils.updatePaddings(notificationIcon, dp(16f), dp(16f), -1, -1)
      notificationIcon.setColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
      notificationIcon.visibility = View.VISIBLE
    } else {
      notificationIcon.visibility = View.GONE
    }
  }

  @CallbackProp
  fun setClickListener(callback: ((View) -> Unit)?) {
    if (callback == null) {
      settingViewHolder.setOnClickListener(null)
      return
    }

    settingViewHolder.setOnClickListener { view -> callback.invoke(view) }
  }

  private fun updateTopDescriptionTextColor() {
    topDescriptor.setTextColor(
      ColorStateList(
        arrayOf(
          intArrayOf(android.R.attr.state_enabled),
          intArrayOf(-android.R.attr.state_enabled)
        ),
        intArrayOf(
          themeEngine.chanTheme.textColorPrimary,
          themeEngine.chanTheme.getDisabledTextColor(themeEngine.chanTheme.textColorPrimary)
        )
      )
    )
  }

  private fun updateBottomDescriptionTextColor() {
    bottomDescription.setTextColor(
      ColorStateList(
        arrayOf(
          intArrayOf(android.R.attr.state_enabled),
          intArrayOf(-android.R.attr.state_enabled)
        ),
        intArrayOf(
          themeEngine.chanTheme.textColorSecondary,
          themeEngine.chanTheme.getDisabledTextColor(themeEngine.chanTheme.textColorSecondary)
        )
      )
    )
  }
}
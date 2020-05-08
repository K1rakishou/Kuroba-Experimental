package com.github.adamantcheese.chan.features.settings.epoxy

import android.content.Context
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
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.ui.settings.SettingNotificationType
import com.github.adamantcheese.chan.utils.AndroidUtils
import com.github.adamantcheese.chan.utils.AndroidUtils.dp

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
class EpoxyLinkSetting @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
  private val topDescriptor: TextView
  private val bottomDescription: TextView
  private val settingViewHolder: LinearLayout
  private val notificationIcon: AppCompatImageView

  init {
    inflate(context, R.layout.epoxy_setting_link, this)

    topDescriptor = findViewById(R.id.top)
    bottomDescription = findViewById(R.id.bottom)
    settingViewHolder = findViewById(R.id.preference_item)
    notificationIcon = findViewById(R.id.setting_notification_icon)
  }

  @ModelProp
  fun setTopDescription(description: String) {
    topDescriptor.text = description
  }

  @ModelProp
  fun setBottomDescription(description: String?) {
    if (description != null) {
      bottomDescription.visibility = View.VISIBLE
      bottomDescription.text = description
    } else {
      bottomDescription.visibility = View.GONE
    }
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
}
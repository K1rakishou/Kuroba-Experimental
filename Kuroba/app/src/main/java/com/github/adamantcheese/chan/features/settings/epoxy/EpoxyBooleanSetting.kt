package com.github.adamantcheese.chan.features.settings.epoxy

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.SwitchCompat
import com.airbnb.epoxy.CallbackProp
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.github.adamantcheese.chan.R

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
class EpoxyBooleanSetting @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
  private val topDescriptor: TextView
  private val bottomDescription: TextView
  private val settingViewHolder: LinearLayout
  private val switcher: SwitchCompat
  private val notificationIcon: AppCompatImageView

  init {
    View.inflate(context, R.layout.epoxy_setting_boolean, this)

    topDescriptor = findViewById(R.id.top)
    bottomDescription = findViewById(R.id.bottom)
    settingViewHolder = findViewById(R.id.preference_item)
    switcher = findViewById(R.id.switcher)
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

  @CallbackProp
  fun setClickListener(callback: (() -> Unit)?) {
    if (callback == null) {
      settingViewHolder.setOnClickListener(null)
      return
    }

    settingViewHolder.setOnClickListener { callback.invoke() }
  }

  @CallbackProp
  fun setCheckListener(callback: ((Boolean) -> Unit)?) {
    if (callback == null) {
      switcher.setOnCheckedChangeListener(null)
      return
    }

    switcher.setOnCheckedChangeListener { _, isChecked -> callback.invoke(isChecked) }
  }

}
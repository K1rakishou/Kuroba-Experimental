package com.github.adamantcheese.chan.features.settings.epoxy

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.airbnb.epoxy.CallbackProp
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.github.adamantcheese.chan.R

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
class EpoxyLinkSetting @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
  private val topDescriptor: TextView
  private val bottomDescription: TextView
  private val settingViewHolder: LinearLayout

  init {
    inflate(context, R.layout.epoxy_setting_link, this)

    topDescriptor = findViewById(R.id.top)
    bottomDescription = findViewById(R.id.bottom)
    settingViewHolder = findViewById(R.id.preference_item)
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
}
package com.github.adamantcheese.chan.ui.view.floating_menu

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
class EpoxyFloatingListMenuRow @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
  private val holder: LinearLayout
  private val title: TextView

  init {
    View.inflate(context, R.layout.epoxy_floating_list_menu_row, this)

    holder = findViewById(R.id.holder)
    title = findViewById(R.id.title)
  }

  @ModelProp
  fun setTitle(text: String) {
    title.text = text
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

  @CallbackProp
  fun setCallback(callback: (() -> Unit)?) {
    if (callback == null) {
      holder.setOnClickListener(null)
      return
    }

    holder.setOnClickListener { callback.invoke() }
  }

}
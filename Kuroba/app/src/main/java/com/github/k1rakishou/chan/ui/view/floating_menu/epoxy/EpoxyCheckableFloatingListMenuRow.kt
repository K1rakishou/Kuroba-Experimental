package com.github.k1rakishou.chan.ui.view.floating_menu.epoxy

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.airbnb.epoxy.CallbackProp
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.github.k1rakishou.chan.R
import com.google.android.material.checkbox.MaterialCheckBox

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
class EpoxyCheckableFloatingListMenuRow @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
  private val holder: LinearLayout
  private val title: TextView
  private val checkbox: MaterialCheckBox

  init {
    View.inflate(context, R.layout.epoxy_checkable_floating_list_menu_row, this)

    holder = findViewById(R.id.holder)
    title = findViewById(R.id.title)
    checkbox = findViewById(R.id.checkbox)
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

}
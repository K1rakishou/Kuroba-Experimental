package com.github.k1rakishou.chan.features.search.epoxy

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableButton

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
class EpoxyButtonView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
  private val button: ColorizableButton

  init {
    inflate(context, R.layout.epoxy_button_view, this)

    button = findViewById(R.id.epoxy_button)
  }

  @ModelProp
  fun title(buttonTitle: String) {
    button.text = buttonTitle
  }

  @ModelProp(options = [ModelProp.Option.NullOnRecycle, ModelProp.Option.IgnoreRequireHashCode])
  fun setOnButtonClickListener(listener: (() -> Unit)?) {
    if (listener == null) {
      button.setOnClickListener(null)
      return
    }

    button.setOnClickListener { listener.invoke() }
  }

}
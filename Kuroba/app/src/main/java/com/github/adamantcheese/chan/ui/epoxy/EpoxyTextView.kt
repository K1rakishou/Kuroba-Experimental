package com.github.adamantcheese.chan.ui.epoxy

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.TextView
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.github.adamantcheese.chan.R

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_MATCH_HEIGHT)
class EpoxyTextView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
  private val textView: TextView

  init {
    inflate(context, R.layout.epoxy_text_view, this)

    textView = findViewById(R.id.text_view)
  }

  @ModelProp
  fun setMessage(text: String) {
    textView.text = text
  }

}
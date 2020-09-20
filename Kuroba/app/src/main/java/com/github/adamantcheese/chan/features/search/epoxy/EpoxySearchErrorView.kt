package com.github.adamantcheese.chan.features.search.epoxy

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.airbnb.epoxy.CallbackProp
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.github.adamantcheese.chan.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
class EpoxySearchErrorView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
  private val errorText: MaterialTextView
  private val retryButton: MaterialButton

  init {
    inflate(context, R.layout.epoxy_search_error_view, this)

    errorText = findViewById(R.id.search_post_error_text)
    retryButton = findViewById(R.id.search_post_error_retry_button)
  }

  @ModelProp
  fun setErrorText(text: String) {
    errorText.text = text
  }

  @CallbackProp
  fun setClickListener(listener: (() -> Unit)?) {
    if (listener == null) {
      retryButton.setOnClickListener(null)
      return
    }

    retryButton.setOnClickListener { listener.invoke() }
  }

}
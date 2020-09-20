package com.github.adamantcheese.chan.features.search.epoxy

import android.content.Context
import android.text.TextWatcher
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.core.widget.doAfterTextChanged
import com.airbnb.epoxy.CallbackProp
import com.airbnb.epoxy.ModelView
import com.github.adamantcheese.chan.R
import com.google.android.material.textfield.TextInputEditText

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
class EpoxySearchInputView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
  private val inputEditText: TextInputEditText
  private var textWatcher: TextWatcher? = null

  init {
    inflate(context, R.layout.epoxy_search_input_view, this)

    inputEditText = findViewById(R.id.input_edit_text)
  }

  @CallbackProp
  fun setOnTextEnteredListener(listener: ((String) -> Unit)?) {
    if (listener == null) {
      textWatcher?.let { tw -> inputEditText.removeTextChangedListener(tw) }
      inputEditText.text = null
      return
    }

    textWatcher = inputEditText.doAfterTextChanged {
      inputEditText.text?.let { editable -> listener.invoke(editable.toString()) }
    }
  }

}
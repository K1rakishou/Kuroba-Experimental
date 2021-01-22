package com.github.k1rakishou.chan.features.search.epoxy

import android.content.Context
import android.text.TextWatcher
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.core.widget.doAfterTextChanged
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEditText

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
class EpoxySearchInputView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
  private val inputEditText: ColorizableEditText
  private var textWatcher: TextWatcher? = null

  init {
    inflate(context, R.layout.epoxy_search_input_view, this)

    inputEditText = findViewById(R.id.input_edit_text)
    inputEditText.text = null
  }

  @ModelProp(ModelProp.Option.DoNotHash)
  fun setInitialQuery(query: String) {
    val editable = inputEditText.text
      ?: return

    if (editable.toString() == query) {
      return
    }

    if (textWatcher == null) {
      editable.replace(0, editable.length, query)
      return
    }

    inputEditText.removeTextChangedListener(textWatcher)
    editable.replace(0, editable.length, query)
    inputEditText.addTextChangedListener(textWatcher)
  }

  @ModelProp(ModelProp.Option.IgnoreRequireHashCode)
  fun setOnTextEnteredListener(listener: ((String) -> Unit)?) {
    if (listener == null) {
      textWatcher?.let { tw -> inputEditText.removeTextChangedListener(tw) }
      inputEditText.text = null
      return
    }

    textWatcher?.let { tw -> inputEditText.removeTextChangedListener(tw) }
    textWatcher = null

    textWatcher = inputEditText.doAfterTextChanged {
      inputEditText.text?.let { editable -> listener.invoke(editable.toString()) }
    }
  }

}
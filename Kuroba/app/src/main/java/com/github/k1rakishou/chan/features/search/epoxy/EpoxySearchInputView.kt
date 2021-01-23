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
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableTextInputLayout

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
class EpoxySearchInputView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
  private val searchInputLayout: ColorizableTextInputLayout
  private val searchInputEditText: ColorizableEditText
  private var textWatcher: TextWatcher? = null

  init {
    inflate(context, R.layout.epoxy_search_input_view, this)

    searchInputEditText = findViewById(R.id.search_input_edit_text)
    searchInputEditText.text = null

    searchInputLayout = findViewById(R.id.search_input_layout)
  }

  @ModelProp(ModelProp.Option.DoNotHash)
  fun setInitialQuery(query: String) {
    val editable = searchInputEditText.text
      ?: return

    if (editable.toString() == query) {
      return
    }

    if (textWatcher == null) {
      editable.replace(0, editable.length, query)
      return
    }

    searchInputEditText.removeTextChangedListener(textWatcher)
    editable.replace(0, editable.length, query)
    searchInputEditText.addTextChangedListener(textWatcher)
  }

  @ModelProp
  fun setHint(hint: String) {
    searchInputLayout.hint = hint
  }

  @ModelProp(ModelProp.Option.NullOnRecycle, ModelProp.Option.IgnoreRequireHashCode)
  fun setOnTextEnteredListener(listener: ((String) -> Unit)?) {
    if (listener == null) {
      textWatcher?.let { tw -> searchInputEditText.removeTextChangedListener(tw) }
      searchInputEditText.text = null
      return
    }

    textWatcher?.let { tw -> searchInputEditText.removeTextChangedListener(tw) }
    textWatcher = null

    textWatcher = searchInputEditText.doAfterTextChanged {
      searchInputEditText.text?.let { editable -> listener.invoke(editable.toString()) }
    }
  }

}
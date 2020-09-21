package com.github.k1rakishou.chan.features.search.epoxy

import android.content.Context
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.github.k1rakishou.chan.R
import com.google.android.material.textview.MaterialTextView

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
class EpoxySearchEndOfResultsView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {
  private val textView: MaterialTextView

  init {
    inflate(context, R.layout.epoxy_search_end_of_results_view, this)

    textView = findViewById(R.id.text_view)
  }

  @ModelProp
  fun setText(text: String) {
    textView.text = text
  }

}
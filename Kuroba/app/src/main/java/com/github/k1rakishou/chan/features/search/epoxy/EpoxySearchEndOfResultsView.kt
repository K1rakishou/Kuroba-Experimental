package com.github.k1rakishou.chan.features.search.epoxy

import android.content.Context
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import com.github.k1rakishou.chan.utils.AndroidUtils
import com.google.android.material.textview.MaterialTextView
import javax.inject.Inject

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
class EpoxySearchEndOfResultsView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr), ThemeEngine.ThemeChangesListener {

  @Inject
  lateinit var themeEngine: ThemeEngine

  private val textView: MaterialTextView

  init {
    inflate(context, R.layout.epoxy_search_end_of_results_view, this)

    AndroidUtils.extractStartActivityComponent(context)
      .inject(this)

    textView = findViewById(R.id.text_view)
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    themeEngine.addListener(this)
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    themeEngine.removeListener(this)
  }

  override fun onThemeChanged() {
    updateTextColor()
  }

  @ModelProp
  fun setText(text: String) {
    textView.text = text
    updateTextColor()
  }

  private fun updateTextColor() {
    textView.setTextColor(themeEngine.chanTheme.textColorPrimary)
  }

}
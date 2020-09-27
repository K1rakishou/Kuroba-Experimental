package com.github.k1rakishou.chan.ui.epoxy

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.TextView
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import javax.inject.Inject

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_MATCH_HEIGHT)
class EpoxyErrorView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

  @Inject
  lateinit var themeEngine: ThemeEngine

  private val errorTitleView: TextView
  private val errorTextView: TextView

  init {
    Chan.inject(this)
    inflate(context, R.layout.epoxy_error_view, this)

    errorTitleView = findViewById(R.id.error_title)
    errorTitleView.setTextColor(themeEngine.chanTheme.textPrimaryColor)

    errorTextView = findViewById(R.id.error_text)
    errorTextView.setTextColor(themeEngine.chanTheme.textPrimaryColor)
  }

  @ModelProp
  fun setErrorMessage(text: String) {
    errorTextView.text = text
  }

}
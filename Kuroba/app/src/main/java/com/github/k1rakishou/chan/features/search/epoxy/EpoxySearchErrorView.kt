package com.github.k1rakishou.chan.features.search.epoxy

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.airbnb.epoxy.CallbackProp
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableButton
import com.google.android.material.textview.MaterialTextView
import javax.inject.Inject

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
class EpoxySearchErrorView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

  @Inject
  lateinit var themeEngine: ThemeEngine

  private val errorTitle: MaterialTextView
  private val errorText: MaterialTextView
  private val retryButton: ColorizableButton

  init {
    Chan.inject(this)
    inflate(context, R.layout.epoxy_search_error_view, this)

    errorTitle = findViewById(R.id.search_post_error_title)
    errorText = findViewById(R.id.search_post_error_text)
    retryButton = findViewById(R.id.search_post_error_retry_button)

    errorTitle.setTextColor(themeEngine.chanTheme.textPrimaryColor)
    errorText.setTextColor(themeEngine.chanTheme.textPrimaryColor)
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
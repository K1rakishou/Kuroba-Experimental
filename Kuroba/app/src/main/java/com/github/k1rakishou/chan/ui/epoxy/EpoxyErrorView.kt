package com.github.k1rakishou.chan.ui.epoxy

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableTextView
import com.github.k1rakishou.chan.utils.AndroidUtils
import javax.inject.Inject

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_MATCH_HEIGHT)
class EpoxyErrorView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

  @Inject
  lateinit var themeEngine: ThemeEngine

  private val errorTextView: ColorizableTextView

  init {
    inflate(context, R.layout.epoxy_error_view, this)

    AndroidUtils.extractStartActivityComponent(context)
      .inject(this)

    errorTextView = findViewById(R.id.error_text)
  }

  @ModelProp
  fun setErrorMessage(text: String) {
    errorTextView.text = text
  }

}
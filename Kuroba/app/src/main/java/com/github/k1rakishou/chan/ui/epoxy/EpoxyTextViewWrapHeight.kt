package com.github.k1rakishou.chan.ui.epoxy

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableTextView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.core_themes.ThemeEngine
import javax.inject.Inject

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
class EpoxyTextViewWrapHeight @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

  @Inject
  lateinit var themeEngine: ThemeEngine

  private val textView: ColorizableTextView

  init {
    inflate(context, R.layout.epoxy_text_view_wrap_height, this)

    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    textView = findViewById(R.id.text_view)
  }

  @ModelProp
  fun setMessage(text: String) {
    textView.text = text
  }

}
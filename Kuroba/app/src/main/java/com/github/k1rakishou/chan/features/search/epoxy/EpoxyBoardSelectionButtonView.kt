package com.github.k1rakishou.chan.features.search.epoxy

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableTextView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.setOnThrottlingClickListener
import com.github.k1rakishou.core_themes.ThemeEngine
import javax.inject.Inject

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
class EpoxyBoardSelectionButtonView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

  @Inject
  lateinit var themeEngine: ThemeEngine

  private val textView: ColorizableTextView
  private val rootView: FrameLayout

  init {
    inflate(context, R.layout.epoxy_board_selection_button, this)

    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    textView = findViewById(R.id.text_view)
    rootView = findViewById(R.id.root_view)
  }

  @ModelProp
  fun setBoardCode(boardCode: String?) {
    if (boardCode == null) {
      textView.text = context.getString(R.string.click_to_select_board)
      return
    }

    textView.text = boardCode
  }

  @ModelProp(options = [ModelProp.Option.IgnoreRequireHashCode])
  fun bindClickCallback(callback: (() -> Unit)?) {
    if (callback == null) {
      rootView.setOnThrottlingClickListener(null)
      return
    }

    rootView.setOnThrottlingClickListener { callback.invoke() }
  }

}
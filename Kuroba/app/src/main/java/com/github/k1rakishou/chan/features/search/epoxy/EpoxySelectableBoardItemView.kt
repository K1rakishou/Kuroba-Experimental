package com.github.k1rakishou.chan.features.search.epoxy

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableTextView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.setBackgroundColorFast
import com.github.k1rakishou.chan.utils.setOnThrottlingClickListener

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT, fullSpan = false)
class EpoxySelectableBoardItemView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
  private val boardName: ColorizableTextView

  init {
    inflate(context, R.layout.epoxy_selectable_board_item_view, this)

    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    boardName = findViewById(R.id.board_name)
  }

  @ModelProp
  fun itemBackgroundColor(color: Int?) {
    if (color == null) {
      return
    }

    setBackgroundColorFast(color)
  }

  @ModelProp
  fun bindBoardName(name: String) {
    boardName.text = "/$name/"
  }

  @ModelProp(options = [ModelProp.Option.NullOnRecycle, ModelProp.Option.IgnoreRequireHashCode])
  fun bindClickCallback(callback: (() -> Unit)?) {
    if (callback == null) {
      boardName.setOnThrottlingClickListener(null)
      return
    }

    boardName.setOnThrottlingClickListener { callback.invoke() }
  }

}
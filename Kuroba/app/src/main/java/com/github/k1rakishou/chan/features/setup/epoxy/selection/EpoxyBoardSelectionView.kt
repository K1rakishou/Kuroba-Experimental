package com.github.k1rakishou.chan.features.setup.epoxy.selection

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.airbnb.epoxy.CallbackProp
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import com.google.android.material.textview.MaterialTextView
import javax.inject.Inject

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
class EpoxyBoardSelectionView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

  @Inject
  lateinit var themeEngine: ThemeEngine

  private val boardName: MaterialTextView

  init {
    Chan.inject(this)
    inflate(context, R.layout.epoxy_board_selection_view, this)

    boardName = findViewById(R.id.board_name)
    boardName.setTextColor(themeEngine.chanTheme.textPrimaryColor)
  }

  @ModelProp
  fun bindBoardName(boardName: String) {
    this.boardName.text = boardName
  }

  @CallbackProp
  fun bindRowClickCallback(callback: (() -> Unit)?) {
    if (callback == null) {
      setOnClickListener(null)
      return
    }

    setOnClickListener {
      callback.invoke()
    }
  }

}
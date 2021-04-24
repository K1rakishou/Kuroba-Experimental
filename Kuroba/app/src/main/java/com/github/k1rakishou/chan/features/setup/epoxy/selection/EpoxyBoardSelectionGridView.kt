package com.github.k1rakishou.chan.features.setup.epoxy.selection

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.airbnb.epoxy.CallbackProp
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.core_themes.ThemeEngine
import com.google.android.material.textview.MaterialTextView
import javax.inject.Inject

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT, fullSpan = false)
class EpoxyBoardSelectionGridView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), ThemeEngine.ThemeChangesListener {

  @Inject
  lateinit var themeEngine: ThemeEngine

  private val boardCode: MaterialTextView
  private val boardName: MaterialTextView

  init {
    inflate(context, R.layout.epoxy_board_selection_grid_view, this)

    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    boardCode = findViewById(R.id.board_code)
    boardName = findViewById(R.id.board_name)
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
    updateBoardNameColor()
  }

  @ModelProp
  fun bindBoardCode(boardCode: String) {
    this.boardCode.text = boardCode
    updateBoardNameColor()
  }

  @ModelProp
  fun bindBoardName(boardName: String) {
    this.boardName.text = boardName
    updateBoardNameColor()
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

  private fun updateBoardNameColor() {
    boardCode.setTextColor(themeEngine.chanTheme.textColorPrimary)
    boardName.setTextColor(themeEngine.chanTheme.textColorPrimary)
  }

}
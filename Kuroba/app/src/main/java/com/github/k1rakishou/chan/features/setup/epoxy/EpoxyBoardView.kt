package com.github.k1rakishou.chan.features.setup.epoxy

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.google.android.material.textview.MaterialTextView
import javax.inject.Inject

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
class EpoxyBoardView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), ThemeEngine.ThemeChangesListener {

  @Inject
  lateinit var themeEngine: ThemeEngine

  var descriptor: BoardDescriptor? = null
  private val boardName: MaterialTextView
  private val boardDescription: MaterialTextView

  init {
    Chan.inject(this)
    inflate(context, R.layout.epoxy_board_view, this)

    boardName = findViewById(R.id.board_name)
    boardDescription = findViewById(R.id.board_description)

    updateBoardNameColor()
    updateBoardDescriptionColor()
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
    updateBoardDescriptionColor()
    updateBoardNameColor()
  }

  @ModelProp
  fun setBoardName(boardName: String) {
    this.boardName.text = boardName
    updateBoardNameColor()
  }

  @ModelProp
  fun setBoardDescription(boardDescription: String) {
    this.boardDescription.text = boardDescription
    updateBoardDescriptionColor()
  }

  @ModelProp(options = [ModelProp.Option.DoNotHash, ModelProp.Option.NullOnRecycle])
  fun setBoardDescriptor(boardDescriptor: BoardDescriptor?) {
    this.descriptor = boardDescriptor
  }

  private fun updateBoardDescriptionColor() {
    boardDescription.setTextColor(themeEngine.chanTheme.textColorSecondary)
  }

  private fun updateBoardNameColor() {
    boardName.setTextColor(themeEngine.chanTheme.textColorPrimary)
  }

}
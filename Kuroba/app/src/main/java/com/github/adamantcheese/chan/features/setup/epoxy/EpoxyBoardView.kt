package com.github.adamantcheese.chan.features.setup.epoxy

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.model.data.descriptor.BoardDescriptor
import com.google.android.material.textview.MaterialTextView

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
class EpoxyBoardView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
  var descriptor: BoardDescriptor? = null
  private val boardName: MaterialTextView
  private val boardDescription: MaterialTextView

  init {
    inflate(context, R.layout.epoxy_board_view, this)

    boardName = findViewById(R.id.board_name)
    boardDescription = findViewById(R.id.board_description)
  }

  @ModelProp
  fun setBoardName(boardName: String) {
    this.boardName.text = boardName
  }

  @ModelProp
  fun setBoardDescription(boardDescription: String) {
    this.boardDescription.text = boardDescription
  }

  @ModelProp(options = [ModelProp.Option.DoNotHash, ModelProp.Option.NullOnRecycle])
  fun setBoardDescriptor(boardDescriptor: BoardDescriptor?) {
    this.descriptor = boardDescriptor
  }

}
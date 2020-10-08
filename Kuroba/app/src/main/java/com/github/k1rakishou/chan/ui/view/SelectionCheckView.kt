package com.github.k1rakishou.chan.ui.view

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import com.github.k1rakishou.chan.R

class SelectionCheckView @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defStyleAttr: Int = 0
) : AppCompatImageView(context, attributeSet, defStyleAttr) {
  private var isChecked = false

  init {
    setImageDrawable(
      ContextCompat.getDrawable(
        context,
        R.drawable.ic_radio_button_unchecked_white_24dp
      )
    )
  }

  fun setChecked(checked: Boolean) {
    if (this.isChecked == checked) {
      return
    }

    this.isChecked = checked

    val drawableId = if (this.isChecked) {
      R.drawable.ic_blue_checkmark_24dp
    } else {
      R.drawable.ic_radio_button_unchecked_white_24dp
    }

    setImageDrawable(
      ContextCompat.getDrawable(
        context,
        drawableId
      )
    )
  }

}
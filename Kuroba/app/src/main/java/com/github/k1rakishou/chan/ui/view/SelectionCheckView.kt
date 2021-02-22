package com.github.k1rakishou.chan.ui.view

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.github.k1rakishou.chan.R

class SelectionCheckView @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defStyleAttr: Int = 0
) : AppCompatImageView(context, attributeSet, defStyleAttr) {
  private var _isChecked = false
  private var _enabled = true

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()

    updateDrawable()
  }

  fun enabled(): Boolean = _enabled
  fun checked(): Boolean = _isChecked

  fun enabled(enabled: Boolean) {
    if (_enabled == enabled) {
      return
    }

    _enabled = enabled
    updateDrawable()
  }

  fun setChecked(checked: Boolean) {
    if (_isChecked == checked) {
      return
    }

    _isChecked = checked
    updateDrawable()
  }

  private fun updateDrawable() {
    val drawableId = if (_isChecked) {
      R.drawable.ic_blue_checkmark_24dp
    } else {
      R.drawable.ic_radio_button_unchecked_white_24dp
    }

    var drawable = ContextCompat.getDrawable(
      context,
      drawableId
    )!!

    if (!_enabled) {
      val tintedDrawable = drawable.mutate()
      // Make the check box of gray color
      tintedDrawable.colorFilter = PorterDuffColorFilter(
        ColorUtils.setAlphaComponent(Color.GRAY, (0.5f * 255).toInt()),
        PorterDuff.Mode.MULTIPLY
      )

      drawable = tintedDrawable
    }

    setImageDrawable(drawable)
  }

}
package com.github.k1rakishou.chan.ui.theme

import android.content.Context
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.core.content.ContextCompat

class ThemeDrawable(var drawable: Int, alpha: Float) {
  var intAlpha = 0
  var tint = -1

  init {
    intAlpha = Math.round(alpha * 0xff)
  }

  fun setAlpha(alpha: Float) {
    intAlpha = Math.round(alpha * 0xff)
  }

  fun apply(imageView: ImageView) {
    imageView.setImageResource(drawable)

    // Use the int one!
    imageView.imageAlpha = intAlpha

    if (tint != -1) {
      imageView.drawable.setTint(tint)
    }
  }

  fun makeDrawable(context: Context): Drawable {
    val d = ContextCompat.getDrawable(context, drawable)!!.mutate()
    d.alpha = intAlpha

    if (tint != -1) {
      d.setTint(tint)
    }

    return d
  }

}
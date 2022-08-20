package com.github.k1rakishou.core_spannable

import android.text.TextPaint
import android.text.style.CharacterStyle

/**
 * Taken from
 * https://github.com/Mishiranu/Dashchan/blob/74a72416e2a00d762791752bcc65520e7f7d1fd5/src/com/mishiranu/dashchan/text/style/ScriptSpan.java
 * */
class ScriptSpan(val isSuperScript: Boolean) : CharacterStyle() {

  override fun updateDrawState(tp: TextPaint) {
    val oldSize: Float = tp.textSize
    val newSize = oldSize * 3f / 4f
    val shift = (oldSize - newSize).toInt()

    tp.textSize = (newSize + 0.5f).toInt().toFloat()
    if (isSuperScript) {
      tp.baselineShift -= shift
    }
  }

}
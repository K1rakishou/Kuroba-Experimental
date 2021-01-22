package com.github.k1rakishou.chan.utils

import android.view.View
import java.util.*

private const val clickDelay = 350L
private val clickTimeStorage = WeakHashMap<View, Long>()

fun View.setOnThrottlingClickListener(listener: View.OnClickListener?) {
  if (listener == null) {
    clickTimeStorage.remove(this)
    setOnClickListener(null)
    return
  }

  setOnClickListener { view ->
    val now = System.currentTimeMillis()
    val lastClickTime = clickTimeStorage[this]

    if (lastClickTime == null) {
      listener.onClick(view)
      clickTimeStorage[this] = now

      return@setOnClickListener
    }

    if (now - lastClickTime < clickDelay) {
      return@setOnClickListener
    }

    listener.onClick(view)
    clickTimeStorage[this] = now
  }
}
package com.github.k1rakishou.chan.utils

import android.view.View
import java.util.*

private const val clickDelay = 350L
private val regularClicksTimeStorage = WeakHashMap<View, Long>()

fun View.setOnThrottlingClickListener(listener: View.OnClickListener?) {
  if (listener == null) {
    regularClicksTimeStorage.remove(this)
    setOnClickListener(null)
    return
  }

  setOnClickListener { view ->
    val now = System.currentTimeMillis()
    val lastClickTime = regularClicksTimeStorage[this]

    if (lastClickTime == null) {
      listener.onClick(view)
      regularClicksTimeStorage[this] = now

      return@setOnClickListener
    }

    if (now - lastClickTime < clickDelay) {
      return@setOnClickListener
    }

    listener.onClick(view)
    regularClicksTimeStorage[this] = now
  }
}
package com.github.k1rakishou.chan.utils

import android.view.View
import java.util.*

private const val clickDelay = 350L
private val regularClicksTimeStorage = WeakHashMap<Any, Long>()
private val longClicksTimeStorage = WeakHashMap<Any, Long>()

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

fun View.setOnThrottlingClickListener(token: String, listener: View.OnClickListener?) {
  if (listener == null) {
    setOnClickListener(null)
    return
  }

  setOnClickListener { view ->
    val now = System.currentTimeMillis()
    val lastClickTime = regularClicksTimeStorage[token]

    if (lastClickTime == null) {
      listener.onClick(view)
      regularClicksTimeStorage[token] = now

      return@setOnClickListener
    }

    if (now - lastClickTime < clickDelay) {
      return@setOnClickListener
    }

    listener.onClick(view)
    regularClicksTimeStorage[token] = now
  }
}

fun View.setOnThrottlingLongClickListener(token: String, listener: View.OnLongClickListener?) {
  if (listener == null) {
    setOnLongClickListener(null)
    return
  }

  setOnLongClickListener { view ->
    val now = System.currentTimeMillis()
    val lastClickTime = longClicksTimeStorage[token]

    if (lastClickTime == null) {
      val result = listener.onLongClick(view)
      longClicksTimeStorage[token] = now

      return@setOnLongClickListener result
    }

    if (now - lastClickTime < clickDelay) {
      return@setOnLongClickListener true
    }

    val result = listener.onLongClick(view)
    longClicksTimeStorage[token] = now

    return@setOnLongClickListener result
  }
}
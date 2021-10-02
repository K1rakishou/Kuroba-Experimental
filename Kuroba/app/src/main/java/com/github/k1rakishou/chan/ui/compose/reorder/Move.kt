package com.github.k1rakishou.chan.ui.compose.reorder


/**
 * Taken from https://github.com/aclassen/ComposeReorderable
 * */


fun <T> MutableList<T>.move(fromIdx: Int, toIdx: Int): Boolean {
  if (fromIdx == toIdx) {
    return false
  }

  if (fromIdx < 0 || fromIdx >= size) {
    return false
  }

  if (toIdx < 0 || toIdx >= size) {
    return false
  }

  if (toIdx > fromIdx) {
    for (i in fromIdx until toIdx) {
      this[i] = this[i + 1].also { this[i + 1] = this[i] }
    }

    return true
  }

  for (i in fromIdx downTo toIdx + 1) {
    this[i] = this[i - 1].also { this[i - 1] = this[i] }
  }

  return true
}
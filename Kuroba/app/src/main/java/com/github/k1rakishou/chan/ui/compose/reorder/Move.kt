package com.github.k1rakishou.chan.ui.compose.reorder


/**
 * Taken from https://github.com/aclassen/ComposeReorderable
 * */


fun <T> MutableList<T>.move(fromIdx: Int, toIdx: Int): Boolean {
  when {
    fromIdx == toIdx -> {
      return false
    }
    toIdx > fromIdx -> {
      for (i in fromIdx until toIdx) {
        this[i] = this[i + 1].also { this[i + 1] = this[i] }
      }

      return true
    }
    else -> {
      for (i in fromIdx downTo toIdx + 1) {
        this[i] = this[i - 1].also { this[i - 1] = this[i] }
      }

      return true
    }
  }
}
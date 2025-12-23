package com.github.k1rakishou.chan.ui.helper

object PinHelper {

  @JvmStatic
  fun getShortUnreadCount(value: Int): String {
    if (value < 0) {
      return "???"
    }

    val notations = "kmb"
    var currentNotationIndex = -1
    var valueLocal = value.toFloat()

    while (valueLocal >= 1000f) {
      valueLocal /= 1000f
      ++currentNotationIndex
    }

    if (currentNotationIndex < 0) {
      return valueLocal.toInt().toString()
    }

    val notation = notations[currentNotationIndex]
    return String.format("%.${1}f${notation}", valueLocal)
  }

}
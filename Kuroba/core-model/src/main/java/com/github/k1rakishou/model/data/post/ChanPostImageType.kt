package com.github.k1rakishou.model.data.post

enum class ChanPostImageType(val value: Int) {
  STATIC(0),
  GIF(1),
  MOVIE(2),
  PDF(3),
  SWF(4);

  companion object {
    fun fromValue(value: Int): ChanPostImageType {
      return values().first { it.value == value }
    }
  }
}
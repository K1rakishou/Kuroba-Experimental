package com.github.k1rakishou.core_spannable.serializable

enum class SerializableSpanType(val spanTypeValue: Int) {
  ForegroundColorSpanHashedType(0),
  BackgroundColorSpanHashedType(1),
  StrikethroughSpanType(2),
  StyleSpanType(3),
  TypefaceSpanType(4),
  AbsoluteSizeSpanHashed(5),
  PostLinkable(6),
  ColorizableBackgroundColorSpan(7),
  ColorizableForegroundColorSpan(8);

  companion object {
    fun from(value: Int): SerializableSpanType {
      return when (value) {
        0 -> ForegroundColorSpanHashedType
        1 -> BackgroundColorSpanHashedType
        2 -> StrikethroughSpanType
        3 -> StyleSpanType
        4 -> TypefaceSpanType
        5 -> AbsoluteSizeSpanHashed
        6 -> PostLinkable
        7 -> ColorizableBackgroundColorSpan
        8 -> ColorizableForegroundColorSpan
        else -> throw IllegalArgumentException("Not implemented for value = $value")
      }
    }
  }
}
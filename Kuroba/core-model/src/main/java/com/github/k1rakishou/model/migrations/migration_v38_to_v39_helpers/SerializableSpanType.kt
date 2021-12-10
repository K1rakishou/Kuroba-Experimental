package com.github.k1rakishou.model.migrations.migration_v38_to_v39_helpers

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
    fun from(value: Int): SerializableSpanType? {
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
        else -> null
      }
    }
  }
}
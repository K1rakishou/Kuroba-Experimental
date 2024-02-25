package com.github.k1rakishou.chan.features.reply.data

import androidx.compose.runtime.Immutable

@Immutable
enum class ReplyLayoutVisibility(val order: Int) {
  Collapsed(0),
  Opened(1),
  Expanded(2);

  companion object {
    fun fromRawValue(value: Int?): ReplyLayoutVisibility {
      if (value == null) {
        return Collapsed
      }

      return entries
        .firstOrNull { rlv -> rlv.order == value }
        ?: Collapsed
    }
  }
}
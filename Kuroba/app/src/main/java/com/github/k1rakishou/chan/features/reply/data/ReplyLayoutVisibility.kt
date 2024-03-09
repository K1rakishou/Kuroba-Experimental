package com.github.k1rakishou.chan.features.reply.data

import androidx.compose.runtime.Immutable

@Immutable
sealed class ReplyLayoutVisibility(val value: Int) : Comparable<ReplyLayoutVisibility> {

  override fun compareTo(other: ReplyLayoutVisibility): Int {
    return this.value.compareTo(other.value)
  }

  data object Collapsed : ReplyLayoutVisibility(0)
  data object Opened : ReplyLayoutVisibility(1)
  data object Expanded : ReplyLayoutVisibility(2)
}
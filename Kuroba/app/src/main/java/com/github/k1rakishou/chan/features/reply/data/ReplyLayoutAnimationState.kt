package com.github.k1rakishou.chan.features.reply.data

sealed interface ReplyLayoutAnimationState : Comparable<ReplyLayoutAnimationState> {
  val order: Int

  override fun compareTo(other: ReplyLayoutAnimationState): Int {
    return this.order.compareTo(other.order)
  }

  data object Collapsed : ReplyLayoutAnimationState {
    override val order: Int = 0
  }

  data object Collapsing : ReplyLayoutAnimationState {
    override val order: Int = 1
  }

  data object Opening : ReplyLayoutAnimationState {
    override val order: Int = 2
  }

  data object Opened : ReplyLayoutAnimationState {
    override val order: Int = 3
  }

  data object Expanding : ReplyLayoutAnimationState {
    override val order: Int = 4
  }

  data object Expanded : ReplyLayoutAnimationState {
    override val order: Int = 5
  }

  companion object {
    fun fromRawValue(value: Int?): ReplyLayoutAnimationState {
      return when (value) {
        0 -> Collapsed
        1 -> Collapsing
        2 -> Opening
        3 -> Opened
        4 -> Expanding
        5 -> Expanded
        else -> Collapsed
      }
    }
  }
}
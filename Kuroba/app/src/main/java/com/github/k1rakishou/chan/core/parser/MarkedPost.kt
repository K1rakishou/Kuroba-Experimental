package com.github.k1rakishou.chan.core.parser

import com.github.k1rakishou.model.data.descriptor.PostDescriptor

data class MarkedPost(
  val postDescriptor: PostDescriptor,
  val markedPostType: MarkedPostType
)

enum class MarkedPostType(val type: Int) {
  MyPost(0);

  companion object {
    fun fromTypRaw(value: Int): MarkedPostType? {
      return entries
        .firstOrNull { markedPostType -> markedPostType.type == value }
    }
  }
}
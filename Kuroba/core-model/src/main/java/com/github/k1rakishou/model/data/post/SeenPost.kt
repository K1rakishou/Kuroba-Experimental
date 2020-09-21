package com.github.k1rakishou.model.data.post

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import org.joda.time.DateTime

data class SeenPost(
  val threadDescriptor: ChanDescriptor.ThreadDescriptor,
  val postNo: Long,
  val insertedAt: DateTime
)
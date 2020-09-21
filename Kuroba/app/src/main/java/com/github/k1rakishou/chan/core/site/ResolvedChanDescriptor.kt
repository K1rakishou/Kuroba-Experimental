package com.github.k1rakishou.chan.core.site

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

data class ResolvedChanDescriptor(
  val chanDescriptor: ChanDescriptor,
  val markedPostNo: Long? = null
)
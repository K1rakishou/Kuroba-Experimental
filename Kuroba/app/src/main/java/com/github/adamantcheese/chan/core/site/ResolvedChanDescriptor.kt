package com.github.adamantcheese.chan.core.site

import com.github.adamantcheese.model.data.descriptor.ChanDescriptor

data class ResolvedChanDescriptor(
  val chanDescriptor: ChanDescriptor,
  val markedPostNo: Long? = null
)
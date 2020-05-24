package com.github.adamantcheese.model.data.descriptor

class ArchivePostDescriptor(
  descriptor: ChanDescriptor,
  postNo: Long,
  override val postSubNo: Long
) : PostDescriptor(descriptor, postNo) {
}
package com.github.k1rakishou.chan.core.site.parser.processor

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.post.ChanPostBuilder

interface IChanReaderProcessor {
  val chanDescriptor: ChanDescriptor
  val canUseEmptyBoardIfBoardDoesNotExist: Boolean

  suspend fun setOp(op: ChanPostBuilder?)
  suspend fun getOp(): ChanPostBuilder?
  suspend fun addPost(postBuilder: ChanPostBuilder)
  suspend fun addManyPosts(postBuilders: List<ChanPostBuilder>)
  suspend fun applyChanReadOptions()
  suspend fun getToParse(): List<ChanPostBuilder>
  suspend fun getThreadDescriptors(): List<ChanDescriptor.ThreadDescriptor>
  suspend fun getTotalPostsCount(): Int
}
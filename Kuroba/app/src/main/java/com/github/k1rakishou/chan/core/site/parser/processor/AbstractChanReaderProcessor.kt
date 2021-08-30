package com.github.k1rakishou.chan.core.site.parser.processor

import com.github.k1rakishou.chan.core.site.SiteSpecificError
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.post.ChanPostBuilder

abstract class AbstractChanReaderProcessor {
  abstract val chanDescriptor: ChanDescriptor
  abstract val canUseEmptyBoardIfBoardDoesNotExist: Boolean

  @get:Synchronized
  @set:Synchronized
  var closed: Boolean = false
  @get:Synchronized
  @set:Synchronized
  var deleted: Boolean = false
  @get:Synchronized
  @set:Synchronized
  var archived: Boolean = false
  @get:Synchronized
  @set:Synchronized
  var endOfUnlimitedCatalogReached: Boolean = false
  @get:Synchronized
  @set:Synchronized
  var error: SiteSpecificError? = null

  abstract suspend fun setOp(op: ChanPostBuilder?)
  abstract suspend fun addPost(postBuilder: ChanPostBuilder)
  abstract suspend fun addManyPosts(postBuilders: List<ChanPostBuilder>)
  abstract suspend fun applyChanReadOptions()
  abstract suspend fun getToParse(): List<ChanPostBuilder>
  abstract suspend fun getThreadDescriptors(): List<ChanDescriptor.ThreadDescriptor>
  abstract suspend fun getTotalPostsCount(): Int

  init {
    check(chanDescriptor !is ChanDescriptor.CompositeCatalogDescriptor) {
      error("Cannot use CompositeCatalogDescriptor here")
    }
  }
}
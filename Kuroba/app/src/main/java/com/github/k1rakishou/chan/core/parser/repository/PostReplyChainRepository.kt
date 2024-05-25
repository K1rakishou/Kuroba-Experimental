package com.github.k1rakishou.chan.core.parser.repository

import com.github.k1rakishou.common.mutableMapWithCap
import com.github.k1rakishou.common.mutableSetWithCap
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import java.util.concurrent.ConcurrentHashMap

interface PostReplyChainRepository {
  suspend fun copyThreadReplyChain(threadDescriptor: ChanDescriptor.ThreadDescriptor): ThreadReplyChainCopy?

  suspend fun insertRepliesTo(postDescriptor: PostDescriptor, repliesTo: Set<PostDescriptor>)
  suspend fun insertRepliesFrom(postDescriptor: PostDescriptor, repliesFrom: Set<PostDescriptor>)

  /**
   * Get descriptors of posts **this post replies to**
   * */
  suspend fun getRepliesTo(postDescriptor: PostDescriptor): Set<PostDescriptor>

  /**
   * Get descriptors of posts that **reply to this post**
   * */
  suspend fun getRepliesFrom(postDescriptor: PostDescriptor): Set<PostDescriptor>
  suspend fun getAllRepliesFromRecursively(postDescriptor: PostDescriptor): Set<PostDescriptor>
  suspend fun getManyRepliesTo(postDescriptors: List<PostDescriptor>): Set<PostDescriptor>

  suspend fun findPostWithRepliesRecursive(
    postDescriptor: PostDescriptor,
    includeRepliesFrom: Boolean,
    includeRepliesTo: Boolean,
    maxRecursion: Int = Int.MAX_VALUE,
    resultPostDescriptors: MutableSet<PostDescriptor> = mutableSetOf()
  )
}

class PostReplyChainRepositoryImpl : PostReplyChainRepository {
  private val replyChains = ConcurrentHashMap<ChanDescriptor.ThreadDescriptor, ThreadReplyChain>()

  override suspend fun copyThreadReplyChain(threadDescriptor: ChanDescriptor.ThreadDescriptor): ThreadReplyChainCopy? {
    return replyChains[threadDescriptor]?.toThreadReplyChainCopy()
  }

  override suspend fun insertRepliesTo(postDescriptor: PostDescriptor, repliesTo: Set<PostDescriptor>) {
    val threadReplyChain = replyChains.getOrPut(
      key = postDescriptor.threadDescriptor(),
      defaultValue = { ThreadReplyChainActual() }
    )

    threadReplyChain.insertRepliesTo(postDescriptor, repliesTo)
  }

  override suspend fun insertRepliesFrom(postDescriptor: PostDescriptor, repliesFrom: Set<PostDescriptor>) {
    val threadReplyChain = replyChains.getOrPut(
      key = postDescriptor.threadDescriptor(),
      defaultValue = { ThreadReplyChainActual() }
    )

    threadReplyChain.insertRepliesFrom(postDescriptor, repliesFrom)
  }

  override suspend fun getRepliesTo(postDescriptor: PostDescriptor): Set<PostDescriptor> {
    val threadReplyChain = replyChains[postDescriptor.threadDescriptor()]
      ?: return emptySet()

    return threadReplyChain.getRepliesTo(postDescriptor)
  }

  override suspend fun getRepliesFrom(postDescriptor: PostDescriptor): Set<PostDescriptor> {
    val threadReplyChain = replyChains[postDescriptor.threadDescriptor()]
      ?: return emptySet()

    return threadReplyChain.getRepliesFrom(postDescriptor)
  }

  override suspend fun getAllRepliesFromRecursively(postDescriptor: PostDescriptor): Set<PostDescriptor> {
    val threadReplyChain = replyChains[postDescriptor.threadDescriptor()]
      ?: return emptySet()

    return threadReplyChain.getAllRepliesFromRecursively(postDescriptor)
  }

  override suspend fun getManyRepliesTo(postDescriptors: List<PostDescriptor>): Set<PostDescriptor> {
    val mapped = postDescriptors
      .mapNotNull { postDescriptor ->
        val threadReplyChain = replyChains[postDescriptor.threadDescriptor()]
          ?: return@mapNotNull null

        return@mapNotNull postDescriptor to threadReplyChain
      }

    if (mapped.isEmpty()) {
      return emptySet()
    }

    val threadReplyChainMap = mutableMapWithCap<PostDescriptor, ThreadReplyChain>(mapped.size)

    for ((postDescriptor, threadReplyChain) in mapped) {
      threadReplyChainMap[postDescriptor] = threadReplyChain
    }

    if (threadReplyChainMap.isEmpty()) {
      return emptySet()
    }

    val resultSet = mutableSetWithCap<PostDescriptor>(threadReplyChainMap.size * 2)

    for ((postDescriptor, threadReplyChain) in threadReplyChainMap) {
      val repliesTo = threadReplyChain.getRepliesTo(postDescriptor)
      resultSet.addAll(repliesTo)
    }

    return resultSet
  }

  override suspend fun findPostWithRepliesRecursive(
    postDescriptor: PostDescriptor,
    includeRepliesFrom: Boolean,
    includeRepliesTo: Boolean,
    maxRecursion: Int,
    resultPostDescriptors: MutableSet<PostDescriptor>
  ) {
    require(includeRepliesFrom || includeRepliesTo) {
      "Either includeRepliesFrom or includeRepliesTo must be true"
    }

    if (maxRecursion < 0) {
      return
    }

    val threadReplyChain = replyChains[postDescriptor.threadDescriptor()]
      ?: return

    if (includeRepliesFrom) {
      threadReplyChain.getRepliesFrom(postDescriptor).forEach { replyFrom ->
        if (!resultPostDescriptors.add(replyFrom)) {
          return@forEach
        }

        findPostWithRepliesRecursive(
          postDescriptor = replyFrom,
          includeRepliesFrom = true,
          includeRepliesTo = includeRepliesTo,
          maxRecursion = maxRecursion - 1,
          resultPostDescriptors = resultPostDescriptors
        )
      }
    }

    if (includeRepliesTo) {
      threadReplyChain.getRepliesTo(postDescriptor).forEach { replyTo ->
        if (!resultPostDescriptors.add(replyTo)) {
          return@forEach
        }

        findPostWithRepliesRecursive(
          postDescriptor = replyTo,
          includeRepliesFrom = includeRepliesFrom,
          includeRepliesTo = true,
          maxRecursion = maxRecursion - 1,
          resultPostDescriptors = resultPostDescriptors
        )
      }
    }
  }

}
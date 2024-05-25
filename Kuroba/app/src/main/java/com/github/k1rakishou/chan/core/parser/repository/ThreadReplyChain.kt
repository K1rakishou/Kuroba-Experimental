package com.github.k1rakishou.chan.core.parser.repository

import androidx.annotation.GuardedBy
import com.github.k1rakishou.common.mutableMapWithCap
import com.github.k1rakishou.common.withLockNonCancellable
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import kotlinx.coroutines.sync.Mutex

interface ThreadReplyChain {
  suspend fun insertRepliesTo(postDescriptor: PostDescriptor, repliesTo: Set<PostDescriptor>)
  suspend fun insertRepliesFrom(postDescriptor: PostDescriptor, repliesFrom: Set<PostDescriptor>)
  suspend fun getRepliesTo(postDescriptor: PostDescriptor): Set<PostDescriptor>
  suspend fun getRepliesFrom(postDescriptor: PostDescriptor): Set<PostDescriptor>
  suspend fun getAllRepliesFromRecursively(postDescriptor: PostDescriptor): Set<PostDescriptor>
  suspend fun toThreadReplyChainCopy(): ThreadReplyChainCopy
}

class ThreadReplyChainCopy(
  replyToMap: Map<PostDescriptor, MutableSet<PostDescriptor>>,
  replyFromMap: Map<PostDescriptor, MutableSet<PostDescriptor>>,
) : ThreadReplyChain {
  private val replyToMap = mutableMapWithCap<PostDescriptor, MutableSet<PostDescriptor>>(128)
  private val replyFromMap = mutableMapWithCap<PostDescriptor, MutableSet<PostDescriptor>>(128)

  init {
    this.replyToMap.putAll(replyToMap)
    this.replyFromMap.putAll(replyFromMap)
  }

  override suspend fun insertRepliesTo(postDescriptor: PostDescriptor, repliesTo: Set<PostDescriptor>) {
    replyToMap[postDescriptor] = repliesTo.toMutableSet()

    for (replyToDescriptor in repliesTo) {
      val repliesFrom = replyFromMap.getOrPut(
        key = replyToDescriptor,
        defaultValue = { mutableSetOf() }
      )

      repliesFrom.add(postDescriptor)
    }
  }

  override suspend fun insertRepliesFrom(postDescriptor: PostDescriptor, repliesFrom: Set<PostDescriptor>) {
    replyFromMap[postDescriptor] = repliesFrom.toMutableSet()

    for (replyFromDescriptor in repliesFrom) {
      val repliesTo = replyToMap.getOrPut(
        key = replyFromDescriptor,
        defaultValue = { mutableSetOf() }
      )

      repliesTo.add(postDescriptor)
    }
  }

  override suspend fun getRepliesTo(postDescriptor: PostDescriptor): Set<PostDescriptor> {
    return replyToMap[postDescriptor]?.toSet() ?: emptySet()
  }

  override suspend fun getRepliesFrom(postDescriptor: PostDescriptor): Set<PostDescriptor> {
    return replyFromMap[postDescriptor]?.toSet() ?: emptySet()
  }

  override suspend fun getAllRepliesFromRecursively(postDescriptor: PostDescriptor): Set<PostDescriptor> {
    val collectedReplies = linkedSetOf<PostDescriptor>()
    getAllRepliesFromRecursivelyInternal(postDescriptor, collectedReplies)
    return collectedReplies
  }

  override suspend fun toThreadReplyChainCopy(): ThreadReplyChainCopy {
    return this
  }

  private fun getAllRepliesFromRecursivelyInternal(
    postDescriptor: PostDescriptor,
    collectedReplies: MutableSet<PostDescriptor>
  ) {
    val replies = replyFromMap[postDescriptor]
      ?: return

    for (reply in replies) {
      if (!collectedReplies.add(reply)) {
        continue
      }

      getAllRepliesFromRecursivelyInternal(reply, collectedReplies)
    }
  }
}

class ThreadReplyChainActual : ThreadReplyChain {
  private val mutex = Mutex()

  @GuardedBy("mutex")
  private val replyToMap = mutableMapWithCap<PostDescriptor, MutableSet<PostDescriptor>>(128)
  @GuardedBy("mutex")
  private val replyFromMap = mutableMapWithCap<PostDescriptor, MutableSet<PostDescriptor>>(128)

  override suspend fun insertRepliesTo(postDescriptor: PostDescriptor, repliesTo: Set<PostDescriptor>) {
    mutex.withLockNonCancellable {
      replyToMap[postDescriptor] = repliesTo.toMutableSet()

      for (replyToDescriptor in repliesTo) {
        val repliesFrom = replyFromMap.getOrPut(
          key = replyToDescriptor,
          defaultValue = { mutableSetOf() }
        )

        repliesFrom.add(postDescriptor)
      }
    }
  }

  override suspend fun insertRepliesFrom(postDescriptor: PostDescriptor, repliesFrom: Set<PostDescriptor>) {
    mutex.withLockNonCancellable {
      replyFromMap[postDescriptor] = repliesFrom.toMutableSet()

      for (replyFromDescriptor in repliesFrom) {
        val repliesTo = replyToMap.getOrPut(
          key = replyFromDescriptor,
          defaultValue = { mutableSetOf() }
        )

        repliesTo.add(postDescriptor)
      }
    }
  }

  override suspend fun getRepliesTo(postDescriptor: PostDescriptor): Set<PostDescriptor> {
    return mutex.withLockNonCancellable { replyToMap[postDescriptor]?.toSet() ?: emptySet() }
  }

  override suspend fun getRepliesFrom(postDescriptor: PostDescriptor): Set<PostDescriptor> {
    return mutex.withLockNonCancellable { replyFromMap[postDescriptor]?.toSet() ?: emptySet() }
  }

  override suspend fun getAllRepliesFromRecursively(postDescriptor: PostDescriptor): Set<PostDescriptor> {
    return mutex.withLockNonCancellable {
      val collectedReplies = linkedSetOf<PostDescriptor>()
      getAllRepliesFromRecursivelyInternal(postDescriptor, collectedReplies)
      return@withLockNonCancellable collectedReplies
    }
  }

  override suspend fun toThreadReplyChainCopy(): ThreadReplyChainCopy {
    return ThreadReplyChainCopy(
      replyToMap = replyToMap.toMap(),
      replyFromMap = replyFromMap.toMap()
    )
  }

  private fun getAllRepliesFromRecursivelyInternal(
    postDescriptor: PostDescriptor,
    collectedReplies: MutableSet<PostDescriptor>
  ) {
    val replies = replyFromMap[postDescriptor]
      ?: return

    for (reply in replies) {
      if (!collectedReplies.add(reply)) {
        continue
      }

      getAllRepliesFromRecursivelyInternal(reply, collectedReplies)
    }
  }

}
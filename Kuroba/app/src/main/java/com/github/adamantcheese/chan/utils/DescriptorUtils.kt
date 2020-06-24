package com.github.adamantcheese.chan.utils

import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.orm.Loadable
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.PostDescriptor

object DescriptorUtils {

  fun getThreadDescriptorOrThrow(loadable: Loadable): ChanDescriptor.ThreadDescriptor {
    require(loadable.isThreadMode) { "Loadable must be in thread mode" }
    require(loadable.no > 0) { "Loadable is in thread mode but it has no threadId" }

    return ChanDescriptor.ThreadDescriptor(loadable.board.boardDescriptor(), loadable.no.toLong())
  }

  @JvmStatic
  fun getDescriptor(loadable: Loadable): ChanDescriptor {
    if (loadable.isThreadMode) {
      val threadId = loadable.no.toLong()
      check(threadId > 0) { "Loadable is in thread mode but it has no threadId" }

      return ChanDescriptor.ThreadDescriptor(loadable.board.boardDescriptor(), threadId)
    } else if (loadable.isCatalogMode) {
      return ChanDescriptor.CatalogDescriptor(loadable.board.boardDescriptor())
    }

    throw IllegalStateException("Unsupported loadable mode: ${loadable.mode}")
  }

  fun getPostDescriptor(loadable: Loadable, post: Post): PostDescriptor {
    return when (val descriptor = getDescriptor(loadable)) {
      is ChanDescriptor.ThreadDescriptor -> PostDescriptor.create(
        descriptor.siteName(),
        descriptor.boardCode(),
        descriptor.threadNo,
        post.no
      )
      is ChanDescriptor.CatalogDescriptor -> {
        PostDescriptor.create(
          descriptor.siteName(),
          descriptor.boardCode(),
          post.no
        )
      }
    }
  }

}
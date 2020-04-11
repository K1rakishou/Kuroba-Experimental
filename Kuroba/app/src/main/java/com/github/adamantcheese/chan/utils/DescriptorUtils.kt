package com.github.adamantcheese.chan.utils

import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.orm.Loadable
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.github.adamantcheese.model.data.descriptor.ThreadDescriptor

object DescriptorUtils {

    @JvmStatic
    fun getThreadDescriptor(loadable: Loadable): ThreadDescriptor {
        require(loadable.isThreadMode) { "Loadable must be in thread mode" }
        require(loadable.no > 0) { "Loadable is in thread mode but it has no threadId" }

        return ThreadDescriptor(loadable.boardDescriptor, loadable.no.toLong())
    }

    /**
     * When you have access to both [Loadable] and [Post] prefer this method. It won't crash if the
     * loadable ends up being a catalog loadable
     * */
    @JvmStatic
    fun getThreadDescriptor(loadable: Loadable, post: Post): ThreadDescriptor {
        if (loadable.isThreadMode) {
            val threadId = loadable.no.toLong()
            check(threadId > 0) { "Loadable is in thread mode but it has no threadId" }

            return ThreadDescriptor(loadable.boardDescriptor, threadId)
        } else if (loadable.isCatalogMode) {
            return ThreadDescriptor(loadable.boardDescriptor, post.no.toLong())
        }

        throw IllegalStateException("Bad loadable mode: ${loadable.mode}")
    }

    fun getPostDescriptor(loadable: Loadable, post: Post): PostDescriptor {
        val threadDescriptor = getThreadDescriptor(loadable, post)

        return PostDescriptor(threadDescriptor, post.no.toLong())
    }

}
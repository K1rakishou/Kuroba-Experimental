package com.github.adamantcheese.model.data.descriptor

data class PostDescriptor(
        val threadDescriptor: ThreadDescriptor,
        val postId: Long
) {

    companion object {
        @JvmStatic
        fun create(siteName: String, boardCode: String, threadId: Long, postId: Long): PostDescriptor {
            require(threadId > 0) { "Bad threadId: $threadId" }
            require(postId > 0) { "Bad postId: $postId" }

            return PostDescriptor(ThreadDescriptor.create(siteName, boardCode, threadId), postId)
        }
    }

}
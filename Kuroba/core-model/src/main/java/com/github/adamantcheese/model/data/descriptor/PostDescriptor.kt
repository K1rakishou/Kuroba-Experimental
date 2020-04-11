package com.github.adamantcheese.model.data.descriptor

data class PostDescriptor(
        val threadDescriptor: ThreadDescriptor,
        val postId: Long
) {

    companion object {
        @JvmStatic
        fun create(siteName: String, boardCode: String, threadId: Long, postId: Long): PostDescriptor {
            return PostDescriptor(ThreadDescriptor.create(siteName, boardCode, threadId), postId)
        }
    }

}
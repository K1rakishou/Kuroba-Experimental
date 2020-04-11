package com.github.adamantcheese.model.data.descriptor

data class PostDescriptor(
        val threadDescriptor: ThreadDescriptor,
        val postNo: Long
) {

    companion object {
        @JvmStatic
        fun create(siteName: String, boardCode: String, threadNo: Long, postNo: Long): PostDescriptor {
            require(threadNo > 0) { "Bad threadNo: $threadNo" }
            require(postNo > 0) { "Bad postNo: $postNo" }

            return PostDescriptor(ThreadDescriptor.create(siteName, boardCode, threadNo), postNo)
        }
    }

}
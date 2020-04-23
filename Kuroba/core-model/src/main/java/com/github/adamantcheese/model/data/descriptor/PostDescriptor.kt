package com.github.adamantcheese.model.data.descriptor

class PostDescriptor private constructor(
        /**
         * A post may belong to a thread or to a catalog (OP) that's why we use abstract
         * ChanDescriptor here and not a concrete Thread/Catalog descriptor
         * */
        val descriptor: ChanDescriptor,
        val postNo: Long
) {

    fun getThreadDescriptor(): ChanDescriptor.ThreadDescriptor {
        return when (descriptor) {
            is ChanDescriptor.ThreadDescriptor -> descriptor
            is ChanDescriptor.CatalogDescriptor -> descriptor.toThreadDescriptor(postNo)
        }
    }

    fun getThreadNo(): Long {
        return when (descriptor) {
            is ChanDescriptor.ThreadDescriptor -> descriptor.opNo
            is ChanDescriptor.CatalogDescriptor -> {
                require(postNo > 0) { "Bad postNo: $postNo" }
                postNo
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PostDescriptor) return false

        if (descriptor != other.descriptor) return false
        if (postNo != other.postNo) return false

        return true
    }

    override fun hashCode(): Int {
        var result = descriptor.hashCode()
        result = 31 * result + postNo.hashCode()
        return result
    }

    override fun toString(): String {
        val opNo = if (descriptor is ChanDescriptor.ThreadDescriptor) {
            descriptor.opNo.toString()
        } else {
            postNo.toString()
        }

        return "PostDescriptor(siteName='${descriptor.siteName()}', " +
                "boardCode='${descriptor.boardCode()}', opNo=$opNo, postNo=$postNo)"
    }

    companion object {
        @JvmStatic
        fun create(siteName: String, boardCode: String, threadNo: Long): PostDescriptor {
            require(threadNo > 0) { "Bad threadNo: $threadNo" }

            return PostDescriptor(
                    ChanDescriptor.CatalogDescriptor.create(siteName, boardCode),
                    threadNo
            )
        }

        @JvmStatic
        fun create(siteName: String, boardCode: String, threadNo: Long, postNo: Long): PostDescriptor {
            require(threadNo > 0) { "Bad threadNo: $threadNo" }
            require(postNo > 0) { "Bad postNo: $postNo" }

            return PostDescriptor(
                    ChanDescriptor.ThreadDescriptor.create(siteName, boardCode, threadNo),
                    postNo
            )
        }
    }

}
package com.github.adamantcheese.model.data.descriptor

sealed class ChanDescriptor(val boardDescriptor: BoardDescriptor) {
    abstract fun isThreadDescriptor(): Boolean
    abstract fun isCatalogDescriptor(): Boolean

    fun siteName() = boardDescriptor.siteDescriptor.siteName
    fun boardCode() = boardDescriptor.boardCode

    class ThreadDescriptor(
            boardDescriptor: BoardDescriptor,
            val opNo: Long
    ) : ChanDescriptor(boardDescriptor) {

        override fun isThreadDescriptor(): Boolean = true
        override fun isCatalogDescriptor(): Boolean = false

        companion object {
            @JvmStatic
            fun create(siteName: String, boardCode: String, threadNo: Long): ThreadDescriptor {
                require(threadNo > 0) { "Bad threadId: $threadNo" }

                return ThreadDescriptor(BoardDescriptor.create(siteName, boardCode), threadNo)
            }
        }

    }

    class CatalogDescriptor(
            boardDescriptor: BoardDescriptor
    ) : ChanDescriptor(boardDescriptor) {
        override fun isThreadDescriptor(): Boolean = false
        override fun isCatalogDescriptor(): Boolean = true
    }
}
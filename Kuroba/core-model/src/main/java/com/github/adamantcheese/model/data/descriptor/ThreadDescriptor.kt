package com.github.adamantcheese.model.data.descriptor

data class ThreadDescriptor(
        val boardDescriptor: BoardDescriptor,
        val opNo: Long
) {

    fun siteName() = boardDescriptor.siteDescriptor.siteName
    fun boardCode() = boardDescriptor.boardCode

    companion object {
        @JvmStatic
        fun create(siteName: String, boardCode: String, threadId: Long): ThreadDescriptor {
            return ThreadDescriptor(BoardDescriptor.create(siteName, boardCode), threadId)
        }
    }

}
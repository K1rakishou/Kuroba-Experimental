package com.github.adamantcheese.model.data.descriptor

data class ThreadDescriptor(
        val boardDescriptor: BoardDescriptor,
        val opNo: Long
) {

    fun siteName() = boardDescriptor.siteName
    fun boardCode() = boardDescriptor.boardCode

}
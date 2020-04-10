package com.github.adamantcheese.model.data.descriptor

data class ThreadDescriptor(
        val boardDescriptor: BoardDescriptor,
        val opId: Long
) {

    fun siteName() = boardDescriptor.siteName
    fun boardCode() = boardDescriptor.boardCode

}
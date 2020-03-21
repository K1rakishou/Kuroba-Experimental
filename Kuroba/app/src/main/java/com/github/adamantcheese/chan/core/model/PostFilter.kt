package com.github.adamantcheese.chan.core.model

data class PostFilter(
        val filterHighlightedColor: Int = 0,
        val filterStub: Boolean = false,
        val filterRemove: Boolean = false,
        val filterWatch: Boolean = false,
        val filterReplies: Boolean = false,
        val filterOnlyOP: Boolean = false,
        val filterSaved: Boolean = false
) {

    fun hasFilterParameters(): Boolean {
        return filterRemove || filterHighlightedColor != 0 || filterReplies || filterStub
    }

}
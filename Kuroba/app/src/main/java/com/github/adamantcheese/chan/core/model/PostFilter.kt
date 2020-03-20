package com.github.adamantcheese.chan.core.model

data class PostFilter(
        @get:Synchronized
        val filterHighlightedColor: Int = 0,
        @get:Synchronized
        val filterStub: Boolean = false,
        @get:Synchronized
        val filterRemove: Boolean = false,
        @get:Synchronized
        val filterWatch: Boolean = false,
        @get:Synchronized
        val filterReplies: Boolean = false,
        @get:Synchronized
        val filterOnlyOP: Boolean = false,
        @get:Synchronized
        val filterSaved: Boolean = false
) {

    @Synchronized
    fun hasFilterParameters(): Boolean {
        return filterRemove || filterHighlightedColor != 0 || filterReplies || filterStub
    }

}
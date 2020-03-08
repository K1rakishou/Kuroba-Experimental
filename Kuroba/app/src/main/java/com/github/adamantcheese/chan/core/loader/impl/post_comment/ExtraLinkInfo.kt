package com.github.adamantcheese.chan.core.loader.impl.post_comment

internal data class ExtraLinkInfo(
        val title: String? = null,
        val duration: String? = null
) {
    fun isEmpty() = title.isNullOrEmpty() && duration.isNullOrEmpty()

    companion object {
        fun empty(): ExtraLinkInfo = ExtraLinkInfo()
    }
}
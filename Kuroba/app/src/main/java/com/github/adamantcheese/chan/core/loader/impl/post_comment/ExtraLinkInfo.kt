package com.github.adamantcheese.chan.core.loader.impl.post_comment

internal data class ExtraLinkInfo(
        val error: Boolean = false,
        val title: String? = null,
        val duration: String? = null
) {
    fun isEmpty() = title.isNullOrEmpty() && duration.isNullOrEmpty()

    companion object {
        fun empty(): ExtraLinkInfo = ExtraLinkInfo()
        fun error(): ExtraLinkInfo = ExtraLinkInfo(error = true)
        fun value(title: String?, duration: String?) = ExtraLinkInfo(false, title, duration)
    }
}
package com.github.adamantcheese.chan.core.loader.impl.post_comment

internal sealed class ExtraLinkInfo {
    class Success(val title: String?, val duration: String?) : ExtraLinkInfo() {
        fun isEmpty() = title.isNullOrEmpty() && duration.isNullOrEmpty()
    }

    object Error : ExtraLinkInfo()
    object NotAvailable : ExtraLinkInfo()
}
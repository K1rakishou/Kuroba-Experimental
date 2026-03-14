package com.github.k1rakishou.chan.core.site.parser.style

import com.github.k1rakishou.chan.core.site.parser.PostParser
import com.github.k1rakishou.core_parser.comment.HtmlTag
import com.github.k1rakishou.model.data.post.ChanPostBuilder

class StyleRulesParams(
    val text: CharSequence,
    val htmlTag: HtmlTag,
    val callback: PostParser.Callback?,
    val post: ChanPostBuilder?,
    val forceHttpsScheme: Boolean,
    val revealTextSpoilers: Boolean,
)

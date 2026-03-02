package com.github.k1rakishou.chan.core.site.parser.style

import com.github.k1rakishou.chan.core.site.parser.PostParser
import com.github.k1rakishou.core_parser.comment.HtmlTag
import com.github.k1rakishou.model.data.post.ChanPostBuilder

class StyleRulesParams(
    @JvmField val text: CharSequence,
    @JvmField val htmlTag: HtmlTag,
    callback: PostParser.Callback?,
    post: ChanPostBuilder?,
    forceHttpsScheme: Boolean
) {
  var callback: PostParser.Callback? = null
    private set
  var post: ChanPostBuilder? = null
    private set
  var isForceHttpsScheme: Boolean = true
    private set

  init {
    this.callback = callback
    this.post = post
    this.isForceHttpsScheme = forceHttpsScheme
  }
}

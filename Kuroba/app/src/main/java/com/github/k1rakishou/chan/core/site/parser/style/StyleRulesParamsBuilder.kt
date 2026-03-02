package com.github.k1rakishou.chan.core.site.parser.style

import com.github.k1rakishou.chan.core.site.parser.PostParser
import com.github.k1rakishou.core_parser.comment.HtmlTag
import com.github.k1rakishou.model.data.post.ChanPostBuilder
import java.util.Objects

class StyleRulesParamsBuilder {
  private var text: CharSequence? = null
  private var htmlTag: HtmlTag? = null
  private var callback: PostParser.Callback? = null
  private var post: ChanPostBuilder? = null
  private var forceHttpsScheme = true

  fun withCallback(callback: PostParser.Callback?): StyleRulesParamsBuilder {
    this.callback = callback
    return this
  }

  fun withPostBuilder(post: ChanPostBuilder?): StyleRulesParamsBuilder {
    this.post = post
    return this
  }

  fun withText(text: CharSequence?): StyleRulesParamsBuilder {
    this.text = text
    return this
  }

  fun withHtmlTag(htmlTag: HtmlTag?): StyleRulesParamsBuilder {
    this.htmlTag = htmlTag
    return this
  }

  fun forceHttpsScheme(forceHttpsScheme: Boolean): StyleRulesParamsBuilder {
    this.forceHttpsScheme = forceHttpsScheme
    return this
  }

  fun build(): StyleRulesParams {
    Objects.requireNonNull<CharSequence?>(text, "text must not bel null")
    Objects.requireNonNull<HtmlTag?>(htmlTag, "htmlTag must not bel null")

    return StyleRulesParams(text!!, htmlTag!!, callback, post, forceHttpsScheme)
  }
}
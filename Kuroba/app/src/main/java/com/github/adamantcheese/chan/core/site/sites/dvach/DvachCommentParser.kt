package com.github.adamantcheese.chan.core.site.sites.dvach

import android.text.TextUtils
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.site.common.vichan.VichanCommentParser
import com.github.adamantcheese.chan.core.site.parser.ICommentParser
import com.github.adamantcheese.chan.core.site.parser.MockReplyManager
import com.github.adamantcheese.chan.core.site.parser.PostParser
import com.github.adamantcheese.chan.core.site.parser.StyleRule
import com.github.adamantcheese.chan.ui.text.span.PostLinkable
import com.github.adamantcheese.chan.ui.text.span.PostLinkable.Value.*
import com.github.adamantcheese.common.groupOrNull
import com.github.adamantcheese.model.data.descriptor.BoardDescriptor
import org.jsoup.nodes.Element
import java.util.regex.Matcher
import java.util.regex.Pattern

class DvachCommentParser(mockReplyManager: MockReplyManager) : VichanCommentParser(mockReplyManager), ICommentParser {

  override fun addDefaultRules(): DvachCommentParser {
    super.addDefaultRules()
    rule(StyleRule.tagRule("span").cssClass("s").strikeThrough())
    rule(StyleRule.tagRule("span").cssClass("u").underline())
    return this
  }

  override fun matchAnchor(
    post: Post.Builder,
    text: CharSequence,
    anchor: Element,
    callback: PostParser.Callback
  ): PostLinkable.Link {
    val href = extractQuote(anchor.attr("href"), post)
    val currentThreadNo = post.opId

    val quoteMatcher = QUOTE_PATTERN.matcher(href)
    if (!quoteMatcher.find()) {
      return handleNotQuote(post, href, text, callback)
    }

    val boardCode = quoteMatcher.groupOrNull(1)
    val threadNo = quoteMatcher.groupOrNull(2)?.toLong()

    if (boardCode == null || threadNo == null) {
      return handleNotQuote(post, href, text, callback)
    }

    val postNo = extractPostIdOrReplaceWithThreadId(quoteMatcher, threadNo, 3)
    if (currentThreadNo != threadNo) {
      // handle external quote
      return handleExternalLink(post, callback, text, boardCode, threadNo, postNo)
    }

    return handleInternalLink(callback, text, postNo)
  }

  private fun handleNotQuote(
    post: Post.Builder,
    href: String,
    text: CharSequence,
    callback: PostParser.Callback
  ): PostLinkable.Link {
    val siteDescriptor = post.boardDescriptor!!.siteDescriptor
    val boardLinkMatcher = BOARD_LINK_PATTERN.matcher(href)

    if (boardLinkMatcher.find()) {
      val boardCode = boardLinkMatcher.groupOrNull(1)
      if (!boardCode.isNullOrEmpty()) {
        val boardDescriptor = BoardDescriptor.create(siteDescriptor, boardCode)
        if (callback.isValidBoard(boardDescriptor)) {
          // board link
          return PostLinkable.Link(PostLinkable.Type.BOARD, text, StringValue(boardCode))
        }
      }
    }

    // normal link
    return PostLinkable.Link(
      PostLinkable.Type.LINK,
      text,
      StringValue(href)
    )
  }

  override fun appendSuffixes(
    callback: PostParser.Callback,
    post: Post.Builder,
    handlerLink: PostLinkable.Link,
    postNo: Long,
    postSubNo: Long
  ) {
    // Append (OP) when it's a reply to OP
    if (postNo == post.opId) {
      // 2ch.hk automatically appends (OP) at the end of quotes that quote OPs so we don't really
      // need to do it by ourselves, but we still do just in case.
      if (!handlerLink.key.endsWith(OP_REPLY_SUFFIX)) {
        handlerLink.key = TextUtils.concat(handlerLink.key, OP_REPLY_SUFFIX)
      }
    }

    if (handlerLink.type == PostLinkable.Type.DEAD) {
      handlerLink.key = TextUtils.concat(handlerLink.key, DEAD_REPLY_SUFFIX)
    }

    // Append (You) when it's a reply to a saved reply, (Me) if it's a self reply
    if (callback.isSaved(postNo, postSubNo)) {
      if (post.isSavedReply) {
        handlerLink.key = TextUtils.concat(handlerLink.key, SAVED_REPLY_SELF_SUFFIX)
      } else {
        handlerLink.key = TextUtils.concat(handlerLink.key, SAVED_REPLY_OTHER_SUFFIX)
      }
    }
  }

  private fun handleExternalLink(
    post: Post.Builder,
    callback: PostParser.Callback,
    text: CharSequence,
    boardCode: String,
    threadNo: Long,
    postNo: Long
  ): PostLinkable.Link {
    if (boardCode == post.boardDescriptor!!.boardCode && callback.isInternal(postNo)) {
      // link to post in same thread with post number (>>post)
      return PostLinkable.Link(PostLinkable.Type.QUOTE, text, LongValue(postNo))
    }

    // link to post not in same thread with post number (>>post or >>>/board/post)
    return PostLinkable.Link(PostLinkable.Type.THREAD, text, ThreadLink(boardCode, threadNo, postNo))
  }

  private fun handleInternalLink(
    callback: PostParser.Callback,
    text: CharSequence,
    postNo: Long
  ): PostLinkable.Link {
    if (callback.isInternal(postNo)) {
      return PostLinkable.Link(PostLinkable.Type.QUOTE, text, LongValue(postNo))
    }

    return PostLinkable.Link(PostLinkable.Type.DEAD, text, LongValue(postNo))
  }

  @Suppress("SameParameterValue")
  private fun extractPostIdOrReplaceWithThreadId(externalMatcher: Matcher, threadId: Long, group: Int): Long {
    val postIdGroup = externalMatcher.groupOrNull(group)

    if (TextUtils.isEmpty(postIdGroup)) {
      return threadId
    }

    return postIdGroup!!.toLong()
  }

  companion object {
    // board link - https://2ch.hk/mobi
    // post quote - /po/res/39410052.html#39410422
    // full quote - https://2ch.hk/po/res/39420150.html#39420150
    private val POST_ID_PATTERN = Pattern.compile("#(\\d+)$")
    private val BOARD_LINK_PATTERN = Pattern.compile("/(\\w+)/?\$")
    private val QUOTE_PATTERN = Pattern.compile("/(\\w+)/\\w+/(\\d+).html(?:#(\\d+))?")
  }
}
package com.github.k1rakishou.chan.core.site.sites.dvach

import android.text.TextUtils
import com.github.k1rakishou.chan.core.site.common.vichan.VichanCommentParser
import com.github.k1rakishou.chan.core.site.parser.ICommentParser
import com.github.k1rakishou.chan.core.site.parser.PostParser
import com.github.k1rakishou.common.CommentParserConstants
import com.github.k1rakishou.common.groupOrNull
import com.github.k1rakishou.core_parser.comment.HtmlTag
import com.github.k1rakishou.core_spannable.PostLinkable
import com.github.k1rakishou.model.data.post.ChanPostBuilder
import java.util.regex.Matcher
import java.util.regex.Pattern

class DvachCommentParser : VichanCommentParser(), ICommentParser {

  init {
    addDefaultRules()
  }

  override fun matchAnchor(
    post: ChanPostBuilder,
    text: CharSequence,
    anchorTag: HtmlTag,
    callback: PostParser.Callback
  ): PostLinkable.Link {
    val href = extractQuote(anchorTag.attrUnescapedOrNull("href"), post)
    val currentThreadNo = post.opId

    val quoteMatcher = QUOTE_PATTERN.matcher(href)
    if (!quoteMatcher.find()) {
      return handleNotQuote(href, text)
    }

    val boardCode = quoteMatcher.groupOrNull(1)
    val threadNo = quoteMatcher.groupOrNull(2)?.toLong()

    if (boardCode == null || threadNo == null) {
      return handleNotQuote(href, text)
    }

    val postNo = extractPostIdOrReplaceWithThreadId(quoteMatcher, threadNo, 3)
    if (currentThreadNo != threadNo) {
      // handle external quote
      return handleExternalLink(post, callback, text, boardCode, threadNo, postNo)
    }

    return handleInternalLink(post, callback, text, postNo)
  }

  private fun handleNotQuote(
    href: String,
    text: CharSequence
  ): PostLinkable.Link {
    val boardLinkMatcher = BOARD_LINK_PATTERN.matcher(href)
    if (boardLinkMatcher.find()) {
      val boardCode = boardLinkMatcher.groupOrNull(1)
      if (!boardCode.isNullOrEmpty()) {
        // board link (probably)
        return PostLinkable.Link(PostLinkable.Type.BOARD, text, PostLinkable.Value.StringValue(boardCode))
      }
    }

    // normal link
    return PostLinkable.Link(
      PostLinkable.Type.LINK,
      text,
      PostLinkable.Value.StringValue(href)
    )
  }

  override fun appendSuffixes(
    callback: PostParser.Callback,
    post: ChanPostBuilder,
    handlerLink: PostLinkable.Link,
    postNo: Long,
    postSubNo: Long
  ) {
    // Append (OP) when it's a reply to OP
    if (postNo == post.opId) {
      // 2ch.hk automatically appends (OP) at the end of quotes that quote OPs so we don't really
      // need to do it by ourselves, but we still do just in case.
      if (!handlerLink.key.endsWith(CommentParserConstants.OP_REPLY_SUFFIX)) {
        handlerLink.key = TextUtils.concat(handlerLink.key, CommentParserConstants.OP_REPLY_SUFFIX)
      }
    }

    if (handlerLink.type == PostLinkable.Type.DEAD) {
      handlerLink.key = TextUtils.concat(handlerLink.key, CommentParserConstants.DEAD_REPLY_SUFFIX)
    }

    // Append (You) when it's a reply to a saved reply, (Me) if it's a self reply
    if (callback.isSaved(post.opId, postNo, postSubNo)) {
      if (post.isSavedReply) {
        handlerLink.key = TextUtils.concat(handlerLink.key, CommentParserConstants.SAVED_REPLY_SELF_SUFFIX)
      } else {
        handlerLink.key = TextUtils.concat(handlerLink.key, CommentParserConstants.SAVED_REPLY_OTHER_SUFFIX)
      }
    }

    val hiddenOrRemoved = callback.isHiddenOrRemoved(post.opId, postNo, postSubNo)
    if (hiddenOrRemoved != PostParser.NORMAL_POST) {
      val suffix = if (hiddenOrRemoved == PostParser.HIDDEN_POST) {
        CommentParserConstants.HIDDEN_POST_SUFFIX
      } else {
        CommentParserConstants.REMOVED_POST_SUFFIX
      }

      handlerLink.key = TextUtils.concat(handlerLink.key, suffix)
    }
  }

  private fun handleExternalLink(
    post: ChanPostBuilder,
    callback: PostParser.Callback,
    text: CharSequence,
    boardCode: String,
    threadNo: Long,
    postNo: Long
  ): PostLinkable.Link {
    if (boardCode == post.boardDescriptor!!.boardCode && callback.isInternal(postNo)) {
      // link to post in same thread with post number (>>post)
      return PostLinkable.Link(PostLinkable.Type.QUOTE, text, PostLinkable.Value.LongValue(postNo))
    }

    // link to post not in same thread with post number (>>post or >>>/board/post)
    return PostLinkable.Link(
      PostLinkable.Type.THREAD,
      text,
      PostLinkable.Value.ThreadOrPostLink(boardCode, threadNo, postNo)
    )
  }

  private fun handleInternalLink(
    post: ChanPostBuilder,
    callback: PostParser.Callback,
    text: CharSequence,
    postNo: Long
  ): PostLinkable.Link {
    if (!callback.isInternal(postNo)) {
      return PostLinkable.Link(PostLinkable.Type.DEAD, text, PostLinkable.Value.LongValue(postNo))
    }

    when (callback.isHiddenOrRemoved(post.opId, postNo, 0)) {
      PostParser.HIDDEN_POST,
      PostParser.REMOVED_POST -> {
        // Quote pointing to a (locally) hidden or removed post
        return PostLinkable.Link(PostLinkable.Type.QUOTE_TO_HIDDEN_OR_REMOVED_POST, text, PostLinkable.Value.LongValue(postNo))
      }
      else -> {
        // Normal post quote
        return PostLinkable.Link(PostLinkable.Type.QUOTE, text, PostLinkable.Value.LongValue(postNo))
      }
    }
  }

  @Suppress("SameParameterValue")
  private fun extractPostIdOrReplaceWithThreadId(externalMatcher: Matcher, threadId: Long, group: Int): Long {
    val postIdGroup = externalMatcher.groupOrNull(group)

    if (postIdGroup.isNullOrEmpty()) {
      return threadId
    }

    return postIdGroup.toLong()
  }

  companion object {
    // board links:
    // https://2ch.hk/mobi/
    // https://2-ch.hk/mobi
    // https://2-ch.so/mobi
    // https://2ch.life/mobi
    // /mobi/
    private val BOARD_LINK_PATTERN = Pattern.compile("(?:^|2-?ch\\..*)\\/(\\w+)\\/?\$")

    // full quote - https://2ch.hk/po/res/39420150.html#39420150
    private val QUOTE_PATTERN = Pattern.compile("/(\\w+)/\\w+/(\\d+).html(?:#(\\d+))?")
  }
}
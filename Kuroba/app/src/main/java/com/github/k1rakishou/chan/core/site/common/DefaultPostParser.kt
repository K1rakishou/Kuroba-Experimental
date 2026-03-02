package com.github.k1rakishou.chan.core.site.common

import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.TextUtils
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.site.common.PostParserHelper.detectAndMarkThemeJsonSpan
import com.github.k1rakishou.chan.core.site.parser.CommentParser
import com.github.k1rakishou.chan.core.site.parser.CommentParserHelper.detectLinks
import com.github.k1rakishou.chan.core.site.parser.PostParser
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.FoolFuukaCommentParser
import com.github.k1rakishou.common.groupOrNull
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_parser.comment.HtmlNode
import com.github.k1rakishou.core_parser.comment.HtmlParser
import com.github.k1rakishou.core_spannable.PostLinkable
import com.github.k1rakishou.core_spannable.PostLinkable.Value.ArchiveThreadLink
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostBuilder
import org.jsoup.parser.Parser

open class DefaultPostParser(
  private val commentParser: CommentParser,
  private val archivesManager: ArchivesManager
) : PostParser {
  private val htmlParserThreadLocal = ThreadLocal<HtmlParser?>()

  open fun defaultName(): String {
    return CHAN4_DEFAULT_POSTER_NAME
  }

  override fun parseFull(builder: ChanPostBuilder, callback: PostParser.Callback): ChanPost {
    parseNameAndSubject(builder)

    if (!builder.postCommentBuilder.commentAlreadyParsed()) {
      if (builder.postCommentBuilder.hasUnparsedComment()) {
        val parsedComment = parseComment(
          builder,
          builder.postCommentBuilder.getUnparsedComment(),
          callback
        )

        detectAndMarkThemeJsonSpan(parsedComment)

        builder.postCommentBuilder.setParsedComment(parsedComment)
      } else {
        builder.postCommentBuilder.setUnparsedComment("")
        builder.postCommentBuilder.setParsedComment(SpannableString(""))
      }
    }

    return builder.build()
  }

  override fun parseNameAndSubject(builder: ChanPostBuilder) {
    if (!TextUtils.isEmpty(builder.name)) {
      builder.name = Parser.unescapeEntities(builder.name, false)
    }

    if (!TextUtils.isEmpty(builder.subject)) {
      builder.subject = Parser.unescapeEntities(builder.subject.toString(), false)
    }

    val anonymize = ChanSettings.anonymize.get()
    val anonymizeIds = ChanSettings.anonymizeIds.get()

    if (anonymize) {
      builder.name("")
      builder.tripcode("")
    }

    if (anonymizeIds) {
      builder.posterId("")
    }

    if (builder.name == defaultName() && !ChanSettings.showAnonymousName.get()) {
      builder.name("")
    }
  }

  override fun parseComment(
    post: ChanPostBuilder,
    commentRaw: CharSequence,
    callback: PostParser.Callback
  ): Spannable {
    if (commentRaw.isEmpty()) {
      return SpannableString.valueOf(commentRaw)
    }

    val total = SpannableStringBuilder("")

    try {
      val comment = commentRaw.toString().replace("<wbr>", "")

      val htmlParser = htmlParserThreadLocal.get()
      val localParser = if (htmlParser != null) {
        htmlParser
      } else {
        htmlParserThreadLocal.set(HtmlParser())
        htmlParserThreadLocal.get()!!
      }

      val document = localParser.parse(comment)

      val nodes = document.nodes
      val texts = ArrayList<CharSequence?>(nodes.size)

      for (node in nodes) {
        val nodeParsed = parseNode(post, callback, node)
        if (nodeParsed != null) {
          texts.add(nodeParsed)
        }
      }

      for (text in texts) {
        total.append(text)
      }
    } catch (error: Throwable) {
      Logger.error(TAG, error) { "Error parsing comment html" }
    }

    return SpannableString.valueOf(total)
  }

  private fun parseNode(
    post: ChanPostBuilder,
    callback: PostParser.Callback?,
    node: HtmlNode
  ): CharSequence? {
    when (node) {
      is HtmlNode.Text -> {
        val text = postProcessText(node, node.text)
        val forceHttpsScheme = ChanSettings.forceHttpsUrlScheme.get()

        return detectLinks(
          post = post,
          text = text,
          forceHttpsScheme = forceHttpsScheme,
          linkHandler = { link -> this.handleLink(link) }
        )
      }
      is HtmlNode.Tag -> {
        val tag = commentParser.preprocessTag(node)
        val nodeName = tag.tagName

        // Recursively call parseNode with the nodes of the paragraph.
        val innerNodes = tag.children
        val texts = ArrayList<CharSequence?>(innerNodes.size + 1)

        for (innerNode in innerNodes) {
          val nodeParsed = parseNode(post, callback, innerNode)
          if (nodeParsed != null) {
            texts.add(nodeParsed)
          }
        }

        val allInnerText = TextUtils.concat(*texts.toTypedArray<CharSequence?>())

        val result = commentParser.handleTag(
          callback = callback,
          post = post,
          tag = nodeName,
          text = allInnerText,
          htmlTag = tag
        )

        if (result != null) {
          return result
        }

        return allInnerText
      }
    }
  }

  protected open fun postProcessText(textNode: HtmlNode.Text, text: String): String {
    return text
  }

  private fun handleLink(link: CharSequence): PostLinkable? {
    val archiveType = archivesManager.extractArchiveTypeFromLinkOrNull(link)
    if (archiveType == null) {
      return null
    }

    val archiveLinkPattern = FoolFuukaCommentParser.ALL_ARCHIVE_LINKS_PATTERNS_MAP.get(archiveType)
    if (archiveLinkPattern == null) {
      return null
    }

    val matcher = archiveLinkPattern.matcher(link)
    if (!matcher.find()) {
      return null
    }

    val boardCode = matcher.groupOrNull(1)
    if (boardCode == null || TextUtils.isEmpty(boardCode)) {
      return null
    }

    val threadNoStr = matcher.groupOrNull(2)
    if (threadNoStr == null || TextUtils.isEmpty(threadNoStr)) {
      return null
    }

    val postNoStr = matcher.groupOrNull(3)
    var postNo: Long? = null
    if (postNoStr != null) {
      postNo = postNoStr.toLongOrNull()
    }

    if (postNo == null) {
      return null
    }

    val threadNo = threadNoStr.toLongOrNull()
    if (threadNo == null || threadNo <= 0) {
      return null
    }

    if (postNo <= 0) {
      postNo = threadNo
    }

    val archiveThreadLink = ArchiveThreadLink(
      archiveType = archiveType,
      board = boardCode,
      threadId = threadNo,
      postId = postNo,
      postSubId = 0L
    )

    return PostLinkable(
      key = archiveThreadLink.urlText(),
      linkableValue = archiveThreadLink,
      type = PostLinkable.Type.ARCHIVE
    )
  }

  companion object {
    private const val TAG = "DefaultPostParser"

    const val CHAN4_DEFAULT_POSTER_NAME: String = "Anonymous"
  }
}

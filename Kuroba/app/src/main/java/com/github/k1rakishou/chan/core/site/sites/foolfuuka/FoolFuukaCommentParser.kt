package com.github.k1rakishou.chan.core.site.sites.foolfuuka

import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.site.parser.CommentParser
import com.github.k1rakishou.chan.core.site.parser.ICommentParser
import com.github.k1rakishou.chan.core.site.parser.PostParser
import com.github.k1rakishou.chan.core.site.parser.style.StyleRule
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.common.data.ArchiveType
import com.github.k1rakishou.common.groupOrNull
import com.github.k1rakishou.core_parser.comment.HtmlNode
import com.github.k1rakishou.core_parser.comment.HtmlTag
import com.github.k1rakishou.core_spannable.PostLinkable
import com.github.k1rakishou.core_spannable.PostLinkable.Value.ThreadOrPostLink
import com.github.k1rakishou.core_themes.ChanThemeColorId
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPostBuilder
import java.util.regex.Pattern

class FoolFuukaCommentParser(
  private val archivesManager: ArchivesManager
) : CommentParser(), ICommentParser {

  init {
    addDefaultRules()

    addRule(
      StyleRule.tagRule("pre")
        .monospace()
        .size(AppModuleAndroidUtils.sp(12f))
        .backgroundColorId(ChanThemeColorId.BackColorSecondary)
        .foregroundColorId(ChanThemeColorId.TextColorPrimary)
    )

  }

  override fun preprocessTag(node: HtmlNode.Tag): HtmlTag {
    val htmlTag = node.htmlTag
    if (htmlTag.tagName != "span") {
      return super.preprocessTag(node)
    }

    val hasGreenTextAttr = htmlTag.classAttrOrNull() == "greentext"
    if (hasGreenTextAttr) {
      val firstAnchorTag = htmlTag.getTagsByName("a").firstOrNull()
      if (firstAnchorTag != null) {
        return firstAnchorTag
      }
    }

    return super.preprocessTag(node)
  }

  override fun matchAnchor(
    post: ChanPostBuilder,
    text: CharSequence,
    anchorTag: HtmlTag,
    callback: PostParser.Callback
  ): PostLinkable.Link {
    val href = anchorTag.attrUnescapedOrNull("href")
    if (href == null) {
      return PostLinkable.Link(PostLinkable.Type.LINK, text, PostLinkable.Value.StringValue(anchorTag.text()))
    }

    // Must be a valid archive link to avoid matching other site's links
    val matcher = getFoolFuukaDefaultQuotePattern(post.postDescriptor)?.matcher(href)
    if (matcher != null && matcher.matches()) {
      val externalMatcher = getFoolFuukaFullQuotePattern(post.postDescriptor)?.matcher(href)
      if (externalMatcher != null && externalMatcher.find()) {
        val board = externalMatcher.groupOrNull(1)
        val threadId = externalMatcher.groupOrNull(2)?.toLong()

        if (board != null && threadId != null) {
          val postId = externalMatcher.groupOrNull(3)?.toLongOrNull() ?: threadId

          val isInternalQuote = board == post.boardDescriptor!!.boardCode
            && !callback.isParsingCatalogPosts
            && callback.isInternal(postId)

          if (isInternalQuote) {
            when (callback.isHiddenOrRemoved(post.opId, postId, 0)) {
              PostParser.HIDDEN_POST,
              PostParser.REMOVED_POST -> {
                // Quote pointing to a (locally) hidden or removed post
                return PostLinkable.Link(PostLinkable.Type.QUOTE_TO_HIDDEN_OR_REMOVED_POST, text, PostLinkable.Value.LongValue(postId))
              }
              else -> {
                // Normal post quote
                return PostLinkable.Link(PostLinkable.Type.QUOTE, text, PostLinkable.Value.LongValue(postId))
              }
            }
          }

          // link to post not in same thread with post number (>>post or >>>/board/post)
          return PostLinkable.Link(PostLinkable.Type.THREAD, text, ThreadOrPostLink(board, threadId, postId))
        }

        // fallthrough
      }

      val quoteMatcher = getFoolFuukaInternalQuotePattern(post.postDescriptor)?.matcher(href)
      if (quoteMatcher != null && quoteMatcher.find()) {
        val postId = quoteMatcher.groupOrNull(3)?.toLongOrNull()
        if (postId != null) {
          val type = if (callback.isInternal(postId)) {
            // Normal post quote
            PostLinkable.Type.QUOTE
          } else {
            // Most likely a quote to a deleted post (Or any other post that we don't have
            // in the cache).
            PostLinkable.Type.DEAD
          }

          return PostLinkable.Link(type, text, PostLinkable.Value.LongValue(postId))
        }

        // fallthrough
      }

      // fallthrough
    }

    // normal link
    return PostLinkable.Link(PostLinkable.Type.LINK, text, PostLinkable.Value.StringValue(href))
  }

  private fun getFoolFuukaFullQuotePattern(postDescriptor: PostDescriptor?): Pattern? {
    if (postDescriptor == null) {
      return null
    }

    return when (getArchiveType(postDescriptor)) {
      ArchiveType.WakarimasenMoe -> WAKARIMASEN_FULL_QUOTE_PATTERN

      ArchiveType.ForPlebs,
      ArchiveType.Nyafuu,
      ArchiveType.Warosu,
      ArchiveType.DesuArchive,
      ArchiveType.Fireden,
      ArchiveType.B4k,
      ArchiveType.Bstats,
      ArchiveType.ArchivedMoe,
      ArchiveType.TheBarchive,
      ArchiveType.ArchiveOfSins,
      ArchiveType.TokyoChronos -> FULL_QUOTE_PATTERN
      null -> null
    }
  }

  private fun getFoolFuukaInternalQuotePattern(postDescriptor: PostDescriptor?): Pattern? {
    if (postDescriptor == null) {
      return null
    }

    return when (getArchiveType(postDescriptor)) {
      ArchiveType.WakarimasenMoe -> WAKARIMASEN_INTERNAL_QUOTE_PATTERN

      ArchiveType.ForPlebs,
      ArchiveType.Nyafuu,
      ArchiveType.Warosu,
      ArchiveType.DesuArchive,
      ArchiveType.Fireden,
      ArchiveType.B4k,
      ArchiveType.Bstats,
      ArchiveType.ArchivedMoe,
      ArchiveType.TheBarchive,
      ArchiveType.ArchiveOfSins,
      ArchiveType.TokyoChronos -> INTERNAL_QUOTE_PATTERN
      null -> null
    }
  }

  private fun getFoolFuukaDefaultQuotePattern(postDescriptor: PostDescriptor?): Pattern? {
    if (postDescriptor == null) {
      return null
    }

    return when (getArchiveType(postDescriptor)) {
      ArchiveType.ForPlebs -> FOR_PLEBS_DEFAULT_QUOTE_PATTERN
      ArchiveType.Nyafuu -> NYAFUU_DEFAULT_QUOTE_PATTERN
      ArchiveType.DesuArchive -> DESU_ARCHIVE_DEFAULT_QUOTE_PATTERN
      ArchiveType.Fireden -> FIREDEN_DEFAULT_QUOTE_PATTERN
      ArchiveType.B4k -> B4K_DEFAULT_QUOTE_PATTERN
      ArchiveType.ArchivedMoe -> ARCHIVED_MOE_DEFAULT_QUOTE_PATTERN
      ArchiveType.ArchiveOfSins -> ARCHIVE_OF_SINS_DEFAULT_QUOTE_PATTERN
      ArchiveType.TokyoChronos -> TOKYO_CHRONOS_DEFAULT_QUOTE_PATTERN
      ArchiveType.WakarimasenMoe -> WAKARIMASEN_DEFAULT_QUOTE_PATTERN

      // Not a FoolFuuka archive
      ArchiveType.Warosu,

      // See ArchivesManager.disabledArchives
      ArchiveType.TheBarchive,
      ArchiveType.Bstats,
      null -> null
    }
  }

  private fun getArchiveType(postDescriptor: PostDescriptor): ArchiveType? {
    val archiveDescriptor = archivesManager.byBoardDescriptor(postDescriptor.boardDescriptor())
      ?: return null

    return archiveDescriptor.archiveType
  }

  companion object {
    private const val TAG = "FoolFuukaCommentParser"

    // An archive quote link may look like one of these:
    // https://archive.domain/g/thread/75659307#75659307
    // https://archive.domain/g/thread/75659307/#75659307
    // https://archive.domain/g/thread/75659307#p75659307
    // https://tokyochronos.net/jp/thread/35737800/#35738075

    private val DESU_ARCHIVE_DEFAULT_QUOTE_PATTERN = Pattern.compile("(?:https:\\/\\/)?desuarchive\\.org\\/(.*?)\\/(?:post|thread)\\/(\\d+)\\/?(?:#)?(\\d+)?\\/?")
    private val B4K_DEFAULT_QUOTE_PATTERN = Pattern.compile("(?:https:\\/\\/)?arch.b4k\\.co\\/(\\w+)\\/(?:post|thread)\\/(\\d+)\\/?(?:#)?(\\d+)?\\/?")
    private val FOR_PLEBS_DEFAULT_QUOTE_PATTERN = Pattern.compile("(?:https:\\/\\/)?archive.4plebs\\.org\\/(.*?)\\/(?:post|thread)\\/(\\d+)\\/?(?:#)?(\\d+)?\\/?")
    private val NYAFUU_DEFAULT_QUOTE_PATTERN = Pattern.compile("(?:https:\\/\\/)?archive.nyafuu\\.org\\/(.*?)\\/(?:post|thread)\\/(\\d+)\\/?(?:#)?(\\d+)?\\/?")
    private val FIREDEN_DEFAULT_QUOTE_PATTERN = Pattern.compile("(?:https:\\/\\/)?boards.fireden\\.net\\/(.*?)\\/(?:post|thread)\\/(\\d+)\\/?(?:#)?(\\d+)?\\/?")
    private val ARCHIVED_MOE_DEFAULT_QUOTE_PATTERN = Pattern.compile("(?:https:\\/\\/)?archived\\.moe\\/(.*?)\\/(?:post|thread)\\/(\\d+)\\/?(?:#)?(\\d+)?\\/?")
    private val ARCHIVE_OF_SINS_DEFAULT_QUOTE_PATTERN = Pattern.compile("(?:https:\\/\\/)?archiveofsins\\.com\\/(.*?)\\/(?:post|thread)\\/(\\d+)\\/?(?:#)?(\\d+)?\\/?")
    private val TOKYO_CHRONOS_DEFAULT_QUOTE_PATTERN = Pattern.compile("(?:https:\\/\\/)?tokyochronos\\.net\\/(.*?)\\/(?:post|thread)\\/?(\\d+)\\/(?:#)?(\\d+)?\\/?")
    private val WAKARIMASEN_DEFAULT_QUOTE_PATTERN = Pattern.compile("(?:https:\\/\\/)?archive.wakarimasen\\.moe\\/(.*?)\\/(?:thread|post)\\/(\\d+)\\/?(?:#)?(\\d+)?\\/?")

    @JvmField
    val ALL_ARCHIVE_LINKS_PATTERNS_MAP = mapOf<ArchiveType, Pattern>(
      ArchiveType.ForPlebs to FOR_PLEBS_DEFAULT_QUOTE_PATTERN,
      ArchiveType.Nyafuu to NYAFUU_DEFAULT_QUOTE_PATTERN,
      ArchiveType.DesuArchive to DESU_ARCHIVE_DEFAULT_QUOTE_PATTERN,
      ArchiveType.Fireden to FIREDEN_DEFAULT_QUOTE_PATTERN,
      ArchiveType.B4k to B4K_DEFAULT_QUOTE_PATTERN,
      ArchiveType.ArchivedMoe to ARCHIVED_MOE_DEFAULT_QUOTE_PATTERN,
      ArchiveType.ArchiveOfSins to ARCHIVE_OF_SINS_DEFAULT_QUOTE_PATTERN,
      ArchiveType.TokyoChronos to TOKYO_CHRONOS_DEFAULT_QUOTE_PATTERN,
      ArchiveType.WakarimasenMoe to WAKARIMASEN_DEFAULT_QUOTE_PATTERN,
    )

    private val FULL_QUOTE_PATTERN = Pattern.compile("\\/(\\w+)\\/\\w+\\/(\\d+)\\/?(?:#p?(\\d+))?")
    private val INTERNAL_QUOTE_PATTERN = Pattern.compile("\\/(\\w+)\\/\\w+\\/(\\d+)\\/?(?:#p?(\\d+))?")

    private val WAKARIMASEN_FULL_QUOTE_PATTERN = Pattern.compile("\\/(\\w+)\\/post\\/(\\d+)\\/?")
    private val WAKARIMASEN_INTERNAL_QUOTE_PATTERN = Pattern.compile("\\/(\\w+)\\/thread\\/(\\d+)\\/?(?:#p?(\\d+))?")
  }
}
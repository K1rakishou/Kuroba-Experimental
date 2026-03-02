package com.github.k1rakishou.chan.core.site.sites.foolfuuka

import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.site.parser.CommentParser
import com.github.k1rakishou.chan.core.site.parser.ICommentParser
import com.github.k1rakishou.chan.core.site.parser.PostParser
import com.github.k1rakishou.chan.core.site.parser.style.StyleRule
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.common.data.ArchiveType
import com.github.k1rakishou.common.fixUrlOrNull
import com.github.k1rakishou.common.groupOrNull
import com.github.k1rakishou.core_parser.comment.HtmlNode
import com.github.k1rakishou.core_parser.comment.HtmlTag
import com.github.k1rakishou.core_spannable.PostLinkable
import com.github.k1rakishou.core_spannable.PostLinkable.Value.ThreadOrPostLink
import com.github.k1rakishou.core_themes.ChanThemeColorId
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
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
    val href = fixUrlOrNull(anchorTag.attrUnescapedOrNull("href"))
    if (href == null) {
      return PostLinkable.Link(PostLinkable.Type.LINK, text, PostLinkable.Value.StringValue(anchorTag.text()))
    }

    val boardDescriptor = checkNotNull(post.boardDescriptor) { "Board descriptor must not be null" }

    // Must be a valid archive link to avoid matching other site's links
    val matcher = getFoolFuukaDefaultQuotePattern(boardDescriptor)?.matcher(href)
    if (matcher != null && matcher.matches()) {
      val externalMatcher = getFoolFuukaFullQuotePattern(boardDescriptor)?.matcher(href)
      if (externalMatcher != null && externalMatcher.find()) {
        val board = externalMatcher.groupOrNull(1)
        val threadId = externalMatcher.groupOrNull(2)?.toLong()

        if (board != null && threadId != null) {
          val postId = externalMatcher.groupOrNull(3)?.toLongOrNull() ?: threadId
          val postSubId = externalMatcher.groupOrNull(4)?.toLongOrNull() ?: 0L

          val postDescriptor = PostDescriptor.create(
            boardDescriptor = boardDescriptor,
            threadNo = threadId,
            postNo = postId,
            postSubNo = postSubId
          )

          val isInternalQuote = board == boardDescriptor.boardCode
            && !callback.isParsingCatalogPosts()
            && callback.isInternal(postDescriptor)

          if (isInternalQuote) {
            when (callback.isHiddenOrRemoved(postDescriptor)) {
              PostParser.HIDDEN_POST,
              PostParser.REMOVED_POST -> {
                // Quote pointing to a (locally) hidden or removed post
                return PostLinkable.Link(
                  type = PostLinkable.Type.QUOTE_TO_HIDDEN_OR_REMOVED_POST,
                  key = text,
                  linkValue = PostLinkable.Value.LongPairValue(postId, postSubId)
                )
              }
              else -> {
                // Normal post quote
                return PostLinkable.Link(
                  type = PostLinkable.Type.QUOTE,
                  key = text,
                  linkValue = PostLinkable.Value.LongPairValue(postId, postSubId)
                )
              }
            }
          }

          // link to post not in same thread with post number (>>post or >>>/board/post)
          return PostLinkable.Link(
            type = PostLinkable.Type.THREAD,
            key = text,
            linkValue = ThreadOrPostLink(
              board = board,
              threadId = threadId,
              postId = postId,
              postSubId = postSubId
            )
          )
        }

        // fallthrough
      }

      // fallthrough
    }

    // normal link
    return PostLinkable.Link(
      type = PostLinkable.Type.LINK,
      key = text,
      linkValue = PostLinkable.Value.StringValue(href)
    )
  }

  private fun getFoolFuukaFullQuotePattern(boardDescriptor: BoardDescriptor): Pattern? {
    return when (getArchiveType(boardDescriptor)) {
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
      ArchiveType.TokyoChronos,
      ArchiveType.RozenArcana -> FULL_QUOTE_PATTERN
      ArchiveType.WakarimasenMoe,
      null -> null
    }
  }

  private fun getFoolFuukaDefaultQuotePattern(boardDescriptor: BoardDescriptor): Pattern? {
    return when (getArchiveType(boardDescriptor)) {
      ArchiveType.ForPlebs -> FOR_PLEBS_DEFAULT_QUOTE_PATTERN
      ArchiveType.Nyafuu -> NYAFUU_DEFAULT_QUOTE_PATTERN
      ArchiveType.DesuArchive -> DESU_ARCHIVE_DEFAULT_QUOTE_PATTERN
      ArchiveType.Fireden -> FIREDEN_DEFAULT_QUOTE_PATTERN
      ArchiveType.B4k -> B4K_DEFAULT_QUOTE_PATTERN
      ArchiveType.ArchivedMoe -> ARCHIVED_MOE_DEFAULT_QUOTE_PATTERN
      ArchiveType.ArchiveOfSins -> ARCHIVE_OF_SINS_DEFAULT_QUOTE_PATTERN
      ArchiveType.TokyoChronos -> TOKYO_CHRONOS_DEFAULT_QUOTE_PATTERN
      ArchiveType.RozenArcana -> ROZEN_ARCANA_QUOTE_PATTERN

      // Not a FoolFuuka archive
      ArchiveType.Warosu,

      // Wakarimasen archive is dead
      ArchiveType.WakarimasenMoe,
      // See ArchivesManager.disabledArchives
      ArchiveType.TheBarchive,
      ArchiveType.Bstats,
      null -> null
    }
  }

  private fun getArchiveType(boardDescriptor: BoardDescriptor): ArchiveType? {
    val archiveDescriptor = archivesManager.byBoardDescriptor(boardDescriptor)
      ?: return null

    return archiveDescriptor.archiveType
  }

  @Suppress("MaxLineLength")
  companion object {
    private const val TAG = "FoolFuukaCommentParser"

    // An archive quote link may look like one of these:
    // https://archive.domain/g/thread/75659307#75659307
    // https://archive.domain/g/thread/75659307/#75659307
    // https://archive.domain/g/thread/75659307#p75659307
    // https://tokyochronos.net/jp/thread/35737800/#35738075

    private val DESU_ARCHIVE_DEFAULT_QUOTE_PATTERN =
      Pattern.compile("(?:https:\\/\\/)?desuarchive\\.org\\/(.*?)\\/(?:post|thread)\\/(\\d+)\\/?(?:#)?q?(\\d+)(_\\d+)?\\/?")
    private val B4K_DEFAULT_QUOTE_PATTERN =
      Pattern.compile("(?:https:\\/\\/)?arch.b4k\\.dev\\/(\\w+)\\/(?:post|thread)\\/(\\d+)\\/?(?:#)?(\\d+)?\\/?")
    private val FOR_PLEBS_DEFAULT_QUOTE_PATTERN =
      Pattern.compile("(?:https:\\/\\/)?archive.4plebs\\.org\\/(.*?)\\/(?:post|thread)\\/(\\d+)\\/?(?:#)?(\\d+)?\\/?")
    private val NYAFUU_DEFAULT_QUOTE_PATTERN =
      Pattern.compile("(?:https:\\/\\/)?archive.nyafuu\\.org\\/(.*?)\\/(?:post|thread)\\/(\\d+)\\/?(?:#)?(\\d+)?\\/?")
    private val FIREDEN_DEFAULT_QUOTE_PATTERN =
      Pattern.compile("(?:https:\\/\\/)?boards.fireden\\.net\\/(.*?)\\/(?:post|thread)\\/(\\d+)\\/?(?:#)?(\\d+)?\\/?")
    private val ARCHIVED_MOE_DEFAULT_QUOTE_PATTERN =
      Pattern.compile("(?:https:\\/\\/)?archived\\.moe\\/(.*?)\\/(?:post|thread)\\/(\\d+)\\/?(?:#)?(\\d+)?\\/?")
    private val ARCHIVE_OF_SINS_DEFAULT_QUOTE_PATTERN =
      Pattern.compile("(?:https:\\/\\/)?archiveofsins\\.com\\/(.*?)\\/(?:post|thread)\\/(\\d+)\\/?(?:#)?(\\d+)?\\/?")
    private val TOKYO_CHRONOS_DEFAULT_QUOTE_PATTERN =
      Pattern.compile("(?:https:\\/\\/)?tokyochronos\\.net\\/(.*?)\\/(?:post|thread)\\/?(\\d+)\\/(?:#)?(\\d+)?\\/?")
    private val ROZEN_ARCANA_QUOTE_PATTERN =
      Pattern.compile("(?:https:\\/\\/)?archive.alice\\.al\\/(.*?)\\/(?:thread|post)\\/(\\d+)\\/?(?:#)?(\\d+)?\\/?")

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
      ArchiveType.RozenArcana to ROZEN_ARCANA_QUOTE_PATTERN,
    )

    private val FULL_QUOTE_PATTERN = Pattern.compile("\\/(\\w+)\\/\\w+\\/(\\d+)\\/?(?:#q?p?(\\d+)(?:_(\\d+))?)?")
  }
}
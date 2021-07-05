package com.github.k1rakishou.chan.core.site.sites.foolfuuka

import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.site.parser.CommentParser
import com.github.k1rakishou.chan.core.site.parser.ICommentParser
import com.github.k1rakishou.chan.core.site.parser.PostParser
import com.github.k1rakishou.common.data.ArchiveType
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPostBuilder
import org.jsoup.nodes.Element
import java.util.regex.Pattern

class FoolFuukaCommentParser(
  private val archivesManager: ArchivesManager
) : CommentParser(), ICommentParser {

  init {
    addDefaultRules()
  }

  override fun handleTag(
    callback: PostParser.Callback,
    post: ChanPostBuilder,
    tag: String,
    text: CharSequence,
    element: Element
  ): CharSequence? {
    var newElement = element
    var newTag = tag

    if (element.getElementsByTag("span").hasClass("greentext")
      && element.getElementsByTag("a").isNotEmpty()) {
      newElement = element.getElementsByTag("a").first()
      newTag = "a"
    }

    return super.handleTag(callback, post, newTag, text, newElement)
  }

  override fun getQuotePattern(): Pattern {
    return FULL_QUOTE_PATTERN
  }

  override fun getFullQuotePattern(): Pattern {
    return FULL_QUOTE_PATTERN
  }

  override fun extractQuote(href: String, post: ChanPostBuilder): String {
    val matcher = getDefaultQuotePattern(post.postDescriptor)?.matcher(href)
    if (matcher == null) {
      Logger.d(TAG, "getDefaultQuotePattern returned null for postDescriptor=${post.postDescriptor}")
      return href
    }

    if (!matcher.matches()) {
      return href
    }

    val hrefWithoutScheme = removeSchemeIfPresent(href)
    return hrefWithoutScheme.substring(hrefWithoutScheme.indexOf('/'))
  }

  private fun getDefaultQuotePattern(postDescriptor: PostDescriptor?): Pattern? {
    if (postDescriptor == null) {
      return null
    }

    val archiveDescriptor = archivesManager.byBoardDescriptor(postDescriptor.boardDescriptor())
      ?: return null

    return when (archiveDescriptor.archiveType) {
      ArchiveType.ForPlebs -> FOR_PLEBS_DEFAULT_QUOTE_PATTERN
      ArchiveType.Nyafuu -> NYAFUU_DEFAULT_QUOTE_PATTERN
      ArchiveType.RebeccaBlackTech -> REBECCA_BLACK_TECH_DEFAULT_QUOTE_PATTERN
      ArchiveType.DesuArchive -> DESU_ARCHIVE_DEFAULT_QUOTE_PATTERN
      ArchiveType.Fireden -> FIREDEN_DEFAULT_QUOTE_PATTERN
      ArchiveType.B4k -> B4K_DEFAULT_QUOTE_PATTERN
      ArchiveType.ArchivedMoe -> ARCHIVED_MOE_DEFAULT_QUOTE_PATTERN
      ArchiveType.ArchiveOfSins -> ARCHIVE_OF_SINS_DEFAULT_QUOTE_PATTERN
      ArchiveType.TokyoChronos -> TOKYO_CHRONOS_DEFAULT_QUOTE_PATTERN
      ArchiveType.WakarimasenMoe -> WAKARIMASEN_DEFAULT_QUOTE_PATTERN

      // Not a FoolFuuka archive
      ArchiveType.Warosu,
      ArchiveType.Yukila,

      // See ArchivesManager.disabledArchives
      ArchiveType.TheBarchive,
      ArchiveType.Bstats -> null
    }
  }

  private fun removeSchemeIfPresent(href: String): String {
    if (href.startsWith("https://")) {
      return href.substring("https://".length)
    }

    if (href.startsWith("http://")) {
      return href.substring("http://".length)
    }

    return href
  }

  companion object {
    private const val TAG = "FoolFuukaCommentParser"

    // An archive quote link may look like one of these:
    // https://archive.domain/g/thread/75659307#75659307
    // https://archive.domain/g/thread/75659307/#75659307
    // https://archive.domain/g/thread/75659307#p75659307

    private val DESU_ARCHIVE_DEFAULT_QUOTE_PATTERN = Pattern.compile("(?:https:\\/\\/)?desuarchive\\.org\\/(.*?)\\/thread\\/(\\d+)\\/?(?:#)?(\\d+)?\\/?")
    private val B4K_DEFAULT_QUOTE_PATTERN = Pattern.compile("(?:https:\\/\\/)?arch.b4k\\.co\\/(.*?)\\/thread\\/(\\d+)\\/?(?:#)?(\\d+)?\\/?")
    private val FOR_PLEBS_DEFAULT_QUOTE_PATTERN = Pattern.compile("(?:https:\\/\\/)?archive.4plebs\\.org\\/(.*?)\\/thread\\/(\\d+)\\/?(?:#)?(\\d+)?\\/?")
    private val NYAFUU_DEFAULT_QUOTE_PATTERN = Pattern.compile("(?:https:\\/\\/)?archive.nyafuu\\.org\\/(.*?)\\/thread\\/(\\d+)\\/?(?:#)?(\\d+)?\\/?")
    private val REBECCA_BLACK_TECH_DEFAULT_QUOTE_PATTERN = Pattern.compile("(?:https:\\/\\/)?archive.rebeccablacktech\\.com\\/(.*?)\\/thread\\/(\\d+)\\/?(?:#)?(\\d+)?\\/?")
    private val FIREDEN_DEFAULT_QUOTE_PATTERN = Pattern.compile("(?:https:\\/\\/)?boards.fireden\\.net\\/(.*?)\\/thread\\/(\\d+)\\/?(?:#)?(\\d+)?\\/?")
    private val ARCHIVED_MOE_DEFAULT_QUOTE_PATTERN = Pattern.compile("(?:https:\\/\\/)?archived\\.moe\\/(.*?)\\/thread\\/(\\d+)\\/?(?:#)?(\\d+)?\\/?")
    private val ARCHIVE_OF_SINS_DEFAULT_QUOTE_PATTERN = Pattern.compile("(?:https:\\/\\/)?archiveofsins\\.com\\/(.*?)\\/thread\\/(\\d+)\\/?(?:#)?(\\d+)?\\/?")
    private val TOKYO_CHRONOS_DEFAULT_QUOTE_PATTERN = Pattern.compile("(?:https:\\/\\/)?tokyochronos\\.net\\/(.*?)\\/thread\\/(\\d+)(?:#)?(\\d+)?\\/?")
    private val WAKARIMASEN_DEFAULT_QUOTE_PATTERN = Pattern.compile("(?:https:\\/\\/)?archive.wakarimasen\\.moe\\/(.*?)\\/thread\\/(\\d+)\\/?(?:#)?(\\d+)?\\/?")

    @JvmField
    val ALL_ARCHIVE_LINKS_PATTERNS_MAP = mapOf<ArchiveType, Pattern>(
      ArchiveType.ForPlebs to FOR_PLEBS_DEFAULT_QUOTE_PATTERN,
      ArchiveType.Nyafuu to NYAFUU_DEFAULT_QUOTE_PATTERN,
      ArchiveType.RebeccaBlackTech to REBECCA_BLACK_TECH_DEFAULT_QUOTE_PATTERN,
      ArchiveType.DesuArchive to DESU_ARCHIVE_DEFAULT_QUOTE_PATTERN,
      ArchiveType.Fireden to FIREDEN_DEFAULT_QUOTE_PATTERN,
      ArchiveType.B4k to B4K_DEFAULT_QUOTE_PATTERN,
      ArchiveType.ArchivedMoe to ARCHIVED_MOE_DEFAULT_QUOTE_PATTERN,
      ArchiveType.ArchiveOfSins to ARCHIVE_OF_SINS_DEFAULT_QUOTE_PATTERN,
      ArchiveType.TokyoChronos to TOKYO_CHRONOS_DEFAULT_QUOTE_PATTERN,
      ArchiveType.WakarimasenMoe to WAKARIMASEN_DEFAULT_QUOTE_PATTERN,
    )

    private val FULL_QUOTE_PATTERN = Pattern.compile("/(\\w+)/\\w+/(\\d+)/?#p?(\\d+)")
  }
}
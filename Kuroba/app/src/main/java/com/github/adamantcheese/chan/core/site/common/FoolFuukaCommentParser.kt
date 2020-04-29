package com.github.adamantcheese.chan.core.site.common

import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.site.parser.CommentParser
import com.github.adamantcheese.chan.core.site.parser.PostParser
import com.github.adamantcheese.chan.ui.theme.Theme
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.model.data.descriptor.ArchiveDescriptor
import org.jsoup.nodes.Element
import java.util.regex.Matcher
import java.util.regex.Pattern

class FoolFuukaCommentParser : CommentParser() {

    init {
        addDefaultRules()
    }

    override fun handleTag(
            callback: PostParser.Callback,
            theme: Theme,
            post: Post.Builder,
            tag: String,
            text: CharSequence,
            element: Element
    ): CharSequence {
        var newElement = element
        var newTag = tag

        if (element.getElementsByTag("span").hasClass("greentext")
                && element.getElementsByTag("a").isNotEmpty()) {
            newElement = element.getElementsByTag("a").first()
            newTag = "a"
        }

        return super.handleTag(callback, theme, post, newTag, text, newElement)
    }

    override fun matchBoardSearch(href: String, post: Post.Builder): Matcher {
        // TODO(archives)
        return super.matchBoardSearch(href, post)
    }

    override fun matchBoardLink(href: String, post: Post.Builder): Matcher {
        // TODO(archives)
        return super.matchBoardLink(href, post)
    }

    override fun matchInternalQuote(href: String, post: Post.Builder): Matcher {
        return FULL_QUOTE_PATTERN.matcher(href)
    }

    override fun matchExternalQuote(href: String, post: Post.Builder): Matcher {
        return FULL_QUOTE_PATTERN.matcher(href)
    }

    override fun extractQuote(href: String, post: Post.Builder): String {
        val matcher = getDefaultQuotePattern(post.archiveDescriptor)?.matcher(href)
        if (matcher == null) {
            Logger.d(TAG, "getDefaultQuotePattern returned null for ${post.archiveDescriptor}")
            return href
        }

        if (!matcher.matches()) {
            return href
        }

        val hrefWithoutScheme = removeSchemeIfPresent(href)
        return hrefWithoutScheme.substring(hrefWithoutScheme.indexOf('/'))
    }

    private fun getDefaultQuotePattern(archiveDescriptor: ArchiveDescriptor?): Pattern? {
        if (archiveDescriptor == null) {
            return null
        }

        return when (archiveDescriptor.archiveType) {
            ArchiveDescriptor.ArchiveType.ForPlebs -> FOR_PLEBS_DEFAULT_QUOTE_PATTERN
            ArchiveDescriptor.ArchiveType.Nyafuu -> NYAFUU_DEFAULT_QUOTE_PATTERN
            ArchiveDescriptor.ArchiveType.RebeccaBlackTech -> REBECCA_BLACK_TECH_DEFAULT_QUOTE_PATTERN
            ArchiveDescriptor.ArchiveType.Warosu -> WASORU_BLACK_TECH_DEFAULT_QUOTE_PATTERN
            ArchiveDescriptor.ArchiveType.DesuArchive -> DESU_ARCHIVE_DEFAULT_QUOTE_PATTERN
            ArchiveDescriptor.ArchiveType.Fireden -> FIREDEN_DEFAULT_QUOTE_PATTERN
            ArchiveDescriptor.ArchiveType.B4k -> B4K_DEFAULT_QUOTE_PATTERN
            ArchiveDescriptor.ArchiveType.ArchivedMoe -> ARCHIVED_MOE_DEFAULT_QUOTE_PATTERN
            ArchiveDescriptor.ArchiveType.TheBarchive -> THE_B_ARCHIVE_DEFAULT_QUOTE_PATTERN
            ArchiveDescriptor.ArchiveType.ArchiveOfSins -> ARCHIVE_OF_SINS_DEFAULT_QUOTE_PATTERN
            ArchiveDescriptor.ArchiveType.Bstats -> null
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

        private val DESU_ARCHIVE_DEFAULT_QUOTE_PATTERN =
                Pattern.compile("(https://)?desuarchive\\.org/(.*?)/thread/(\\d*?)/?#p?(\\d*)")
        private val B4K_DEFAULT_QUOTE_PATTERN =
                Pattern.compile("(https://)?arch\\.b4k\\.co/(.*?)/thread/(\\d*?)/?#p?(\\d*)")
        private val FOR_PLEBS_DEFAULT_QUOTE_PATTERN =
                Pattern.compile("(https://)?archive\\.4plebs\\.org/(.*?)/thread/(\\d*?)/?#p?(\\d*)")
        private val NYAFUU_DEFAULT_QUOTE_PATTERN =
                Pattern.compile("(https://)?archive\\.nyafuu\\.org/(.*?)/thread/(\\d*?)/?#p?(\\d*)")
        private val REBECCA_BLACK_TECH_DEFAULT_QUOTE_PATTERN =
                Pattern.compile("(https://)?archive\\.rebeccablacktech\\.com/(.*?)/thread/(\\d*?)/?#p?(\\d*)")
        private val WASORU_BLACK_TECH_DEFAULT_QUOTE_PATTERN =
                Pattern.compile("(https://)?warosu\\.org/(.*?)/thread/(\\d*?)/?#p?(\\d*)")
        private val FIREDEN_DEFAULT_QUOTE_PATTERN =
                Pattern.compile("(https://)?boards\\.fireden\\.net/(.*?)/thread/(\\d*?)/?#p?(\\d*)")
        private val ARCHIVED_MOE_DEFAULT_QUOTE_PATTERN =
                Pattern.compile("(https://)?archived\\.moe/(.*?)/thread/(\\d*?)/?#p?(\\d*)")
        private val THE_B_ARCHIVE_DEFAULT_QUOTE_PATTERN =
                Pattern.compile("(https://)?thebarchive\\.com/(.*?)/thread/(\\d*?)/?#p?(\\d*)")
        private val ARCHIVE_OF_SINS_DEFAULT_QUOTE_PATTERN =
                Pattern.compile("(https://)?archiveofsins\\.com/(.*?)/thread/(\\d*?)/?#p?(\\d*)")

        private val FULL_QUOTE_PATTERN = Pattern.compile("/(\\w+)/\\w+/(\\d+)/?#p?(\\d+)")
    }
}
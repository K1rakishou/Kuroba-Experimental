package com.github.adamantcheese.chan.core.site.common

import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.site.parser.CommentParser
import java.util.regex.Matcher
import java.util.regex.Pattern

class FoolFuukaCommentParser : CommentParser() {

    init {
        addDefaultRules()
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
        val matcher = DEFAULT_QUOTE_PATTERN.matcher(href)
        if (!matcher.matches()) {
            return href
        }

        val hrefWithoutScheme = removeSchemeIfPresent(href)
        return hrefWithoutScheme.substring(hrefWithoutScheme.indexOf('/'));
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
        private val DEFAULT_QUOTE_PATTERN = Pattern.compile("(https://)?desuarchive\\.org/(.*?)/thread/(\\d*?)/#(\\d*)")
        private val FULL_QUOTE_PATTERN = Pattern.compile("/(\\w+)/\\w+/(\\d+)/#(\\d+)")
    }
}
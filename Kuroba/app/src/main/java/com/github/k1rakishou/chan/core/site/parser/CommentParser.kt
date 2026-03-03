package com.github.k1rakishou.chan.core.site.parser

import android.graphics.Typeface
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.core.graphics.ColorUtils
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.site.parser.StaticHtmlColorRepository.getColorValueByHtmlColorName
import com.github.k1rakishou.chan.core.site.parser.style.StyleRule
import com.github.k1rakishou.chan.core.site.parser.style.StyleRulesParams
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.sp
import com.github.k1rakishou.chan.utils.ConversionUtils.colorFromArgb
import com.github.k1rakishou.chan.utils.ConversionUtils.toIntOrNull
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.CommentParserConstants
import com.github.k1rakishou.common.StringUtils.extractFileNameExtension
import com.github.k1rakishou.common.groupOrNull
import com.github.k1rakishou.common.mutableMapWithCap
import com.github.k1rakishou.core_parser.comment.HtmlNode
import com.github.k1rakishou.core_parser.comment.HtmlTag
import com.github.k1rakishou.core_spannable.AbsoluteSizeSpanHashed
import com.github.k1rakishou.core_spannable.BackgroundColorSpanHashed
import com.github.k1rakishou.core_spannable.ForegroundColorIdSpan
import com.github.k1rakishou.core_spannable.ForegroundColorSpanHashed
import com.github.k1rakishou.core_spannable.PostLinkable
import com.github.k1rakishou.core_spannable.PostLinkable.Value.PostIdValue
import com.github.k1rakishou.core_spannable.PostLinkable.Value.SearchLink
import com.github.k1rakishou.core_spannable.PostLinkable.Value.ThreadOrPostLink
import com.github.k1rakishou.core_themes.ChanThemeColorId
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPostBuilder
import com.github.k1rakishou.model.data.post.ChanPostImageBuilder
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.regex.Matcher
import java.util.regex.Pattern

open class CommentParser : ICommentParser, HasQuotePatterns {
  private val rules = mutableMapWithCap<String, MutableList<StyleRule>>(initialCapacity = 16)

  private val defaultQuoteRegex: Pattern = Pattern.compile("//boards\\.4chan.*?\\.org/(.*?)/thread/(\\d*?)#p(\\d*)")
  private val deadQuotePattern: Pattern = Pattern.compile(">>(\\d+)")
  private val fullQuotePattern: Pattern = Pattern.compile("/(\\w+)/\\w+/(\\d+)#p(\\d+)")
  private val quotePattern: Pattern = Pattern.compile("#p(\\d+)")
  private val boardLinkPattern: Pattern = Pattern.compile("//boards\\.4chan.*?\\.org/(.*?)/")
  private val boardLinkPattern8Chan: Pattern = Pattern.compile("/(.*?)/index.html")
  private val boardSearchPattern: Pattern = Pattern.compile("//boards\\.4chan.*?\\.org/(.*?)/catalog#s=(.*)")
  private val colorPattern: Pattern = Pattern.compile("color:#?(\\w+)")
  private val colorRgbFgBgPattern: Pattern =
    Pattern.compile("color:rgb\\((\\d+),(\\d+),(\\d+)\\)\\;background\\-color\\:rgb\\((\\d+),(\\d+),(\\d+)\\)")

  init {
    // Required tags.
    addRule(StyleRule.tagRule("p"))
    addRule(StyleRule.tagRule("div"))
    addRule(StyleRule.tagRule("br").just("\n"))
  }

  fun addDefaultRules(): CommentParser {
    val codeTagFontSize = sp(ChanSettings.codeTagFontSizePx())
    val sjisTagFontSize = sp(ChanSettings.sjisTagFontSizePx())

    addRule(
      StyleRule.tagRule("a")
        .action { callback, post, text, anchorTag -> handleAnchor(callback, post, text, anchorTag) }
    )
    addRule(
      StyleRule.tagRule("iframe")
        .action { _, post, _, anchorTag -> handleIframe(post, anchorTag) }
    )
    addRule(
      StyleRule.tagRule("img")
        .action { _, post, _, imgTag ->
          handleImg(post, imgTag)
          null
        }
    )
    addRule(
      StyleRule.tagRule("table")
        .action { _, _, _, tableTag -> handleTable(tableTag) }
    )
    addRule(
      StyleRule.tagRule("span")
        .withCssClass("deadlink")
        .action { callback, post, text, _ -> handleDeadlink(callback, post, text) }
    )
    addRule(
      StyleRule.tagRuleWithAttr("*", "style")
        .action { _, _, text, tag -> handleAnyTagWithStyleAttr(text, tag) }
    )

    addRule(StyleRule.tagRule("s").link(PostLinkable.Type.SPOILER))
    addRule(StyleRule.tagRule("b").bold())
    addRule(StyleRule.tagRule("i").italic())
    addRule(StyleRule.tagRule("em").italic())
    addRule(StyleRule.tagRule("u").underline())
    addRule(StyleRule.tagRule("span").withCssClass("s").strikeThrough())
    addRule(StyleRule.tagRule("span").withCssClass("u").underline())

    addRule(StyleRule.tagRule("sup").superscript())
    addRule(StyleRule.tagRule("sub").subscript())
    addRule(
      StyleRule.tagRule("span")
        .withCssClass("o")
        .withPriority(StyleRule.Priority.BeforeWildcardRules)
        .overline()
    )

    addRule(
      StyleRule.tagRule("pre")
        .withCssClass("prettyprint")
        .monospace()
        .size(codeTagFontSize)
        .backgroundColorId(ChanThemeColorId.BackColorSecondary)
        .foregroundColorId(ChanThemeColorId.TextColorPrimary)
    )

    addRule(
      StyleRule.tagRule("span")
        .withCssClass("sjis")
        .size(sjisTagFontSize)
        .foregroundColorId(ChanThemeColorId.TextColorPrimary)
    )

    addRule(
      StyleRule.tagRule("span")
        .withCssClass("spoiler")
        .link(PostLinkable.Type.SPOILER)
    )

    addRule(StyleRule.tagRule("span").withCssClass("abbr").nullify())
    addRule(StyleRule.tagRule("span").foregroundColorId(ChanThemeColorId.PostInlineQuoteColor))
    addRule(StyleRule.tagRule("span").withoutAnyOfCssClass("quote").linkify())

    addRule(StyleRule.tagRule("strong").bold())
    addRule(StyleRule.tagRule("strong-red;").bold().foregroundColorId(ChanThemeColorId.AccentColor))

    return this
  }

  fun addRule(rule: StyleRule) {
    var list = rules.get(rule.tag())
    if (list == null) {
      list = ArrayList<StyleRule>(3)
      rules[rule.tag()] = list
    }

    list.add(rule)
  }

  fun addOrReplaceRule(rule: StyleRule) {
    var list = rules[rule.tag()]
    if (list == null) {
      list = ArrayList<StyleRule>(3)
      rules[rule.tag()] = list
    }

    for (i in list.indices) {
      val oldRule = list[i]
      if (oldRule.areTheSame(rule)) {
        list[i] = rule
        return
      }
    }

    list.add(rule)
  }

  override fun getQuotePattern(): Pattern {
    return quotePattern
  }

  override fun getFullQuotePattern(): Pattern {
    return fullQuotePattern
  }

  open fun preprocessTag(node: HtmlNode.Tag): HtmlTag {
    return node.htmlTag
  }

  fun handleTag(
    callback: PostParser.Callback?,
    post: ChanPostBuilder?,
    tag: String?,
    text: CharSequence,
    htmlTag: HtmlTag
  ): CharSequence? {
    val forceHttpsScheme = ChanSettings.forceHttpsUrlScheme.get()
    val normalRules = this.rules.get(tag)

    // Execute rules which must be executed before the wildcard rules
    if (normalRules != null) {
      for (i in 0..1) {
        val highPriority = i == 0

        for (rule in normalRules) {
          if (rule.rulePriority() != StyleRule.Priority.BeforeWildcardRules) {
            continue
          }

          if (rule.highPriority() == highPriority && rule.applies(htmlTag)) {
            return rule.apply(StyleRulesParams(text, htmlTag, callback, post, forceHttpsScheme))
          }
        }
      }
    }

    // Execute wildcard rules
    val wildcardRules = this.rules.get("*")
    if (wildcardRules != null) {
      outer@ for (i in 0..1) {
        val highPriority = i == 0

        for (rule in wildcardRules) {
          if (rule.highPriority() == highPriority && rule.applies(htmlTag, true)) {
            val result = rule.apply(StyleRulesParams(text, htmlTag, callback, post, forceHttpsScheme))
            if (!TextUtils.isEmpty(result)) {
              return result
            }

            break@outer
          }
        }
      }
    }

    // Execute the rest of the rules
    if (normalRules != null) {
      for (i in 0..1) {
        val highPriority = i == 0

        for (rule in normalRules) {
          if (rule.rulePriority() != StyleRule.Priority.Normal) {
            continue
          }

          if (rule.highPriority() == highPriority && rule.applies(htmlTag)) {
            return rule.apply(StyleRulesParams(text, htmlTag, callback, post, forceHttpsScheme))
          }
        }
      }
    }

    // Unknown tag, return the text;
    return text
  }

  // <span style="color:#0893e1">Test</span>
  // <span style="color:red">Test</span>
  // <span style=\"color:rgb(77,100,77);background-color:rgb(241,140,31)\"
  private fun handleAnyTagWithStyleAttr(
    text: CharSequence?,
    tag: HtmlTag
  ): CharSequence? {
    var style = tag.attrOrNull("style")
    if (style == null || TextUtils.isEmpty(style)) {
      return text
    }

    style = style.replace(" ", "")

    if (style.contains("rgb")) {
      val matcher = colorRgbFgBgPattern.matcher(style)

      if (!matcher.find()) {
        return text
      }

      val foregroundColor = colorFromArgb(
        alpha = 255,
        r = matcher.groupOrNull(1),
        g = matcher.groupOrNull(2),
        b = matcher.groupOrNull(3)
      )

      val backgroundColor = colorFromArgb(
        alpha = 255,
        r = matcher.groupOrNull(4),
        g = matcher.groupOrNull(5),
        b = matcher.groupOrNull(6)
      )

      var foregroundColorSpanHashed: ForegroundColorSpanHashed? = null
      if (foregroundColor != null) {
        foregroundColorSpanHashed = ForegroundColorSpanHashed(foregroundColor)
      }

      var backgroundColorSpanHashed: BackgroundColorSpanHashed? = null
      if (backgroundColor != null) {
        backgroundColorSpanHashed = BackgroundColorSpanHashed(backgroundColor)
      }

      return span(
        text,
        foregroundColorSpanHashed,
        backgroundColorSpanHashed,
        StyleSpan(Typeface.BOLD)
      )
    }

    val matcher = colorPattern.matcher(style)
    if (!matcher.find()) {
      return text
    }

    val colorRaw = matcher.group(1)
    if (colorRaw != null) {
      var colorByName = getColorValueByHtmlColorName(colorRaw)

      if (colorByName == null) {
        colorByName = toIntOrNull(colorRaw)
      }

      if (colorByName != null) {
        return span(
          text,
          ForegroundColorSpanHashed(ColorUtils.setAlphaComponent(colorByName, 255)),
          StyleSpan(Typeface.BOLD)
        )
      }
    }

    return text
  }

  private fun handleDeadlink(
    callback: PostParser.Callback,
    post: ChanPostBuilder,
    text: CharSequence?,
  ): CharSequence? {
    if (text.isNullOrBlank()) {
      return null
    }

    val matcher = deadQuotePattern.matcher(text)
    if (!matcher.matches()) {
      // Something unknown
      return text
    }

    val postId: Long
    val postSubId = 0L

    try {
      postId = matcher.group(1)!!.toLong()
    } catch (ignored: Throwable) {
      // Some bugged value. May happen on 4chan.
      return text
    }

    val boardDescriptor = post.boardDescriptor
      ?: return null

    val type: PostLinkable.Type?
    val value: PostLinkable.Value?

    val postDescriptor = PostDescriptor.create(
      boardDescriptor = boardDescriptor,
      threadNo = post.postDescriptor().threadDescriptor().threadNo,
      postNo = postId,
      postSubNo = postSubId
    )

    if (callback.isInternal(postDescriptor)) {
      // Link to post in same thread with post number (>>post)
      type = PostLinkable.Type.QUOTE
      post.addReplyTo(postId, postSubId)

      value = PostIdValue(postId, postSubId)
    } else {
      // Link to post not in same thread in this case it means that the post is dead.
      type = PostLinkable.Type.DEAD

      value = ThreadOrPostLink(
        board = boardDescriptor.boardCode,
        threadId = post.opId(),
        postId = postId,
        postSubId = 0L
      )
    }

    val link = PostLinkable.Link(
      type = type,
      key = TextUtils.concat(text, CommentParserConstants.DEAD_REPLY_SUFFIX),
      linkValue = value
    )

    appendSuffixes(
      callback = callback,
      post = post,
      handlerLink = link,
      postNo = postId,
      postSubNo = postSubId
    )

    val res = SpannableString(link.key)
    val pl = PostLinkable(
      key = link.key,
      linkableValue = link.linkValue,
      type = link.type
    )
    res.setSpan(pl, 0, res.length, (250 shl Spanned.SPAN_PRIORITY_SHIFT) and Spanned.SPAN_PRIORITY)

    post.addLinkable(pl)

    return res
  }

  private fun handleImg(
    post: ChanPostBuilder,
    imgTag: HtmlTag
  ) {
    var srcValue = imgTag.attrUnescapedOrNull("src")
    if (srcValue.isNullOrEmpty()) {
      return
    }

    val parentNode = imgTag.parentNode
    if (parentNode is HtmlNode.Tag) {
      val htmlTag = parentNode.htmlTag
      val tagName = htmlTag.tagName

      if ("a" == tagName) {
        return
      }
    }

    // Local images (located on 4chan) may be displayed without the "http(s)" scheme.
    // Like "//s.4cdn.org/image/temp/danger.gif"
    if (srcValue.startsWith("//")) {
      srcValue = "https:$srcValue"
    }

    val httpUrl = srcValue.toHttpUrlOrNull()
    if (httpUrl == null) {
      return
    }

    val serverFileName = System.currentTimeMillis().toString()
    var filename = imgTag.attrUnescapedOrNull("alt")
    val extension = extractFileNameExtension(srcValue)

    if (TextUtils.isEmpty(filename)) {
      filename = serverFileName
    }

    post.postImages.add(
      ChanPostImageBuilder(post.postDescriptor())
        .thumbnailUrl(AppConstants.INLINED_IMAGE_THUMBNAIL_URL)
        .imageUrl(httpUrl)
        .serverFilename(serverFileName)
        .filename(filename)
        .extension(extension)
        .inlined()
        .build()
    )
  }

  private fun handleIframe(
    post: ChanPostBuilder,
    anchorTag: HtmlTag
  ): CharSequence {
    val srcValue = anchorTag.attrUnescapedOrNull("src")
    if (srcValue.isNullOrEmpty()) {
      return ""
    }

    val spannableStringBuilder = SpannableStringBuilder()
    spannableStringBuilder.append(IFRAME_CONTENT_PREFIX)
    spannableStringBuilder.append("\n")
    spannableStringBuilder.append(srcValue)

    val postLinkable = PostLinkable(
      key = srcValue,
      linkableValue = PostLinkable.Value.StringValue(srcValue),
      type = PostLinkable.Type.LINK
    )

    spannableStringBuilder.setSpan(
      postLinkable,
      0,
      spannableStringBuilder.length,
      (250 shl Spanned.SPAN_PRIORITY_SHIFT) and Spanned.SPAN_PRIORITY
    )

    post.addLinkable(postLinkable)

    return spannableStringBuilder
  }

  private fun handleAnchor(
    callback: PostParser.Callback,
    post: ChanPostBuilder,
    text: CharSequence?,
    anchorTag: HtmlTag
  ): CharSequence? {
    if (text == null) {
      return null
    }

    val handlerLink = matchAnchor(post, text, anchorTag, callback)
    val spannableStringBuilder = SpannableStringBuilder()
    addReply(callback, post, handlerLink, spannableStringBuilder)

    return spannableStringBuilder.ifEmpty { null }
  }

  private fun addReply(
    callback: PostParser.Callback,
    post: ChanPostBuilder,
    handlerLink: PostLinkable.Link,
    spannableStringBuilder: SpannableStringBuilder
  ) {
    if (isPostLinkableAlreadyAdded(SpannableString(handlerLink.key), handlerLink.linkValue)) {
      // Fix for some sites (like 2ch.hk and some archives too) having the same link spans
      // encountered twice (This breaks video title and duration spans for youtube links
      // since we process the same spans twice)
      return
    }

    if (handlerLink.type == PostLinkable.Type.THREAD) {
      handlerLink.key = appendExternalThreadSuffixIfNeeded(handlerLink.key)!!
    }

    if (handlerLink.type == PostLinkable.Type.QUOTE
      || handlerLink.type == PostLinkable.Type.DEAD
      || handlerLink.type == PostLinkable.Type.QUOTE_TO_HIDDEN_OR_REMOVED_POST
    ) {
      val value = handlerLink.linkValue.extractPostIdOrNull()
      if (value != null) {
        val postNo = value.postNo
        val postSubNo = value.postSubNo

        post.addReplyTo(postNo, postSubNo)
        appendSuffixes(
          callback = callback,
          post = post,
          handlerLink = handlerLink,
          postNo = postNo,
          postSubNo = postSubNo
        )
      }
    }

    val res = SpannableString(handlerLink.key)

    val pl = PostLinkable(
      key = handlerLink.key,
      linkableValue = handlerLink.linkValue,
      type = handlerLink.type
    )
    res.setSpan(pl, 0, res.length, (250 shl Spanned.SPAN_PRIORITY_SHIFT) and Spanned.SPAN_PRIORITY)
    post.addLinkable(pl)

    spannableStringBuilder.append(res)
  }

  private fun appendExternalThreadSuffixIfNeeded(handlerLinkKey: CharSequence?): CharSequence? {
    if (TextUtils.isEmpty(handlerLinkKey)) {
      return handlerLinkKey
    }

    val lastChar = handlerLinkKey!!.get(handlerLinkKey.length - 1)
    if (lastChar == CommentParserConstants.ARROW_TO_THE_RIGHT) {
      return handlerLinkKey
    }

    return TextUtils.concat(
      handlerLinkKey,
      CommentParserConstants.EXTERNAL_THREAD_LINK_SUFFIX
    )
  }

  protected open fun appendSuffixes(
    callback: PostParser.Callback,
    post: ChanPostBuilder,
    handlerLink: PostLinkable.Link,
    postNo: Long,
    postSubNo: Long?
  ) {
    // Append (OP) when it's a reply to OP
    if (postNo == post.opId()) {
      handlerLink.key = TextUtils.concat(
        handlerLink.key,
        CommentParserConstants.OP_REPLY_SUFFIX
      )
    }

    // Append (You) when it's a reply to a saved reply, (Me) if it's a self reply
    if (callback.isSaved(post.postDescriptor())) {
      if (post.isSavedReply) {
        handlerLink.key = TextUtils.concat(
          handlerLink.key,
          CommentParserConstants.SAVED_REPLY_SELF_SUFFIX
        )
      } else {
        handlerLink.key = TextUtils.concat(
          handlerLink.key,
          CommentParserConstants.SAVED_REPLY_OTHER_SUFFIX
        )
      }
    }

    val hiddenOrRemoved = callback.isHiddenOrRemoved(post.postDescriptor())
    if (hiddenOrRemoved != PostParser.NORMAL_POST) {
      val suffix: String?

      if (hiddenOrRemoved == PostParser.HIDDEN_POST) {
        suffix = CommentParserConstants.HIDDEN_POST_SUFFIX
      } else {
        suffix = CommentParserConstants.REMOVED_POST_SUFFIX
      }

      handlerLink.key = TextUtils.concat(handlerLink.key, suffix)
    }
  }

  private fun isPostLinkableAlreadyAdded(res: SpannableString, linkValue: PostLinkable.Value): Boolean {
    val alreadySetPostLinkables = res.getSpans<PostLinkable>(0, res.length, PostLinkable::class.java)
    if (alreadySetPostLinkables.size == 0) {
      return false
    }

    for (postLinkable in alreadySetPostLinkables) {
      if (linkValue.equals(postLinkable.linkableValue)) {
        return true
      }
    }

    return false
  }

  fun handleTable(
    tableTag: HtmlTag
  ): CharSequence {
    val parts = ArrayList<CharSequence>()
    val tableRows = tableTag.getTagsByName("tr")

    for (i in tableRows.indices) {
      val tableRow = tableRows[i]
      if (tableRow.text().isEmpty()) {
        continue
      }

      val tableDatas = tableRow.getTagsByName("td")

      for (j in tableDatas.indices) {
        val tableData = tableDatas.get(j)
        val tableDataPart = SpannableString(tableData.text())

        if (tableData.getTagsByName("b").isNotEmpty()) {
          tableDataPart.setSpan(
            StyleSpan(Typeface.BOLD),
            0,
            tableDataPart.length,
            0
          )

          tableDataPart.setSpan(
            UnderlineSpan(),
            0,
            tableDataPart.length,
            0
          )
        }

        parts.add(tableDataPart)

        if (j < tableDatas.size - 1) {
          parts.add(": ")
        }
      }

      if (i < tableRows.size - 1) {
        parts.add("\n")
      }
    }

    // Overrides the text (possibly) parsed by child nodes.
    return span(
      TextUtils.concat(*parts.toTypedArray<CharSequence?>()),
      ForegroundColorIdSpan(ChanThemeColorId.PostInlineQuoteColor),
      AbsoluteSizeSpanHashed(sp(12f))
    )
  }

  open fun matchAnchor(
    post: ChanPostBuilder,
    text: CharSequence,
    anchorTag: HtmlTag,
    callback: PostParser.Callback
  ): PostLinkable.Link {
    val href = extractQuote(anchorTag.attrUnescapedOrNull("href"))
    val externalMatcher = matchExternalQuote(href)

    var type = PostLinkable.Type.LINK
    var value: PostLinkable.Value = PostLinkable.Value.StringValue(href)

    if (externalMatcher.find()) {
      val board = externalMatcher.group(1)

      val threadId: Long
      try {
        threadId = externalMatcher.group(2)!!.toLong()
      } catch (ignored: Throwable) {
        return PostLinkable.Link(type, text, value)
      }

      val postId: Long
      val postSubId = 0L

      try {
        postId = externalMatcher.group(3)!!.toLong()
      } catch (ignored: Throwable) {
        return PostLinkable.Link(type, text, value)
      }

      val postDescriptor = PostDescriptor.create(
        boardDescriptor = post.boardDescriptor!!,
        threadNo = threadId,
        postNo = postId,
        postSubNo = postSubId
      )

      val isInternalQuote = board == post.boardDescriptor!!.boardCode
        && callback.isInternal(postDescriptor)
        && !callback.isParsingCatalogPosts()

      if (isInternalQuote) {
        // link to post in same thread with post number (>>post)
        type = PostLinkable.Type.QUOTE
        value = PostIdValue(postId, postSubId)
      } else {
        // link to post not in same thread with post number (>>post or >>>/board/post)
        type = PostLinkable.Type.THREAD
        value = ThreadOrPostLink(board!!, threadId, postId, 0L)
      }
    } else {
      val quoteMatcher = matchInternalQuote(href)
      if (quoteMatcher.matches()) {
        val postId: Long
        val postSubId = 0L
        try {
          postId = quoteMatcher.group(1)!!.toLong()
        } catch (ignored: Throwable) {
          return PostLinkable.Link(type, text, value)
        }

        val postDescriptor = PostDescriptor.create(
          boardDescriptor = post.boardDescriptor!!,
          threadNo = post.postDescriptor().threadDescriptor().threadNo,
          postNo = postId,
          postSubNo = postSubId
        )

        if (callback.isInternal(postDescriptor)) {
          val hiddenOrRemoved = callback.isHiddenOrRemoved(postDescriptor)

          type = when (hiddenOrRemoved) {
            PostParser.HIDDEN_POST,
            PostParser.REMOVED_POST -> {
              // Quote pointing to a (locally) hidden or removed post
              PostLinkable.Type.QUOTE_TO_HIDDEN_OR_REMOVED_POST
            }
            else -> {
              // Normal post quote
              PostLinkable.Type.QUOTE
            }
          }
        } else {
          // Most likely a quote to a deleted post (Or any other post that we don't have
          // in the cache).
          type = PostLinkable.Type.DEAD
        }

        value = PostIdValue(postId, postSubId)
      } else {
        val boardLinkMatcher = matchBoardLink(href)
        val boardSearchMatcher = matchBoardSearch(href)

        if (boardLinkMatcher.matches()) {
          // board link
          type = PostLinkable.Type.BOARD
          value = PostLinkable.Value.StringValue(boardLinkMatcher.group(1))
        } else if (boardSearchMatcher.matches()) {
          // search link
          val board = boardSearchMatcher.group(1)
          var search: String?

          try {
            search = URLDecoder.decode(boardSearchMatcher.group(2), StandardCharsets.US_ASCII.name())
          } catch (ignored: Throwable) {
            search = boardSearchMatcher.group(2)
          }

          type = PostLinkable.Type.SEARCH
          value = SearchLink(board!!, search)
        } else {
          // normal link
          type = PostLinkable.Type.LINK
          value = PostLinkable.Value.StringValue(href)
        }
      }
    }

    return PostLinkable.Link(type, text, value)
  }

  protected fun matchBoardSearch(href: String): Matcher {
    return boardSearchPattern.matcher(href)
  }

  protected fun matchBoardLink(href: String): Matcher {
    val chan4BoardLinkMatcher = boardLinkPattern.matcher(href)
    if (chan4BoardLinkMatcher.matches()) {
      return chan4BoardLinkMatcher
    }

    return boardLinkPattern8Chan.matcher(href)
  }

  private fun matchInternalQuote(href: String): Matcher {
    return getQuotePattern().matcher(href)
  }

  private fun matchExternalQuote(href: String): Matcher {
    return getFullQuotePattern().matcher(href)
  }

  protected fun extractQuote(href: String?): String {
    if (href.isNullOrEmpty()) {
      return ""
    }

    if (defaultQuoteRegex.matcher(href).matches()) {
      // gets us something like /board/ or /thread/postno#quoteno
      // hacky fix for 4chan having two domains but the same API
      return href.substring(2).substring(href.indexOf('/'))
    }

    return href
  }

  fun span(text: CharSequence?, vararg additionalSpans: Any?): SpannableString {
    val result = SpannableString.valueOf(text)
    val l = result.length

    if (additionalSpans.isNotEmpty()) {
      for (additionalSpan in additionalSpans) {
        if (additionalSpan != null) {
          result.setSpan(additionalSpan, 0, l, 0)
        }
      }
    }

    return result
  }

  companion object {
    private const val IFRAME_CONTENT_PREFIX = "[Iframe content]"
  }
}

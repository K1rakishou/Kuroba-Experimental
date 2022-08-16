package com.github.k1rakishou.chan.core.site.sites.chan4

import android.text.SpannableStringBuilder
import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.core.site.sites.search.Chan4SearchParams
import com.github.k1rakishou.chan.core.site.sites.search.PageCursor
import com.github.k1rakishou.chan.core.site.sites.search.SearchEntry
import com.github.k1rakishou.chan.core.site.sites.search.SearchEntryPost
import com.github.k1rakishou.chan.core.site.sites.search.SearchError
import com.github.k1rakishou.chan.core.site.sites.search.SearchResult
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.BadStatusResponseException
import com.github.k1rakishou.common.EmptyBodyResponseException
import com.github.k1rakishou.common.flatMapNotNull
import com.github.k1rakishou.common.groupOrNull
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.joda.time.DateTime
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.FormElement
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

class Chan4SearchRequest(
  private val request: Request,
  private val proxiedOkHttpClient: Lazy<RealProxiedOkHttpClient>,
  private val searchParams: Chan4SearchParams
) {

  suspend fun execute(): SearchResult {
    return withContext(Dispatchers.IO) {
      try {
        val response = proxiedOkHttpClient.get().okHttpClient().suspendCall(request)

        if (!response.isSuccessful) {
          throw BadStatusResponseException(response.code)
        }

        if (response.body == null) {
          throw EmptyBodyResponseException()
        }

        return@withContext response.body!!.use { body ->
          return@use body.byteStream().use { inputStream ->
            val url = request.url.toString()

            val htmlDocument = Jsoup.parse(inputStream, StandardCharsets.UTF_8.name(), url)
            return@use readHtml(url, htmlDocument)
          }
        }
      } catch (error: Throwable) {
        return@withContext SearchResult.Failure(SearchError.UnknownError(error))
      }
    }
  }

  private fun readHtml(url: String, document: Document): SearchResult {
    BackgroundUtils.ensureBackgroundThread()

    val searchEntries = mutableListOf<SearchEntry>()
    var pageCursor: PageCursor = PageCursor.End
    var totalFoundEntries: Int? = null

    val error = iteratePostBuilders(document, { postBuilders ->
      val threadDescriptor = postBuilders
        .firstOrNull { postBuilder -> postBuilder.isOp == true }
        ?.threadDescriptor()

      if (threadDescriptor == null) {
        return@iteratePostBuilders
      }

      searchEntries += SearchEntry(
        postBuilders.map { postBuilder -> postBuilder.toSearchEntryPost() }
      )
    }, { page ->
      pageCursor = page
    }, { totalFound ->
      totalFoundEntries = totalFound
    })

    if (error != null) {
      return error
    }

    return SearchResult.Success(searchParams, searchEntries, pageCursor, totalFoundEntries)
  }

  private fun iteratePostBuilders(
    document: Document,
    postBuildersFunc: (List<SearchEntryPostBuilder>) -> Unit,
    nextPageFunc: (PageCursor) -> Unit,
    totalEntriesFound: (Int?) -> Unit
  ): SearchResult.Failure? {
    for (topChildNode in document.childNodes()) {
      if (topChildNode !is Element || topChildNode.tagName() != HTML_TAG) {
        continue
      }

      for (htmlTagChild in topChildNode.childNodes()) {
        if (htmlTagChild !is Element || htmlTagChild.tagName() != BODY_TAG) {
          continue
        }

        for (bodyTagChild in htmlTagChild.childNodes()) {
          if (bodyTagChild is FormElement) {
            if (bodyTagChild.tagName() == FORM_TAG) {
              val error = parseSearchData(bodyTagChild, postBuildersFunc)
              if (error != null) {
                return error
              }

              continue
            }
          }

          if (bodyTagChild is Element) {
            if (bodyTagChild.tagName() == DIV_TAG && bodyTagChild.attr(CLASS_ATTR) == BOARD_BANNER_ATTR_VAL) {
              parseTotalFoundEntries(bodyTagChild, totalEntriesFound)
              continue
            }

            if (bodyTagChild.tagName() == DIV_TAG && bodyTagChild.attr(CLASS_ATTR) == PAGE_LIST_MOBILE_ATTR_VAL) {
              parsePageData(bodyTagChild, nextPageFunc)
              continue
            }
          }
        }
      }
    }

    return null
  }

  private fun parseTotalFoundEntries(bodyTagChild: Element, totalEntriesFound: (Int?) -> Unit) {
    val titleNode = bodyTagChild.childNodes()
      .firstOrNull { node -> node is Element && node.attr(CLASS_ATTR) == BOARD_TITLE_ATTR_VAL }
      ?.childNodes()
      ?.firstOrNull { node -> node is TextNode }

    if (titleNode !is TextNode) {
      Logger.e(TAG, "parseTotalFoundEntries() Failed to find title node: \"${bodyTagChild.text()}\"")
      totalEntriesFound.invoke(null)
      return
    }

    val matcher = TOTAL_ENTRIES_FOUND_PATTERN.matcher(titleNode.text())
    if (!matcher.find()) {
      Logger.e(TAG, "parseTotalFoundEntries() Failed to match TOTAL_ENTRIES_FOUND_PATTERN: \"${titleNode.text()}\"")
      totalEntriesFound.invoke(null)
      return
    }

    val entriesFound = matcher.groupOrNull(1)?.toIntOrNull()
    if (entriesFound == null) {
      Logger.e(TAG, "parseTotalFoundEntries() Failed to convert found entries count into an Int: \"${titleNode.text()}\"")
      totalEntriesFound.invoke(null)
      return
    }

    totalEntriesFound.invoke(entriesFound)
  }

  private fun parsePageData(bodyTagChild: Element, nextPageFunc: (PageCursor) -> Unit) {
    val allPages = extractPagesRaw(bodyTagChild)
      .mapNotNull { pageRaw ->
        val matcher = PAGE_VALUE_PATTERN.matcher(pageRaw)
        if (!matcher.find()) {
          return@mapNotNull null
        }

        return@mapNotNull matcher.groupOrNull(1)?.toInt()
      }

    if (allPages.isEmpty()) {
      Logger.e(TAG, "Failed to find pages: \"${bodyTagChild.text()}\"")
      nextPageFunc(PageCursor.End)
      return
    }

    val currentPageIndex = if (searchParams.page == null) {
      0
    } else {
      allPages.indexOfFirst { page -> page == searchParams.page }
    }

    if (currentPageIndex >= 0) {
      val nextPageValue = allPages.getOrNull(currentPageIndex + 1)
      if (nextPageValue != null) {
        nextPageFunc(PageCursor.Page(nextPageValue))
        return
      }
    }

    Logger.e(TAG, "Failed to find current page: allPages=${allPages}, currentPage=${searchParams.page}")
    nextPageFunc(PageCursor.End)
  }

  private fun extractPagesRaw(bodyTagChild: Element): List<String> {
    return bodyTagChild.childNodes()
      .flatMapNotNull { pageNode ->
        if (pageNode !is Element) {
          return@flatMapNotNull null
        }

        if (pageNode.attr(CLASS_ATTR) != PAGES_ATTR_VAL) {
          return@flatMapNotNull null
        }

        pageNode.childNodes().flatMapNotNull { spanNode ->
          if (spanNode !is Element) {
            return@flatMapNotNull null
          }

          if (spanNode.tagName() != SPAN_ATTR) {
            return@flatMapNotNull null
          }

          return@flatMapNotNull spanNode.childNodes().flatMapNotNull { strongNode ->
            if (strongNode !is Element) {
              return@flatMapNotNull null
            }

            if (strongNode.tagName() != TAG_STRONG) {
              return@flatMapNotNull null
            }

            return@flatMapNotNull strongNode.childNodes().mapNotNull { aNode ->
              if (aNode !is Element) {
                return@mapNotNull null
              }

              if (aNode.tagName() != TAG_A) {
                return@mapNotNull null
              }

              return@mapNotNull aNode.attr(HREF_ATTR)
            }
          }
        }
      }
  }

  private fun parseSearchData(
    bodyTagChild: FormElement,
    postBuildersFunc: (List<SearchEntryPostBuilder>) -> Unit
  ): SearchResult.Failure? {
    for (formTagChild in bodyTagChild.childNodes()) {
      if (formTagChild !is Element) {
        continue
      }

      if (formTagChild.attr(CLASS_ATTR) != BOARD_ATTR_VAL) {
        continue
      }

      for (boardTagChild in formTagChild.childNodes()) {
        if (boardTagChild !is Element) {
          continue
        }

        if (boardTagChild.attr(CLASS_ATTR) != THREAD_ATTR_VAL) {
          continue
        }

        val postBuilders = mutableListOf<SearchEntryPostBuilder>()

        for (threadTagChild in boardTagChild.childNodes()) {
          if (threadTagChild !is Element) {
            continue
          }

          val classAttr = threadTagChild.attr(CLASS_ATTR)
          if (classAttr == POST_CONTAINER_OP_ATTR_VAL) {
            val searchEntryPostBuilder = SearchEntryPostBuilder()
            searchEntryPostBuilder.isOp = true

            val error = parsePost(true, threadTagChild, searchEntryPostBuilder)
            if (error != null) {
              return error
            }

            postBuilders += searchEntryPostBuilder
            continue
          }

          if (classAttr == POST_CONTAINER_REPLY_ATTR_VAL) {
            val searchEntryPostBuilder = SearchEntryPostBuilder()
            searchEntryPostBuilder.isOp = false

            val error = parsePost(false, threadTagChild, searchEntryPostBuilder)
            if (error != null) {
              return error
            }

            postBuilders += searchEntryPostBuilder
          }
        }

        postBuildersFunc(postBuilders)
      }
    }

    return null
  }

  private fun parsePost(
    isOp: Boolean,
    threadTagChild: Node,
    searchEntryPostBuilder: SearchEntryPostBuilder
  ): SearchResult.Failure? {
    val postClass = if (isOp) {
      POST_OP_ATTR_VAL
    } else {
      POST_REPLY_ATTR_VAL
    }

    val opPostNode = threadTagChild.childNodes()
      .firstOrNull { node -> node is Element && node.attr(CLASS_ATTR) == postClass }

    if (opPostNode == null) {
      val error = SearchError.ParsingError("Couldn't find node with class \"$postClass\"")
      return SearchResult.Failure(error)
    }

    for (childNode in opPostNode.childNodes()) {
      if (childNode !is Element) {
        continue
      }

      val classAttr = childNode.attr(CLASS_ATTR)
      if (classAttr == POST_INFO_M_ATTR_VAL) {
        parsePostInfo(isOp, childNode, searchEntryPostBuilder)
        continue
      }

      if (classAttr == FILE_ATTR_VAL) {
        parseFileInfo(childNode, searchEntryPostBuilder)
        continue
      }

      if (childNode.tagName() == TAG_BLOCK_QUOTE && childNode.attr(CLASS_ATTR) == POST_MESSAGE_ATTR_VAL) {
        val resultString = buildString {
          childNode.childNodes()
            .forEach { commentNode -> append(commentNode.toString()) }
        }

        searchEntryPostBuilder.commentRaw = resultString
        continue
      }
    }

    if (searchEntryPostBuilder.hasMissingInfo()) {
      val error = SearchError.ParsingError("Failed to parse OP, builder=${searchEntryPostBuilder}")
      return SearchResult.Failure(error)
    }

    return null
  }

  private fun parseFileInfo(fileNode: Element, searchEntryPostBuilder: SearchEntryPostBuilder) {
    val fileThumbNode = fileNode.childNodes()
      .firstOrNull { node -> node is Element && node.attr(CLASS_ATTR) == FILE_THUMB_ATTR_VAL } as? Element

    if (fileThumbNode == null) {
      Logger.e(TAG, "parseFileInfo() fileThumbNode is null")
      return
    }

    val imgNode = fileThumbNode.childNodes()
      .firstOrNull { node -> node is Element && node.tagName() == TAG_IMG } as? Element

    if (imgNode == null) {
      Logger.e(TAG, "parseFileInfo() imgNode is null: \"${fileThumbNode.text()}\"")
      return
    }

    val thumbnailUrl = imgNode.attr(SRC_ATTR)
    if (thumbnailUrl.isNullOrBlank()) {
      Logger.e(TAG, "parseFileInfo() thumbnailUrl is null or blank: \"$thumbnailUrl\"")
      return
    }

    val fullThumbnailUrl = "https:$thumbnailUrl".toHttpUrlOrNull()
    if (fullThumbnailUrl == null) {
      Logger.e(TAG, "parseFileInfo() fullThumbnailUrl is null: \"$fullThumbnailUrl\"")
      return
    }

    if (searchEntryPostBuilder.postImageUrlRawList.contains(fullThumbnailUrl)) {
      return
    }

    searchEntryPostBuilder.postImageUrlRawList.add(fullThumbnailUrl)
  }

  private fun parsePostInfo(isOp: Boolean, postInfoNode: Element, searchEntryPostBuilder: SearchEntryPostBuilder) {
    for (postInfoChildNode in postInfoNode.childNodes()) {
      if (searchEntryPostBuilder.hasPostDescriptor()
        && searchEntryPostBuilder.hasDateTime()
        && searchEntryPostBuilder.hasNameAndSubject()) {
        return
      }

      if (postInfoChildNode !is Element) {
        continue
      }

      val classAttr = postInfoChildNode.attr(CLASS_ATTR)
      if (classAttr == NAME_BLOCK_ATTR_VAL) {
        parseNameBlock(postInfoChildNode, searchEntryPostBuilder)
        continue
      }

      if (classAttr == DATE_TIME_POST_NUM_ATTR_VAL) {
        parseDateTime(isOp, postInfoChildNode, searchEntryPostBuilder)
        continue
      }
    }
  }

  private fun parseNameBlock(postInfoChildNode: Element, searchEntryPostBuilder: SearchEntryPostBuilder) {
    for (childNode in postInfoChildNode.childNodes()) {
      if (searchEntryPostBuilder.hasNameAndSubject()) {
        return
      }

      if (childNode !is Element || childNode.tagName() != SPAN_ATTR) {
        continue
      }

      val classAttr = childNode.attr(CLASS_ATTR)
      if (classAttr == NAME_ATTR) {
        val nameValue = (childNode.childNodes().firstOrNull { it is TextNode } as? TextNode)?.text()
        searchEntryPostBuilder.name = nameValue
        continue
      }

      if (classAttr == SUBJECT_ATTR_VAL) {
        val subjectValue = (childNode.childNodes().firstOrNull { it is TextNode } as? TextNode)?.text()
        if (!subjectValue.isNullOrBlank()) {
          searchEntryPostBuilder.subject = subjectValue
        }

        continue
      }
    }
  }

  private fun parseDateTime(isOp: Boolean, childNode: Element, searchEntryPostBuilder: SearchEntryPostBuilder) {
    if (searchEntryPostBuilder.hasDateTime() && searchEntryPostBuilder.hasPostDescriptor()) {
      return
    }

    val dateRawValue = childNode.attr(DATA_UTC_ATTR).toLongOrNull()
    if (dateRawValue != null) {
      searchEntryPostBuilder.dateTime = DateTime(dateRawValue * 1000L)
    }

    parsePostDescriptor(childNode, isOp, searchEntryPostBuilder)
  }

  private fun parsePostDescriptor(childNode: Element, isOp: Boolean, searchEntryPostBuilder: SearchEntryPostBuilder) {
    val hrefAttr = childNode.childNodes()
      .firstOrNull { node -> node is Element && node.tagName() == TAG_A }
      ?.let { node ->
        if (node !is Element) {
          return@let null
        }

        return@let node.attr(HREF_ATTR)
      }

    if (hrefAttr.isNullOrEmpty()) {
      Logger.e(TAG, "parsePostDescriptor() hrefAttr is null or empty")
      return
    }

    if (isOp) {
      val opDescriptorMatcher = OP_POST_DESCRIPTOR_PATTERN.matcher(hrefAttr)
      if (opDescriptorMatcher.matches()) {
        val boardCode = opDescriptorMatcher.groupOrNull(1)
        if (boardCode == null) {
          Logger.e(TAG, "parsePostDescriptor() failed to parse boardCode: \"$hrefAttr\"")
          return
        }

        val threadNo = opDescriptorMatcher.groupOrNull(2)?.toLongOrNull()
        if (threadNo == null) {
          Logger.e(TAG, "parsePostDescriptor() failed to parse threadNo: \"$hrefAttr\"")
          return
        }

        searchEntryPostBuilder.postDescriptor = PostDescriptor.create(
          SITE_DESCRIPTOR.siteName,
          boardCode,
          threadNo,
          threadNo
        )
      }
    } else {
      val postDescriptorMatcher = POST_DESCRIPTOR_PATTERN.matcher(hrefAttr)
      if (postDescriptorMatcher.matches()) {
        val boardCode = postDescriptorMatcher.groupOrNull(1)
        if (boardCode == null) {
          Logger.e(TAG, "parsePostDescriptor() failed to parse boardCode: \"$hrefAttr\"")
          return
        }

        val threadNo = postDescriptorMatcher.groupOrNull(2)?.toLongOrNull()
        if (threadNo == null) {
          Logger.e(TAG, "parsePostDescriptor() failed to parse threadNo: \"$hrefAttr\"")
          return
        }

        val postNo = postDescriptorMatcher.groupOrNull(3)?.toLongOrNull()
        if (postNo == null) {
          Logger.e(TAG, "parsePostDescriptor() failed to parse postNo: \"$hrefAttr\"")
          return
        }

        searchEntryPostBuilder.postDescriptor = PostDescriptor.create(
          SITE_DESCRIPTOR.siteName,
          boardCode,
          threadNo,
          postNo
        )
      }
    }
  }

  private class SearchEntryPostBuilder {
    var isOp: Boolean? = null
    var name: String? = null
    var subject: String? = null
    var postDescriptor: PostDescriptor? = null
    var dateTime: DateTime? = null
    val postImageUrlRawList = mutableListOf<HttpUrl>()
    var commentRaw: String? = null

    fun threadDescriptor(): ChanDescriptor.ThreadDescriptor {
      checkNotNull(isOp) { "isOp is null!" }
      checkNotNull(postDescriptor) { "postDescriptor is null!" }
      check(isOp!!) { "Must be OP!" }

      return postDescriptor!!.threadDescriptor()
    }

    fun hasMissingInfo(): Boolean {
      return isOp == null || postDescriptor == null || dateTime == null
    }

    fun hasPostDescriptor(): Boolean = postDescriptor != null
    fun hasDateTime(): Boolean = dateTime != null
    fun hasNameAndSubject(): Boolean = name != null && !subject.isNullOrBlank()

    fun toSearchEntryPost(): SearchEntryPost {
      if (hasMissingInfo()) {
        throw IllegalStateException("Some info is missing! isOp=$isOp, postDescriptor=$postDescriptor, " +
          "dateTime=$dateTime, commentRaw=$commentRaw")
      }

      return SearchEntryPost(
        isOp!!,
        name?.let { SpannableStringBuilder(it) },
        subject?.let { SpannableStringBuilder(it) },
        postDescriptor!!,
        dateTime!!,
        postImageUrlRawList,
        commentRaw?.let { SpannableStringBuilder(it) }
      )
    }

    override fun toString(): String {
      return "SearchEntryPostBuilder(isOp=$isOp, postDescriptor=$postDescriptor, dateTime=${dateTime?.millis}, " +
        "postImageUrlRawList=$postImageUrlRawList, commentRaw=$commentRaw)"
    }

  }

  companion object {
    private const val TAG = "Chan4SearchRequest"

    private const val CLASS_ATTR = "class"
    private const val DATA_UTC_ATTR = "data-utc"
    private const val HREF_ATTR = "href"
    private const val NAME_ATTR = "name"
    private const val SPAN_ATTR = "span"
    private const val SRC_ATTR = "src"

    private const val FILE_THUMB_ATTR_VAL = "fileThumb"
    private const val SUBJECT_ATTR_VAL = "subject"
    private const val DATE_TIME_POST_NUM_ATTR_VAL = "dateTime postNum"
    private const val NAME_BLOCK_ATTR_VAL = "nameBlock"
    private const val POST_MESSAGE_ATTR_VAL = "postMessage"
    private const val FILE_ATTR_VAL = "file"
    private const val POST_INFO_M_ATTR_VAL = "postInfoM mobile"
    private const val POST_OP_ATTR_VAL = "post op"
    private const val POST_REPLY_ATTR_VAL = "post reply"
    private const val POST_CONTAINER_REPLY_ATTR_VAL = "postContainer replyContainer"
    private const val POST_CONTAINER_OP_ATTR_VAL = "postContainer opContainer"
    private const val THREAD_ATTR_VAL = "thread"
    private const val BOARD_ATTR_VAL = "board"
    private const val PAGES_ATTR_VAL = "pages"
    private const val PAGE_LIST_MOBILE_ATTR_VAL = "mPagelist mobile"
    private const val BOARD_BANNER_ATTR_VAL = "boardBanner"
    private const val BOARD_TITLE_ATTR_VAL = "boardTitle"

    private const val TAG_A = "a"
    private const val TAG_IMG = "img"
    private const val TAG_BLOCK_QUOTE = "blockquote"
    private const val TAG_STRONG = "strong"
    private const val DIV_TAG = "div"
    private const val FORM_TAG = "form"
    private const val BODY_TAG = "body"
    private const val HTML_TAG = "html"

    private val OP_POST_DESCRIPTOR_PATTERN = Pattern.compile("//boards.4chan.org/\\w+/\\w+/(\\w+)/(\\d+)")
    private val POST_DESCRIPTOR_PATTERN = Pattern.compile("//boards.4chan.org/(\\w+)/\\w+/(\\d+)#p(\\d+)")
    private val PAGE_VALUE_PATTERN = Pattern.compile("&o=(\\d+)")
    private val TOTAL_ENTRIES_FOUND_PATTERN = Pattern.compile("4chan Search `.*` (\\d+) comment")

    private val SITE_DESCRIPTOR = SiteDescriptor.create("4chan")
  }

}
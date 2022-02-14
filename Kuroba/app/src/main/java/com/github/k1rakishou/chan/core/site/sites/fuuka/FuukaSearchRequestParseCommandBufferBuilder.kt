package com.github.k1rakishou.chan.core.site.sites.fuuka

import com.github.k1rakishou.chan.core.site.sites.search.SearchEntryPostBuilder
import com.github.k1rakishou.chan.utils.fixImageUrlIfNecessary
import com.github.k1rakishou.common.groupOrNull
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_parser.html.ExtractedAttributeValues
import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCollector
import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCommandBufferBuilder
import com.github.k1rakishou.core_parser.html.KurobaMatcher
import com.github.k1rakishou.core_parser.html.KurobaParserCommandBuilder
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.joda.time.DateTime
import org.jsoup.nodes.Element
import java.util.regex.Pattern

internal class FuukaSearchRequestParseCommandBufferBuilder {

  fun getBuilder(): KurobaHtmlParserCommandBufferBuilder<FuukaSearchPageCollector> {
    return KurobaHtmlParserCommandBufferBuilder<FuukaSearchPageCollector>()
      .start {
        html()

        nest {
          body()

          nest {
            tag(
              tagName = "form",
              matchableBuilderFunc = { id(KurobaMatcher.PatternMatcher.stringEquals("postform")) }
            )

            nest {
              div(matchableBuilderFunc = { className(KurobaMatcher.PatternMatcher.stringEquals("content")) })

              nest {
                loopWhile(predicate = {
                  tag(KurobaMatcher.TagMatcher.tagWithNameAttributeMatcher("table"))
                  attr("itemtype", KurobaMatcher.PatternMatcher.stringContains(REGULAR_POST_TYPE))
                }) {
                  parseRegularPost()
                }
              }
            }
          }
        }
      }
  }

  private fun KurobaParserCommandBuilder<FuukaSearchPageCollector>.parseRegularPost():
    KurobaParserCommandBuilder<FuukaSearchPageCollector> {

    tag(
      tagName = "table",
      matchableBuilderFunc = {
        attr("itemtype", KurobaMatcher.PatternMatcher.stringContains(REGULAR_POST_TYPE))
      },
      extractorFunc = { _, _, fuukaSearchPageCollector ->
        val searchEntryPostBuilder = SearchEntryPostBuilder(fuukaSearchPageCollector.verboseLogs)
        fuukaSearchPageCollector.searchResults += searchEntryPostBuilder
      }
    )

    nest {
      tag(
        tagName = "tbody",
        matchableBuilderFunc = { tag(KurobaMatcher.TagMatcher.tagNoAttributesMatcher()) }
      )

      nest {
        tag(
          tagName = "tr",
          matchableBuilderFunc = { tag(KurobaMatcher.TagMatcher.tagNoAttributesMatcher()) }
        )

        nest {
          executeIf(predicate = { className(KurobaMatcher.PatternMatcher.stringEquals("reply")) }) {
            tag(
              tagName = "td",
              matchableBuilderFunc = { className(KurobaMatcher.PatternMatcher.stringEquals("reply")) }
            )

            nest {
              tag(
                tagName = "label",
                matchableBuilderFunc = { tag(KurobaMatcher.TagMatcher.tagNoAttributesMatcher()) }
              )

              extractRegularPostPosterInfo()

              a(
                matchableBuilderFunc = { tag(KurobaMatcher.TagMatcher.tagHasAttribute("onclick")) },
                attrExtractorBuilderFunc = { extractAttrValueByKey("href") },
                extractorFunc = { node, extractedAttributeValues, fuukaSearchPageCollector ->
                  extractPostInfo(fuukaSearchPageCollector, extractedAttributeValues)
                }
              )

              tryExtractRegularPostMediaLink()

              tag(
                tagName = "blockquote",
                matchableBuilderFunc = { tag(KurobaMatcher.TagMatcher.tagNoAttributesMatcher()) }
              )

              nest {
                tag(
                  tagName = "p",
                  matchableBuilderFunc = { attr("itemprop", KurobaMatcher.PatternMatcher.stringEquals("text")) },
                  attrExtractorBuilderFunc = { extractHtml() },
                  extractorFunc = { node, extractedAttributeValues, fuukaSearchPageCollector ->
                    fuukaSearchPageCollector.lastOrNull()?.let { searchEntryPostBuilder ->
                      val comment = extractedAttributeValues.getHtml()
                      if (comment == null) {
                        Logger.e(TAG, "Failed to extract comment")
                        return@let
                      }

                      searchEntryPostBuilder.commentRaw = comment
                    }
                  }
                )
              }

            }
          }
        }
      }
    }

    return this
  }

  private fun KurobaParserCommandBuilder<FuukaSearchPageCollector>.extractRegularPostPosterInfo() {
    nest {
      span(matchableBuilderFunc = { className(KurobaMatcher.PatternMatcher.stringEquals("postername")) })

      nest {
        span(
          matchableBuilderFunc = { attr("itemprop", KurobaMatcher.PatternMatcher.stringEquals("name")) },
          attrExtractorBuilderFunc = { extractText() },
          extractorFunc = { _, extractedAttributeValues, fuukaSearchPageCollector ->
            fuukaSearchPageCollector.lastOrNull()?.let { searchEntryPostBuilder ->
              val name = extractedAttributeValues.getText()
              if (name == null) {
                Logger.e(TAG, "Failed to extract name")
                return@let
              }

              searchEntryPostBuilder.name = name
            }
          }
        )
      }

      span(
        matchableBuilderFunc = { className(KurobaMatcher.PatternMatcher.stringEquals("posttime")) },
        attrExtractorBuilderFunc = { extractAttrValueByKey("title") },
        extractorFunc = { node, extractedAttributeValues, fuukaSearchPageCollector ->
          fuukaSearchPageCollector.lastOrNull()?.let { searchEntryPostBuilder ->
            val title = extractedAttributeValues.getAttrValue("title")
            val timestamp = title?.toLongOrNull()

            if (timestamp == null) {
              Logger.e(TAG, "Failed to extract unixTimestampSeconds, title='$title'")
              return@let
            }

            searchEntryPostBuilder.dateTime = DateTime(timestamp)
          }
        }
      )
    }
  }

  private fun KurobaParserCommandBuilder<FuukaSearchPageCollector>.tryExtractRegularPostMediaLink() {
    val predicate = KurobaMatcher.TagMatcher.tagPredicateMatcher { element ->
      if (!element.hasAttr("href")) {
        return@tagPredicateMatcher false
      }

      return@tagPredicateMatcher element.childNodes()
        .any { node -> node is Element && node.attr("class") == "thumb" }
    }

    executeIf(predicate = {
      tag(KurobaMatcher.TagMatcher.tagWithNameAttributeMatcher("a"))
      tag(predicate)
    }) {

      a(matchableBuilderFunc = {
        tag(KurobaMatcher.TagMatcher.tagWithNameAttributeMatcher("a"))
        tag(predicate)
      })

      nest {
        tag(
          tagName = "img",
          matchableBuilderFunc = { className(KurobaMatcher.PatternMatcher.stringEquals("thumb")) },
          attrExtractorBuilderFunc = { extractAttrValueByKey("src") },
          extractorFunc = { _, extractedAttributeValues, fuukaSearchPageCollector ->
            fuukaSearchPageCollector.lastOrNull()?.let { searchEntryPostBuilder ->
              val thumbUrl = fixImageUrlIfNecessary(
                fuukaSearchPageCollector.requestUrl,
                extractedAttributeValues.getAttrValue("src")
              )?.toHttpUrlOrNull()

              if (thumbUrl == null) {
                Logger.e(TAG, "Failed to parse thumbnail image url, thumbUrl='$thumbUrl'")
                return@let
              }

              searchEntryPostBuilder.postImageUrlRawList += thumbUrl
            }
          }
        )
      }
    }
  }

  private fun extractPostInfo(
    fuukaSearchPageCollector: FuukaSearchPageCollector,
    extractedAttributeValues: ExtractedAttributeValues
  ) {
    fuukaSearchPageCollector.lastOrNull()?.let { searchEntryPostBuilder ->
      val href = extractedAttributeValues.getAttrValue("href")
      if (href == null) {
        Logger.e(TAG, "Failed to extract postNoLink")
        return@let
      }

      val matcher = POST_LINK_PATTERN.matcher(href)
      if (!matcher.find()) {
        Logger.e(TAG, "Failed to match POST_LINK_PATTERN with href: '$href'")
        return@let
      }

      val postSubNo = matcher.groupOrNull(4)
      if (postSubNo.isNotNullNorEmpty()) {
        return@let
      }

      val boardCode = matcher.groupOrNull(1)
      val threadNo = matcher.groupOrNull(2)?.toLongOrNull()

      if (boardCode.isNullOrEmpty() || threadNo == null) {
        Logger.e(TAG, "Failed to extract boardCode (${matcher.groupOrNull(1)})" +
            " or threadNo (${matcher.groupOrNull(2)})")
        return@let
      }

      searchEntryPostBuilder.siteName = fuukaSearchPageCollector.boardDescriptor.siteName()
      searchEntryPostBuilder.boardCode = fuukaSearchPageCollector.boardDescriptor.boardCode
      searchEntryPostBuilder.threadNo = threadNo

      val postNo = matcher.groupOrNull(3)?.toLongOrNull()
      if (postNo != null) {
        searchEntryPostBuilder.postNo = postNo
      }

      searchEntryPostBuilder.isOp = threadNo == postNo
    }
  }

  internal data class FuukaSearchPageCollector(
    val verboseLogs: Boolean,
    val requestUrl: String,
    val boardDescriptor: BoardDescriptor,
    val searchResults: MutableList<SearchEntryPostBuilder> = mutableListOf(),
    var foundEntriesRaw: String? = null
  ) : KurobaHtmlParserCollector {
    fun lastOrNull(): SearchEntryPostBuilder? = searchResults.lastOrNull()
  }

  companion object {
    private const val TAG = "FuukaApiThreadPostParseCommandBufferBuilder"

//    https://warosu.org/jp/thread/32638291
//    https://warosu.org/jp/thread/32638291#p32638297
//    https://warosu.org/jp/thread/32638291#p32638297_123
//    https://warosu.org/jp/thread/S32638291#p32638297_123
    private val POST_LINK_PATTERN = Pattern.compile("\\/(\\w+)\\/thread\\/S?(\\d+)(?:#p(\\d+))?(?:_(\\d+))?")

    private const val REGULAR_POST_TYPE = "http://schema.org/Comment"
  }

}
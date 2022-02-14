package com.github.k1rakishou.chan.core.site.sites.fuuka

import com.github.k1rakishou.chan.utils.extractFileNameExtension
import com.github.k1rakishou.chan.utils.fixImageUrlIfNecessary
import com.github.k1rakishou.chan.utils.removeExtensionIfPresent
import com.github.k1rakishou.common.groupOrNull
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_parser.html.ExtractedAttributeValues
import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCommandBufferBuilder
import com.github.k1rakishou.core_parser.html.KurobaMatcher
import com.github.k1rakishou.core_parser.html.KurobaParserCommandBuilder
import com.github.k1rakishou.model.data.archive.ArchivePost
import com.github.k1rakishou.model.data.archive.ArchivePostMedia
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.util.regex.Pattern

internal class FuukaApiThreadPostParseCommandBufferBuilder(
  private val verboseLogs: Boolean
) {

  fun getBuilder(): KurobaHtmlParserCommandBufferBuilder<FuukaApi.ArchiveThreadPostCollector> {
    return KurobaHtmlParserCommandBufferBuilder<FuukaApi.ArchiveThreadPostCollector>()
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
                // Apparently warosu has a bug where the original post sometimes won't be shown at all
                // (all the other posts seem to be fine). This is a hack to create a default original
                // post in such cases.
                executeIfElse(
                  predicate = { attr("itemtype", KurobaMatcher.PatternMatcher.stringContains(ORIGINAL_POST_TYPE)) },
                  resetNodeIndex = false,
                  ifBranchBuilder = {
                    // Original post found. Parse it.
                    div(matchableBuilderFunc = { attr("itemtype", KurobaMatcher.PatternMatcher.stringContains(ORIGINAL_POST_TYPE)) })

                    nest {
                      parseOriginalPost()
                    }
                  },
                  elseBranchBuilder = {
                    // Original post was not found. Create a custom one.
                    peekCollector { archiveThreadPostCollector ->
                      check(archiveThreadPostCollector.archivePosts.isEmpty()) { "archivePosts are not empty!" }

                      archiveThreadPostCollector.archivePosts.add(
                        createDefaultOriginalPost(archiveThreadPostCollector.threadDescriptor)
                      )
                    }
                  }
                )

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

  private fun KurobaParserCommandBuilder<FuukaApi.ArchiveThreadPostCollector>.parseOriginalPost():
    KurobaParserCommandBuilder<FuukaApi.ArchiveThreadPostCollector> {

    span(
      matchableBuilderFunc = { tag(KurobaMatcher.TagMatcher.tagNoAttributesMatcher()) },
      attrExtractorBuilderFunc = { extractText() },
      extractorFunc = { _, extractedAttributeValues, archiveThreadPostCollector ->
        val archivePost = ArchivePost(archiveThreadPostCollector.threadDescriptor.boardDescriptor)
        extractFileInfoPart1(archivePost, extractedAttributeValues)
        archiveThreadPostCollector.archivePosts += archivePost
      }
    )

    a(
      matchableBuilderFunc = {
        tag(KurobaMatcher.TagMatcher.tagPredicateMatcher { element ->
          if (!element.hasAttr("href")) {
            return@tagPredicateMatcher false
          }

          return@tagPredicateMatcher element.childNodes()
            .any { node -> node is Element && node.attr("class") == "thumb" }
        })
      },
      attrExtractorBuilderFunc = { extractAttrValueByKey("href") },
      extractorFunc = { _, extractedAttributeValues, archiveThreadPostCollector ->
        extractFileInfoPart2(archiveThreadPostCollector, extractedAttributeValues)
      }
    )

    nest {
      tag(
        tagName = "img",
        matchableBuilderFunc = { className(KurobaMatcher.PatternMatcher.stringEquals("thumb")) },
        attrExtractorBuilderFunc = { extractAttrValueByKey("src") },
        extractorFunc = { _, extractedAttributeValues, archiveThreadPostCollector ->
          archiveThreadPostCollector.lastMediaOrNull()?.let { archivePostMedia ->
            val thumbUrl = fixImageUrlIfNecessary(
              archiveThreadPostCollector.requestUrl,
              extractedAttributeValues.getAttrValue("src")
            )

            if (thumbUrl == null) {
              Logger.e(TAG, "Failed to parse thumbnail image url, thumbUrl='$thumbUrl'")
              return@let
            }

            archivePostMedia.thumbnailUrl = thumbUrl
          }
        }
      )
    }

    tag(
      tagName = "label",
      matchableBuilderFunc = { tag(KurobaMatcher.TagMatcher.tagNoAttributesMatcher()) }
    )

    extractOriginalPostSubjectAndPosterInfo()

    a(
      matchableBuilderFunc = { tag(KurobaMatcher.TagMatcher.tagHasAttribute("onclick")) },
      attrExtractorBuilderFunc = { extractAttrValueByKey("href") },
      extractorFunc = { node, extractedAttributeValues, archiveThreadPostCollector ->
        extractPostInfo(true, archiveThreadPostCollector, extractedAttributeValues)
      }
    )

    tag(
      tagName = "blockquote",
      matchableBuilderFunc = { tag(KurobaMatcher.TagMatcher.tagNoAttributesMatcher()) }
    )

    nest {
      tag(
        tagName = "p",
        matchableBuilderFunc = { attr("itemprop", KurobaMatcher.PatternMatcher.stringEquals("text")) },
        attrExtractorBuilderFunc = { extractHtml() },
        extractorFunc = { node, extractedAttributeValues, archiveThreadPostCollector ->
          archiveThreadPostCollector.lastPostOrNull()?.let { archivePost ->
            archivePost.comment = extractedAttributeValues.getHtml() ?: ""
          }
        }
      )
    }

    return this
  }

  private fun KurobaParserCommandBuilder<FuukaApi.ArchiveThreadPostCollector>.parseRegularPost():
    KurobaParserCommandBuilder<FuukaApi.ArchiveThreadPostCollector> {

    tag(
      tagName = "table",
      matchableBuilderFunc = {
        attr("itemtype", KurobaMatcher.PatternMatcher.stringContains(REGULAR_POST_TYPE))
      },
      extractorFunc = { _, _, archiveThreadPostCollector ->
        val archivePost = ArchivePost(archiveThreadPostCollector.threadDescriptor.boardDescriptor)
        archiveThreadPostCollector.archivePosts += archivePost
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
                extractorFunc = { node, extractedAttributeValues, archiveThreadPostCollector ->
                  extractPostInfo(false, archiveThreadPostCollector, extractedAttributeValues)
                }
              )

              executeIf(predicate = {
                tag(KurobaMatcher.TagMatcher.tagWithNameAttributeMatcher("span"))
                tag(KurobaMatcher.TagMatcher.tagNoAttributesMatcher())
              }) {
                span(
                  matchableBuilderFunc = { tag(KurobaMatcher.TagMatcher.tagNoAttributesMatcher()) },
                  attrExtractorBuilderFunc = { extractText() },
                  extractorFunc = { _, extractedAttributeValues, archiveThreadPostCollector ->
                    extractFileInfoPart1(archiveThreadPostCollector.lastPostOrNull()!!, extractedAttributeValues)
                  }
                )
              }

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
                  extractorFunc = { node, extractedAttributeValues, archiveThreadPostCollector ->
                    archiveThreadPostCollector.lastPostOrNull()?.let { archivePost ->
                      archivePost.comment = extractedAttributeValues.getHtml() ?: ""
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

  private fun KurobaParserCommandBuilder<FuukaApi.ArchiveThreadPostCollector>.extractOriginalPostSubjectAndPosterInfo() {
    nest {
      executeIf(predicate = { className(KurobaMatcher.PatternMatcher.stringEquals("filetitle")) }) {
        tag(
          tagName = "span",
          matchableBuilderFunc = { className(KurobaMatcher.PatternMatcher.stringEquals("filetitle")) },
          attrExtractorBuilderFunc = { extractText() },
          extractorFunc = { _, extractedAttributeValues, archiveThreadPostCollector ->
            archiveThreadPostCollector.lastPostOrNull()?.let { archivePost ->
              val subject = extractedAttributeValues.getText()
              if (subject == null) {
                Logger.e(TAG, "Failed to extract subject")
                return@let
              }

              archivePost.subject = subject
            }
          }
        )
      }

      extractPostPosterInfoInternal()
    }
  }

  private fun KurobaParserCommandBuilder<FuukaApi.ArchiveThreadPostCollector>.extractRegularPostPosterInfo() {
    nest {
      extractPostPosterInfoInternal()
    }
  }

  private fun KurobaParserCommandBuilder<FuukaApi.ArchiveThreadPostCollector>.extractPostPosterInfoInternal():
    KurobaParserCommandBuilder<FuukaApi.ArchiveThreadPostCollector> {
    span(matchableBuilderFunc = { className(KurobaMatcher.PatternMatcher.stringContainsIgnoreCase("postername")) })

    nest {
      span(
        matchableBuilderFunc = { attr("itemprop", KurobaMatcher.PatternMatcher.stringEquals("name")) },
        attrExtractorBuilderFunc = { extractText() },
        extractorFunc = { _, extractedAttributeValues, archiveThreadPostCollector ->
          archiveThreadPostCollector.lastPostOrNull()?.let { archivePost ->
            val name = extractedAttributeValues.getText()
            if (name == null) {
              Logger.e(TAG, "Failed to extract name")
              return@let
            }

            archivePost.name = name
          }
        }
      )
    }

    executeIf(predicate = { className(KurobaMatcher.PatternMatcher.stringEquals("postertrip")) }) {
      span(
        matchableBuilderFunc = { className(KurobaMatcher.PatternMatcher.stringEquals("postertrip")) },
        attrExtractorBuilderFunc = { extractText() },
        extractorFunc = { node, extractedAttributeValues, archiveThreadPostCollector ->
          archiveThreadPostCollector.lastPostOrNull()?.let { archivePost ->
            val tripcode = extractedAttributeValues.getText()
            if (tripcode == null) {
              Logger.e(TAG, "Failed to extract tripcode")
              return@let
            }

            archivePost.name = Parser.unescapeEntities(tripcode, false)
          }
        }
      )
    }

    return span(
      matchableBuilderFunc = { className(KurobaMatcher.PatternMatcher.stringEquals("posttime")) },
      attrExtractorBuilderFunc = { extractAttrValueByKey("title") },
      extractorFunc = { node, extractedAttributeValues, archiveThreadPostCollector ->
        archiveThreadPostCollector.lastPostOrNull()?.let { archivePost ->
          val title = extractedAttributeValues.getAttrValue("title")
          val timestamp = title?.toLongOrNull()

          if (timestamp == null) {
            Logger.e(TAG, "Failed to extract unixTimestampSeconds, title='$title'")
            return@let
          }

          archivePost.unixTimestampSeconds = timestamp / 1000L
        }
      }
    )
  }

  private fun KurobaParserCommandBuilder<FuukaApi.ArchiveThreadPostCollector>.tryExtractRegularPostMediaLink() {
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
      a(
        matchableBuilderFunc = { tag(predicate) },
        attrExtractorBuilderFunc = { extractAttrValueByKey("href") },
        extractorFunc = { _, extractedAttributeValues, archiveThreadPostCollector ->
          extractFileInfoPart2(archiveThreadPostCollector, extractedAttributeValues)
        }
      )

      nest {
        tag(
          tagName = "img",
          matchableBuilderFunc = { className(KurobaMatcher.PatternMatcher.stringEquals("thumb")) },
          attrExtractorBuilderFunc = { extractAttrValueByKey("src") },
          extractorFunc = { _, extractedAttributeValues, archiveThreadPostCollector ->
            archiveThreadPostCollector.lastMediaOrNull()?.let { archivePostMedia ->
              val thumbUrl = fixImageUrlIfNecessary(
                archiveThreadPostCollector.requestUrl,
                extractedAttributeValues.getAttrValue("src")
              )

              if (thumbUrl == null) {
                Logger.e(TAG, "Failed to parse thumbnail image url, thumbUrl='$thumbUrl'")
                return@let
              }

              archivePostMedia.thumbnailUrl = thumbUrl
            }
          }
        )
      }
    }
  }

  private fun extractPostInfo(
    supposedToBeOriginalPost: Boolean,
    archiveThreadPostCollector: FuukaApi.ArchiveThreadPostCollector,
    extractedAttributeValues: ExtractedAttributeValues
  ) {
    archiveThreadPostCollector.lastPostOrNull()?.let { archivePost ->
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
        // TODO(KurobaEx / @GhostPosts):

        if (verboseLogs) {
          Logger.d(TAG, "Skipping ghost post $href")
        }

        return@let
      }

      val boardCode = matcher.groupOrNull(1)
      val threadNo = matcher.groupOrNull(2)?.toLongOrNull()

      if (boardCode.isNullOrEmpty() || threadNo == null) {
        Logger.e(
          TAG, "Failed to extract boardCode (${matcher.groupOrNull(1)})" +
            " or threadNo (${matcher.groupOrNull(2)})"
        )
        return@let
      }

      archivePost.threadNo = threadNo

      var postNo = matcher.groupOrNull(3)?.toLongOrNull()
      if (supposedToBeOriginalPost) {
        postNo = threadNo
      }

      if (postNo != null) {
        archivePost.postNo = postNo
      }

      if (supposedToBeOriginalPost) {
        check(threadNo == postNo) { "Not Original Post: threadNo=${threadNo}, postNo=${postNo}" }
      }

      archivePost.isOP = supposedToBeOriginalPost
    }
  }

  private fun extractFileInfoPart2(
    archiveThreadPostCollector: FuukaApi.ArchiveThreadPostCollector,
    extractedAttributeValues: ExtractedAttributeValues
  ) {
    archiveThreadPostCollector.lastMediaOrNull()?.let { archivePostMedia ->
      val fullImageUrl = fixImageUrlIfNecessary(
        archiveThreadPostCollector.requestUrl,
        extractedAttributeValues.getAttrValue("href")
      )

      if (fullImageUrl == null) {
        Logger.e(TAG, "Failed to parse full image url, fullImageUrl='$fullImageUrl'")
        return@let
      }

      val serverFilename = removeExtensionIfPresent(fullImageUrl.substringAfterLast('/'))
      val extension = extractFileNameExtension(fullImageUrl)

      archivePostMedia.imageUrl = fullImageUrl
      archivePostMedia.serverFilename = serverFilename
      archivePostMedia.extension = extension
    }
  }

  private fun extractFileInfoPart1(
    archivePost: ArchivePost,
    extractedAttributeValues: ExtractedAttributeValues
  ) {
    extractedAttributeValues.getText()?.let { rawText ->
      val matcher = FILE_INFO_PATTERN.matcher(rawText)
      if (matcher.find()) {
        val fileSizeValue = matcher.groupOrNull(1)?.toFloatOrNull()
        val fileSizeType = matcher.groupOrNull(2)
        val fileWidthAndHeight = matcher.groupOrNull(3)
        val originalFileName = matcher.groupOrNull(4)

        if (fileSizeValue == null
          || fileSizeType == null
          || fileWidthAndHeight == null
          || originalFileName == null
        ) {
          Logger.e(
            TAG, "Failed to parse file, tagText='$rawText', fileSizeValue=$fileSizeValue, " +
              "fileSizeType=$fileSizeType, fileWidthAndHeight=$fileWidthAndHeight, " +
              "originalFileName=$originalFileName"
          )
          return@let
        }

        val (width: Int?, height: Int?) = fileWidthAndHeight
          .split("x")
          .map { size -> size.toIntOrNull() }

        if (width == null || height == null) {
          Logger.e(
            TAG, "Failed to extract file width and height, " +
              "fileWidthAndHeight='$fileWidthAndHeight', width=$width, height=$height"
          )
          return@let
        }

        val fileSizeMultiplier = when (fileSizeType) {
          "MB" -> 1024 * 1024
          "KB" -> 1024
          "B" -> 1
          else -> 1
        }

        val actualFileSize = (fileSizeValue * fileSizeMultiplier.toFloat()).toLong()

        archivePost.archivePostMediaList += ArchivePostMedia(
          filename = originalFileName,
          imageWidth = width,
          imageHeight = height,
          size = actualFileSize
        )
      }
    }
  }

  private fun createDefaultOriginalPost(threadDescriptor: ChanDescriptor.ThreadDescriptor): ArchivePost {
    return ArchivePost(
      boardDescriptor = threadDescriptor.boardDescriptor,
      threadNo = threadDescriptor.threadNo,
      postNo = threadDescriptor.threadNo,
      isOP = true,
      unixTimestampSeconds = System.currentTimeMillis() / 1000L,
      comment = "Failed to find Original Post (most likely warosu.org bug)"
    )
  }

  companion object {
    private const val TAG = "FuukaApiThreadPostParseCommandBufferBuilder"

//    File: 1.32 MB, 2688x4096,   EsGgXsxUcAQV0_h.jpg
//    File:   694 KB, 2894x4093, EscP39vUUAEE9ol.jpg
//    File: 694 B,   2894x4093,   EscP39vUUAEE9ol.png
//    File: 694 B,   2894x4093,   EscP39vUUAEE9ol.webm
//    File: 3.29 MB, 1920x1080, 【龍が如く極2】関西と関東の戦い！！？？止めるにぇ、桐生ちゃん！【ホロライブ_さくらみこ】※ネタバレあり 48-45 screenshot.png
    private val FILE_INFO_PATTERN = Pattern.compile("File:\\s+(\\d+(?:\\.\\d+)?)\\s+((?:[MK])?B),\\s+(\\d+x\\d+),\\s+(.*)\$")

//    https://warosu.org/jp/thread/32638291
//    https://warosu.org/jp/thread/32638291#p32638297
//    https://warosu.org/jp/thread/32638291#p32638297_123
//    https://warosu.org/jp/thread/S32638291#p32638297_123
    private val POST_LINK_PATTERN = Pattern.compile("\\/(\\w+)\\/thread\\/S?(\\d+)(?:#p(\\d+))?(?:_(\\d+))?")

    private const val ORIGINAL_POST_TYPE = "http://schema.org/DiscussionForumPosting"
    private const val REGULAR_POST_TYPE = "http://schema.org/Comment"
  }

}
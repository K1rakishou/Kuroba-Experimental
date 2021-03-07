package com.github.k1rakishou.chan.core.site.sites.yukila

import com.github.k1rakishou.chan.utils.extractFileNameExtension
import com.github.k1rakishou.chan.utils.fixImageUrlIfNecessary
import com.github.k1rakishou.common.groupOrNull
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCommandBufferBuilder
import com.github.k1rakishou.core_parser.html.KurobaMatcher
import com.github.k1rakishou.core_parser.html.KurobaParserCommandBuilder
import com.github.k1rakishou.model.data.archive.ArchivePost
import com.github.k1rakishou.model.data.archive.ArchivePostMedia
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.util.regex.Pattern

class YukilaApiThreadPostParseCommandBufferBuilder(
  private val verboseLogs: Boolean
) {

  fun getBuilder(): KurobaHtmlParserCommandBufferBuilder<YukilaApi.ArchiveThreadPostCollector> {
    return KurobaHtmlParserCommandBufferBuilder<YukilaApi.ArchiveThreadPostCollector>()
      .start {
        html()

        nest {
          body()

          nest {
            tag(
              tagName = "form",
              matchableBuilderFunc = { attr("name", KurobaMatcher.PatternMatcher.stringEquals("delform")) }
            )

            nest {
              div(matchableBuilderFunc = { attr("class", KurobaMatcher.PatternMatcher.stringEquals("board")) })

              nest {
                div(matchableBuilderFunc = { attr("class", KurobaMatcher.PatternMatcher.stringEquals("thread")) })

                nest {
                  executeIf(
                    predicate = { attr("class", KurobaMatcher.PatternMatcher.stringEquals("postContainer opContainer")) },
                    resetNodeIndex = true
                  ) {
                    div(matchableBuilderFunc = { attr("class", KurobaMatcher.PatternMatcher.stringEquals("postContainer opContainer")) })

                    nest {
                      parsePost(isOriginalPost = true)
                    }
                  }

                  loopWhile(predicate = {
                    tag(KurobaMatcher.TagMatcher.tagWithNameAttributeMatcher("div"))
                    attr("class", KurobaMatcher.PatternMatcher.stringEquals("postContainer replyContainer"))
                  }) {
                    div(matchableBuilderFunc = { attr("class", KurobaMatcher.PatternMatcher.stringEquals("postContainer replyContainer")) })

                    nest {
                      parsePost(isOriginalPost = false)
                    }
                  }
                }
              }
            }
          }
        }
      }
  }

  private fun KurobaParserCommandBuilder<YukilaApi.ArchiveThreadPostCollector>.parsePost(
    isOriginalPost: Boolean
  ): KurobaParserCommandBuilder<YukilaApi.ArchiveThreadPostCollector> {
    val postClass = if (isOriginalPost) {
      "post op"
    } else {
      "post reply"
    }

    div(
      matchableBuilderFunc = { attr("class", KurobaMatcher.PatternMatcher.stringEquals(postClass)) },
      attrExtractorBuilderFunc = {
        expectAttr("id")
        extractAttrValueByKey("id")
      },
      extractorFunc = { node, extractedAttributeValues, archiveThreadPostCollector ->
        kotlin.run {
          val archivePost = ArchivePost(archiveThreadPostCollector.threadDescriptor.boardDescriptor)

          val postNoRaw = extractedAttributeValues.getAttrValue("id")
          if (postNoRaw.isNullOrEmpty()) {
            Logger.e(TAG, "Failed to extract postNoRaw")
            return@run
          }

          val matcher = POST_NO_PATTERN.matcher(postNoRaw)
          if (!matcher.find()) {
            Logger.e(TAG, "Failed to match POST_NO_PATTERN with '$postNoRaw'")
            return@run
          }

          val postNo = matcher.groupOrNull(1)?.toLongOrNull()
          if (postNo == null) {
            Logger.e(TAG, "Failed to extract postNo from postNoRaw: '$postNoRaw'")
            return@run
          }

          val expectedThreadNo = archiveThreadPostCollector.threadDescriptor.threadNo

          if (isOriginalPost) {
            check(expectedThreadNo == postNo) { "expectedThreadNo ($expectedThreadNo) != postNo ($postNo)" }
            archivePost.isOP = isOriginalPost
          }

          archivePost.postNo = postNo
          archivePost.threadNo = expectedThreadNo

          archiveThreadPostCollector.archivePosts += archivePost
        }
      }
    )

    nest {
      if (isOriginalPost) {
        parseFileInfo()
        parsePostInfo(isOriginalPost = true)
      } else {
        parsePostInfo(isOriginalPost = false)
        parseFileInfo()
      }

      parsePostComment()
    }

    return this
  }

  private fun KurobaParserCommandBuilder<YukilaApi.ArchiveThreadPostCollector>.parseFileInfo():
    KurobaParserCommandBuilder<YukilaApi.ArchiveThreadPostCollector> {

    executeIf(
      predicate = { attr("class", KurobaMatcher.PatternMatcher.stringEquals("file")) },
      resetNodeIndex = true
    ) {

      div(matchableBuilderFunc = {
        attr(
          "class",
          KurobaMatcher.PatternMatcher.stringEquals("file")
        )
      })

      nest {
        div(
          matchableBuilderFunc = {
            attr(
              "class",
              KurobaMatcher.PatternMatcher.stringEquals("fileText")
            )
          },
          extractorFunc = { node, extractedAttributeValues, archiveThreadPostCollector ->
            kotlin.run {
              val fullImageUrlNode = node.childNodes()
                .firstOrNull { childNode -> childNode is Element && childNode.tagName() == "a" }

              if (fullImageUrlNode == null) {
                Logger.e(TAG, "Failed to find fullImageUrlNode")
                return@run
              }

              val fullImageUrlRaw = fullImageUrlNode.attr("href")
              if (fullImageUrlRaw.isEmpty()) {
                Logger.e(TAG, "Failed to find fullImageUrlRaw")
                return@run
              }

              val fileSizeAndDimsNode = node.childNodes()
                .firstOrNull { childNode ->
                  return@firstOrNull childNode is TextNode
                    && POST_FILE_SIZE_AND_DIMS_PATTERN.matcher(childNode.wholeText).find()
                }

              if (fileSizeAndDimsNode == null) {
                Logger.e(TAG, "Failed to find fileSizeAndDimsNode")
                return@run
              }

              val rawText = (fileSizeAndDimsNode as TextNode).wholeText

              val matcher = POST_FILE_SIZE_AND_DIMS_PATTERN.matcher(rawText)
              check(matcher.find())

              val fileSizeValue = matcher.groupOrNull(1)?.toFloatOrNull()
              val fileSizeType = matcher.groupOrNull(2)
              val fileWidthAndHeight = matcher.groupOrNull(3)

              if (fileSizeValue == null
                || fileSizeType == null
                || fileWidthAndHeight == null
              ) {
                Logger.e(
                  TAG, "Failed to parse file, tagText='$rawText', fileSizeValue=$fileSizeValue, " +
                    "fileSizeType=$fileSizeType, fileWidthAndHeight=$fileWidthAndHeight"
                )
                return@run
              }

              val (width: Int?, height: Int?) = fileWidthAndHeight
                .split("x")
                .map { size -> size.toIntOrNull() }

              if (width == null || height == null) {
                Logger.e(
                  TAG, "Failed to extract file width and height, " +
                    "fileWidthAndHeight='$fileWidthAndHeight', width=$width, height=$height"
                )
                return@run
              }

              val fileSizeMultiplier = when (fileSizeType) {
                "MB" -> 1024 * 1024
                "KB" -> 1024
                "B" -> 1
                else -> 1
              }

              val actualFileSize = (fileSizeValue * fileSizeMultiplier.toFloat()).toLong()

              val serverFileName = POST_FILE_SERVER_FILE_NAME_PATTERN.matcher(fullImageUrlRaw)
                .let { serverFileNameMatcher ->
                  if (!serverFileNameMatcher.find()) {
                    return@let null
                  }

                  return@let serverFileNameMatcher.groupOrNull(1)
                }

              if (serverFileName.isNullOrEmpty()) {
                Logger.e(TAG, "Failed to extract serverFileName from '${fullImageUrlRaw}'")
                return@run
              }

              var imageOriginalFileName =
                (fullImageUrlNode.childNodes().firstOrNull() as? TextNode)?.wholeText
              if (imageOriginalFileName == null) {
                imageOriginalFileName = serverFileName
              }

              val fullImageUrl = fixImageUrlIfNecessary(fullImageUrlRaw)
              if (fullImageUrl == null) {
                Logger.e(TAG, "fixImageUrlIfNecessary($fullImageUrlRaw) failure")
                return@run
              }

              val extension = extractFileNameExtension(fullImageUrl)

              val archivePostMedia = ArchivePostMedia(
                filename = imageOriginalFileName,
                serverFilename = serverFileName,
                imageUrl = fullImageUrl,
                imageWidth = width,
                imageHeight = height,
                extension = extension,
                size = actualFileSize
              )

              archiveThreadPostCollector.lastPostOrNull()?.let { archivePost ->
                archivePost.archivePostMediaList += archivePostMedia
              }
            }
          }
        )

        a(
          matchableBuilderFunc = { attr("class", KurobaMatcher.PatternMatcher.stringContains("fileThumb")) },
          attrExtractorBuilderFunc = { extractAttrValueByMatcher("fileThumb", KurobaMatcher.PatternMatcher.stringContains("fileThumb")) },
          extractorFunc = { node, extractedAttributeValues, archiveThreadPostCollector ->
            archiveThreadPostCollector.lastMediaOrNull()?.let { archivePostMedia ->
              val isSpoiler = extractedAttributeValues.getAttrValue("fileThumb")
                ?.contains("imgspoiler")
                ?: false

              val imgElement = node.childNodes()
                .firstOrNull { childNode -> childNode is Element && childNode.tagName() == "img" }
                as? Element

              if (imgElement == null) {
                Logger.e(TAG, "Failed to find imgElement")
                return@let
              }

              val thumbUrl = imgElement.attr("src")
              if (thumbUrl.isEmpty()) {
                Logger.e(TAG, "Failed to find thumbUrl, imgElement=${imgElement.wholeText()}")
                return@let
              }

              val imageMd5Base64 = imgElement.attr("data-md5")
              if (imageMd5Base64.isEmpty()) {
                Logger.e(TAG, "Failed to find imageMd5Base64, imgElement=${imgElement.wholeText()}")
                return@let
              }

              archivePostMedia.thumbnailUrl = fixImageUrlIfNecessary(thumbUrl)
              archivePostMedia.spoiler = isSpoiler
              archivePostMedia.fileHashBase64 = imageMd5Base64
            }
          }
        )

      }
    }

    return this
  }

  private fun KurobaParserCommandBuilder<YukilaApi.ArchiveThreadPostCollector>.parsePostInfo(isOriginalPost: Boolean):
    KurobaParserCommandBuilder<YukilaApi.ArchiveThreadPostCollector> {

    div(matchableBuilderFunc = { attr("class", KurobaMatcher.PatternMatcher.stringEquals("postInfo desktop")) })

    nest {
      if (isOriginalPost) {
        span(
          matchableBuilderFunc = { attr("class", KurobaMatcher.PatternMatcher.stringEquals("subject")) },
          attrExtractorBuilderFunc = { extractText() },
          extractorFunc = { node, extractedAttributeValues, archiveThreadPostCollector ->
            archiveThreadPostCollector.lastPostOrNull()?.let { archivePost ->
              archivePost.subject = extractedAttributeValues.getText() ?: ""
            }
          }
        )
      }

      span(matchableBuilderFunc = { attr("class", KurobaMatcher.PatternMatcher.stringEquals("nameBlock")) })

      nest {
        span(
          matchableBuilderFunc = { attr("class", KurobaMatcher.PatternMatcher.stringEquals("name")) },
          attrExtractorBuilderFunc = { extractText() },
          extractorFunc = { node, extractedAttributeValues, archiveThreadPostCollector ->
            archiveThreadPostCollector.lastPostOrNull()?.let { archivePost ->
              archivePost.name = extractedAttributeValues.getText() ?: ""
            }
          }
        )

        executeIf(
          predicate = { attr("class", KurobaMatcher.PatternMatcher.stringEquals("postertrip")) },
          resetNodeIndex = false
        ) {
          span(
            matchableBuilderFunc = { attr("class", KurobaMatcher.PatternMatcher.stringEquals("postertrip")) },
            attrExtractorBuilderFunc = { extractText() },
            extractorFunc = { node, extractedAttributeValues, archiveThreadPostCollector ->
              archiveThreadPostCollector.lastPostOrNull()?.let { archivePost ->
                archivePost.tripcode = extractedAttributeValues.getText() ?: ""
              }
            }
          )
        }
      }

      span(
        matchableBuilderFunc = { attr("class", KurobaMatcher.PatternMatcher.stringEquals("dateTime")) },
        attrExtractorBuilderFunc = { extractAttrValueByKey("data-utc") },
        extractorFunc = { node, extractedAttributeValues, archiveThreadPostCollector ->
          archiveThreadPostCollector.lastPostOrNull()?.let { archivePost ->
            val dateTimeSecondsRaw = extractedAttributeValues.getAttrValue("data-utc")
            if (dateTimeSecondsRaw.isNullOrEmpty()) {
              Logger.e(TAG, "Failed to extract dateTimeSecondsRaw")
              return@let
            }

            val dateTimeSeconds = dateTimeSecondsRaw.toLongOrNull()
            if (dateTimeSeconds == null) {
              Logger.e(TAG, "Failed to convert dateTimeSeconds ($dateTimeSecondsRaw) into dateTime")
              return@let
            }

            archivePost.unixTimestampSeconds = dateTimeSeconds
          }
        }
      )
    }

    return this
  }

  private fun KurobaParserCommandBuilder<YukilaApi.ArchiveThreadPostCollector>.parsePostComment():
    KurobaParserCommandBuilder<YukilaApi.ArchiveThreadPostCollector> {

    tag(
      tagName = "blockquote",
      matchableBuilderFunc = { attr("class", KurobaMatcher.PatternMatcher.stringEquals("postMessage")) },
      attrExtractorBuilderFunc = { extractHtml() },
      extractorFunc = { node, extractedAttributeValues, archiveThreadPostCollector ->
        archiveThreadPostCollector.lastPostOrNull()?.let { archivePost ->
          val commentRaw = extractedAttributeValues.getHtml()
          if (commentRaw == null) {
            Logger.e(TAG, "Failed to find commentRaw")
            return@let
          }

          archivePost.comment = commentRaw
        }
      }
    )

    return this
  }

  companion object {
    private const val TAG = "YukilaApiThreadPostParseCommandBufferBuilder"
    private val POST_NO_PATTERN = Pattern.compile("p(\\d+)")
    private val POST_FILE_SIZE_AND_DIMS_PATTERN = Pattern.compile("\\((\\d+(?:\\.\\d+)?)\\s+((?:[MK])?B),\\s+(\\d+x\\d+)\\)")
    private val POST_FILE_SERVER_FILE_NAME_PATTERN = Pattern.compile("([0-9a-f]+)\\.\\w+\$")
  }

}


